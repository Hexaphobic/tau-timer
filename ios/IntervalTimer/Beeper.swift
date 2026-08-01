import AVFoundation
import UIKit

enum Cue { case warn, tick, go }

/// Loads the three cue tones and plays them into a running `AVAudioEngine`.
///
/// The engine is doing two jobs. The obvious one is the cues. The load-bearing one is that a
/// running engine under a `.playback` audio session is what keeps the app executing once it is
/// backgrounded or the screen locks — this is the iOS counterpart of Android's foreground service
/// plus wake lock, and the reason the timer's own in-process clock keeps ticking rather than
/// needing dozens of pre-scheduled notifications (iOS keeps only the soonest 64 per app, which a
/// 16-interval Tabata blows straight through at five cues each).
///
/// The session is `.playback` so cues sound through the ring/silent switch — the timer's whole
/// point — and `.mixWithOthers` so activating it doesn't stop the user's music.
final class Beeper {

    private let engine = AVAudioEngine()
    private var nodes: [Cue: AVAudioPlayerNode] = [:]
    private var buffers: [Cue: AVAudioPCMBuffer] = [:]

    /// A looping buffer of digital silence. The engine's output unit renders continuously once
    /// started, but a graph with nothing scheduled on it is a thing iOS is entitled to treat as
    /// "not playing" — and a suspended app is a stopped workout. Four lines to remove the doubt.
    private let silenceNode = AVAudioPlayerNode()
    private var silenceBuffer: AVAudioPCMBuffer?

    private var ducking = false
    private var running = false

    init() {
        for (cue, name) in [(Cue.warn, "warn5"), (.tick, "tick"), (.go, "go")] {
            guard let buf = Beeper.load(name) else { continue }
            let node = AVAudioPlayerNode()
            engine.attach(node)
            engine.connect(node, to: engine.mainMixerNode, format: buf.format)
            nodes[cue] = node
            buffers[cue] = buf
        }
        if let fmt = AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 2),
           let buf = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: AVAudioFrameCount(fmt.sampleRate)) {
            buf.frameLength = buf.frameCapacity   // zero-filled on allocation: silence
            silenceBuffer = buf
            engine.attach(silenceNode)
            engine.connect(silenceNode, to: engine.mainMixerNode, format: fmt)
        }

        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(handleInterruption),
                           name: AVAudioSession.interruptionNotification, object: nil)
        // A route change (headphones out, a call ending on a Bluetooth headset) can tear the engine
        // down. Without this the rest of the workout runs silently, which on this app is the same
        // as not running at all.
        center.addObserver(self, selector: #selector(handleRestart),
                           name: .AVAudioEngineConfigurationChange, object: engine)
        // And `.ended` never arrives if iOS suspended us during the interruption — which it is free
        // to do the moment `.began` stopped the engine, because the stopped engine is no longer what
        // holds the app up. That left `running == true` over a dead engine for the rest of the
        // workout: `activeElapsed()` is a monotonic-clock read, so the count on screen stayed
        // correct while every remaining cue was silent and background residency was gone. Becoming
        // active again is the backstop, and it is an event rather than a poll.
        center.addObserver(self, selector: #selector(handleRestart),
                           name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    deinit { NotificationCenter.default.removeObserver(self) }

    private static func load(_ name: String) -> AVAudioPCMBuffer? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "wav"),
              let file = try? AVAudioFile(forReading: url),
              let buf = AVAudioPCMBuffer(pcmFormat: file.processingFormat,
                                         frameCapacity: AVAudioFrameCount(file.length))
        else { return nil }
        try? file.read(into: buf)
        return buf
    }

    func start() {
        guard !running else { return }
        applyCategory(duck: false)
        try? AVAudioSession.sharedInstance().setActive(true)
        startEngine()
        running = true
    }

    func stop() {
        guard running else { return }
        running = false
        duckEnd()
        silenceNode.stop()
        for node in nodes.values { node.stop() }
        engine.stop()
        // Let whatever was playing before come back up to full volume.
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func startEngine() {
        engine.prepare()
        do { try engine.start() } catch { return }
        if let silenceBuffer {
            silenceNode.scheduleBuffer(silenceBuffer, at: nil, options: .loops, completionHandler: nil)
            silenceNode.play()
        }
        for node in nodes.values where !node.isPlaying { node.play() }
    }

    func play(_ cue: Cue) {
        guard running, !Settings.shared.muted,
              let buf = buffers[cue], let node = nodes[cue] else { return }
        let v = Settings.shared.volume
        // The transition whoosh is the one that matters mid-set, so push it louder than the ticks.
        node.volume = Float(cue == .go ? min(v * 1.6, 1) : v)
        // `.interrupts` rather than stop-then-play: a cue landing while the previous one still rings
        // replaces it in one step, with no window where the node is stopped and a play() is pending.
        node.scheduleBuffer(buf, at: nil, options: .interrupts, completionHandler: nil)
        if !node.isPlaying { node.play() }
    }

    // The 5s warning beeps over the music untouched; ducking only kicks in at the final three ticks
    // and is released at GO, so the user's own music dips for the cluster and returns between them.
    //
    // ponytail: iOS has no per-sound duck, so this re-applies the category with `.duckOthers` on and
    // off. It is the only way to get Android's behaviour without deactivating the session, which
    // would end background execution mid-workout. Fails soft — a throw here just means no ducking.
    func duckStart() {
        guard !Settings.shared.muted, !ducking else { return }
        ducking = true
        applyCategory(duck: true)
    }

    func duckEnd() {
        guard ducking else { return }
        ducking = false
        applyCategory(duck: false)
    }

    private func applyCategory(duck: Bool) {
        var options: AVAudioSession.CategoryOptions = [.mixWithOthers]
        if duck { options.insert(.duckOthers) }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: options)
    }

    @objc private func handleInterruption(_ note: Notification) {
        // `userInfo` is read here rather than inside the hop, so the Notification itself is never
        // captured across threads.
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        switch type {
        case .began:
            // The system has already silenced us; matching that in the engine's own state is what
            // makes `restart()`'s `isRunning` check tell the truth afterwards.
            DispatchQueue.main.async { [self] in engine.stop() }
        case .ended:
            restart()
        @unknown default:
            break
        }
    }

    @objc private func handleRestart(_ note: Notification) { restart() }

    /// Re-establishes the session and engine after anything tore them down.
    ///
    /// Everything past the hop runs on the main thread, which is where `TimerEngine` (`@MainActor`)
    /// drives `play()`, `start()` and `stop()` from. `AVAudioEngineConfigurationChange` is posted on
    /// an arbitrary thread, and the interruption notification promises nothing either, so without
    /// this a route change landing between a cue's `node.volume` write and its `scheduleBuffer` was
    /// an unsynchronised mutation of the same graph — and `running` was read and written from two
    /// threads at once. The hop also keeps every engine call off the internal queue that posted the
    /// notification, which is the re-entrancy `AVAudioEngine.h` warns can deadlock.
    private func restart() {
        DispatchQueue.main.async { [self] in
            guard running, !engine.isRunning else { return }
            // Fails soft while an interruption is genuinely still in progress: `setActive` throws,
            // `engine.start()` throws, `startEngine()` returns, and the next `.ended` — or the next
            // trip to the foreground — tries again. Nothing here retries on its own.
            try? AVAudioSession.sharedInstance().setActive(true)
            startEngine()
        }
    }
}
