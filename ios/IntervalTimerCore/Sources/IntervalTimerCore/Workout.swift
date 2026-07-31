import Foundation

/// Which kind of interval is active. `prepare` is the one-time get-ready lead-in.
///
/// Raw values are the Android names, so a presets file written by either app reads on the other.
public enum Phase: String, Codable, CaseIterable, Sendable {
    case prepare = "PREPARE"
    case work = "WORK"
    case rest = "REST"
    case done = "DONE"
}

/// One block of the workout. `round` is 1-based for work/rest, 0 for prepare.
public struct Interval: Equatable, Sendable {
    public let phase: Phase
    public let durationMs: Int
    public let round: Int

    public init(_ phase: Phase, _ durationMs: Int, round: Int = 0) {
        self.phase = phase
        self.durationMs = durationMs
        self.round = round
    }
}

/// Snapshot of where the clock is at a given moment.
public struct TimerProgress: Equatable, Sendable {
    public let phase: Phase
    public let round: Int
    public let remainingMs: Int
    public let intervalDurationMs: Int
    public let done: Bool

    /// 0 at interval start, 1 at interval end — drives the aura + perimeter stroke.
    public var fraction: Double {
        guard intervalDurationMs > 0 else { return 1 }
        return 1 - Double(remainingMs) / Double(intervalDurationMs)
    }

    public init(phase: Phase, round: Int, remainingMs: Int, intervalDurationMs: Int, done: Bool) {
        self.phase = phase
        self.round = round
        self.remainingMs = remainingMs
        self.intervalDurationMs = intervalDurationMs
        self.done = done
    }
}

/// Pure, device-independent interval clock.
///
/// Every reading derives from a single absolute `activeElapsedMs` (milliseconds of *running* time
/// since the workout started, excluding paused time) mapped against the fixed interval durations.
/// Nothing accumulates per-tick deltas, so sampling at any moment yields the exact same answer as
/// any other path to that moment — this is what makes the timer drift-free.
///
/// The engine feeds it `CACurrentMediaTime()`-based active time (the iOS counterpart of Android's
/// `elapsedRealtime()`: monotonic, unaffected by wall-clock changes). This type has no Apple-platform
/// dependencies at all, so it is unit-testable from the command line.
public struct Workout: Sendable {
    public let intervals: [Interval]
    public let totalMs: Int

    public init(_ intervals: [Interval]) {
        self.intervals = intervals
        self.totalMs = intervals.reduce(0) { $0 + $1.durationMs }
    }

    public func progressAt(_ activeElapsedMs: Int) -> TimerProgress {
        if intervals.isEmpty || activeElapsedMs >= totalMs {
            let last = intervals.last
            return TimerProgress(
                phase: .done,
                round: last?.round ?? 0,
                remainingMs: 0,
                intervalDurationMs: last?.durationMs ?? 0,
                done: true
            )
        }
        let t = max(activeElapsedMs, 0)
        var acc = 0
        for iv in intervals {
            let end = acc + iv.durationMs
            if t < end {
                return TimerProgress(
                    phase: iv.phase,
                    round: iv.round,
                    remainingMs: end - t,
                    intervalDurationMs: iv.durationMs,
                    done: false
                )
            }
            acc = end
        }
        // Unreachable given the totalMs guard above, but keep it total.
        let last = intervals[intervals.count - 1]
        return TimerProgress(phase: .done, round: last.round, remainingMs: 0,
                             intervalDurationMs: last.durationMs, done: true)
    }
}

/// Base mode: a single prepare lead-in, then `rounds` of work, with rest between rounds (no
/// trailing rest after the final round). Sequence mode builds its interval list directly instead.
public func baseWorkout(prepareMs: Int, workMs: Int, restMs: Int, rounds: Int) -> Workout {
    var list: [Interval] = []
    if prepareMs > 0 { list.append(Interval(.prepare, prepareMs)) }
    if rounds >= 1 {
        for r in 1...rounds {
            list.append(Interval(.work, workMs, round: r))
            if restMs > 0 && r < rounds { list.append(Interval(.rest, restMs, round: r)) }
        }
    }
    return Workout(list)
}
