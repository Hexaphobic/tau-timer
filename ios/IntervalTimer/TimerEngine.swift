import Foundation
import IntervalTimerCore

/// Milliseconds on a monotonic clock that keeps counting while the device sleeps.
///
/// `CLOCK_MONOTONIC` is the exact counterpart of Android's `elapsedRealtime()`; `CACurrentMediaTime()`
/// / `CLOCK_UPTIME_RAW` are NOT — they stop while the system is asleep, which would quietly shorten
/// any workout that ran with the screen off.
@inline(__always) func monotonicMs() -> Int {
    Int(clock_gettime_nsec_np(CLOCK_MONOTONIC) / 1_000_000)
}

/// Owns the authoritative clock.
///
/// All timing derives from `monotonicMs()` against a stored start anchor, so a view rebuild, a
/// rotation or a trip through the background never disturbs it. The UI observes `state` and sends
/// start/pause/resume/stop. `Beeper` keeps the process alive while this ticks.
@MainActor
final class TimerEngine: ObservableObject {

    @Published private(set) var state = TimerUiState.idle

    private let beeper = Beeper()

    private var workout: Workout?
    private var totalRounds = 0
    private var roundsPerPass = 0
    private var startMs = 0          // the monotonic instant that maps to activeElapsed == 0
    private var pausedActive = 0     // frozen active-elapsed while paused
    private var paused = false

    private var ticker: Timer?
    private var cues: [(at: Int, cue: Cue)] = []
    private var cueIdx = 0

    var isRunning: Bool { workout != nil }

    func start(_ w: Workout) {
        workout = w
        totalRounds = w.intervals.map(\.round).max() ?? 0
        roundsPerPass = w.roundsPerPass
        startMs = monotonicMs()
        pausedActive = 0
        paused = false
        cues = TimerEngine.buildCues(w)
        cueIdx = 0
        beeper.start()
        loop()
    }

    func pause() {
        guard let w = workout, !paused else { return }
        pausedActive = activeElapsed()
        paused = true
        ticker?.invalidate()
        ticker = nil
        beeper.duckEnd()
        publish(w)
    }

    func resume() {
        guard workout != nil, paused else { return }
        startMs = monotonicMs() - pausedActive
        paused = false
        loop()
    }

    func stop() {
        ticker?.invalidate()
        ticker = nil
        workout = nil
        paused = false
        beeper.stop()
        state = .idle
    }

    private func activeElapsed() -> Int {
        paused ? pausedActive : monotonicMs() - startMs
    }

    /// All cue instants across the whole timeline: 5/3/2/1s before each boundary + GO at it, minus
    /// any that fall at or before the interval's own start.
    ///
    /// That drop is what `at > start` is for: 5s is both the editor's work floor and the default
    /// prepare, and at 5s `end - 5_000` IS the previous interval's boundary. Both cues then came due
    /// in one `fireDueCues` pass, so the warn played over the transition whoosh and outside the duck
    /// window `.go` had just torn down. A cue landing on an interval's start belongs to the boundary
    /// before it, which already has its `.go`. Kept in step with the Kotlin twin in TimerService.kt —
    /// the two have to agree or the same preset sounds different on each phone.
    private static func buildCues(_ w: Workout) -> [(at: Int, cue: Cue)] {
        var list: [(at: Int, cue: Cue)] = []
        var end = 0
        for iv in w.intervals {
            let start = end
            end += iv.durationMs
            // `at > start` subsumes the old `at >= 0` filter, since start is never negative.
            func add(_ at: Int, _ cue: Cue) { if at > start { list.append((at, cue)) } }
            add(end - 5_000, .warn)
            add(end - 3_000, .tick)
            add(end - 2_000, .tick)
            add(end - 1_000, .tick)
            add(end, .go)
        }
        return list.sorted { $0.at < $1.at }
    }

    private func fireDueCues(_ nowMs: Int) {
        while cueIdx < cues.count, cues[cueIdx].at <= nowMs {
            switch cues[cueIdx].cue {
            // The 5s warning beeps over the music untouched; ducking only kicks in at the final 3.
            case .warn: beeper.play(.warn)
            case .tick: beeper.duckStart(); beeper.play(.tick)
            case .go: beeper.play(.go); beeper.duckEnd()
            }
            cueIdx += 1
        }
    }

    private func loop() {
        ticker?.invalidate()
        // ~30fps state for a smooth fraction; cues fire on overshoot, so a late tick still plays
        // every cue it stepped over rather than dropping them.
        //
        // `.common` mode, not the default one: a scroll anywhere on screen would otherwise stall
        // the clock's publishing for as long as the finger was down.
        let t = Timer(timeInterval: 0.033, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick() }
        }
        // A Timer defaults to zero tolerance: 30 exact-deadline main-thread wakeups a second for the
        // whole workout, including the 45 minutes spent backgrounded with the screen locked, which is
        // this app's normal mode of use. Slack lets the kernel coalesce them; a Timer never fires
        // early, so the cost is at most 5ms on top of the 33ms of lateness the tick period already
        // allows a cue. The rate stays 30fps — `fraction` needs it the instant the screen returns.
        t.tolerance = 0.005
        RunLoop.main.add(t, forMode: .common)
        ticker = t
    }

    private func tick() {
        guard let w = workout, !paused else { return }
        fireDueCues(activeElapsed())
        if publish(w) {
            ticker?.invalidate()
            ticker = nil
            // Nothing left to stay resident for. The Done screen is just a view; it needs no audio
            // session, and holding one open would keep the user's music ducked-adjacent for nothing.
            //
            // But the final GO is scheduled on this very tick and only starts on the next audio
            // render cycle, so stopping here swallowed it — the last thing the user heard was the
            // 1s tick, which is precisely the cue that matters with the phone in a pocket. Let the
            // 0.32s tone ring out first. `state.done` is only still true if the user is sat on the
            // Done screen; End, or backgrounding with run-in-background off, both put the state
            // back to idle and make this a no-op. The Task inherits main-actor isolation, so it
            // touches `state`/`beeper` on the same actor as the tick itself.
            Task {
                try? await Task.sleep(for: .milliseconds(500))
                if self.state.done { self.beeper.stop() }
            }
        }
    }

    /// Emits the current snapshot; returns true when the workout is done.
    @discardableResult
    private func publish(_ w: Workout) -> Bool {
        let p = w.progressAt(activeElapsed())
        state = TimerUiState(
            running: true,
            paused: paused,
            phase: p.phase,
            remainingMs: p.remainingMs,
            intervalDurationMs: p.intervalDurationMs,
            fraction: p.fraction,
            round: p.round,
            totalRounds: totalRounds,
            roundsPerPass: roundsPerPass,
            done: p.done,
            label: p.label
        )
        // No stale-snapshot guard here, unlike the Android service. There, pause()/stop() ran on the
        // main thread while the tick ran on a background dispatcher, so a tick already in flight
        // could republish a running state over a paused or torn-down one. This class is main-actor
        // isolated and the ticker fires on the main run loop, so a tick and a stop() cannot
        // interleave in the first place.
        return p.done
    }
}
