import Foundation

/// Immutable snapshot the engine publishes and the UI renders. The single contract.
public struct TimerUiState: Equatable, Sendable {
    public var running: Bool
    public var paused: Bool
    public var phase: Phase
    public var remainingMs: Int
    public var intervalDurationMs: Int
    public var fraction: Double
    public var round: Int
    public var totalRounds: Int
    public var done: Bool

    public init(
        running: Bool = false,
        paused: Bool = false,
        phase: Phase = .prepare,
        remainingMs: Int = 0,
        intervalDurationMs: Int = 0,
        fraction: Double = 0,
        round: Int = 0,
        totalRounds: Int = 0,
        done: Bool = false
    ) {
        self.running = running
        self.paused = paused
        self.phase = phase
        self.remainingMs = remainingMs
        self.intervalDurationMs = intervalDurationMs
        self.fraction = fraction
        self.round = round
        self.totalRounds = totalRounds
        self.done = done
    }

    public static let idle = TimerUiState()
}

/// A settable duration: "45s" under a minute, "1:30" at a minute or more.
public func secLabel(_ sec: Int) -> String {
    sec < 60 ? "\(sec)s" : "\(sec / 60):\(pad2(sec % 60))"
}

/// Ceil-to-second clock so a fresh interval reads its full duration and only hits 0 at true zero.
/// Under a minute: bare seconds (16, 30). A minute or more: M:SS with no leading zero (1:20).
public func formatMs(_ ms: Int) -> String {
    let totalSec = (max(ms, 0) + 999) / 1000
    let min = totalSec / 60
    let sec = totalSec % 60
    return min == 0 ? "\(sec)" : "\(min):\(pad2(sec))"
}

private func pad2(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }
