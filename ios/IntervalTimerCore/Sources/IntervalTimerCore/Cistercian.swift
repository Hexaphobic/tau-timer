import Foundation

/// A line in unit space: the stave runs (0,-1)..(0,1), quadrants extend one unit sideways.
public struct Stroke: Equatable, Sendable {
    public let x1: Double, y1: Double, x2: Double, y2: Double
    public init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
        self.x1 = x1; self.y1 = y1; self.x2 = x2; self.y2 = y2
    }
}

// Digit shapes in the top-right quadrant, local coords x=0...1 (out from stave), y=0...1 (down from top).
private let DIGITS: [[Stroke]] = [
    [],                                                          // 0 — nothing drawn
    [Stroke(0, 0, 1, 0)],                                        // 1 top bar
    [Stroke(0, 1, 1, 1)],                                        // 2 lower bar
    [Stroke(0, 0, 1, 1)],                                        // 3 down-diagonal
    [Stroke(0, 1, 1, 0)],                                        // 4 up-diagonal
    [Stroke(0, 0, 1, 0), Stroke(0, 1, 1, 0)],                    // 5 = 1 + 4
    [Stroke(1, 0, 1, 1)],                                        // 6 outer vertical
    [Stroke(0, 0, 1, 0), Stroke(1, 0, 1, 1)],                    // 7 = 1 + 6
    [Stroke(0, 1, 1, 1), Stroke(1, 0, 1, 1)],                    // 8 = 2 + 6
    [Stroke(0, 0, 1, 0), Stroke(0, 1, 1, 1), Stroke(1, 0, 1, 1)],// 9
]

/// Strokes for `n` in 0...9999, including the stave. Units go top-right, tens top-left,
/// hundreds bottom-right, thousands bottom-left — each quadrant the same nine shapes mirrored.
public func cistercian(_ n: Int) -> [Stroke] {
    precondition((0...9999).contains(n), "Cistercian numerals cover 0...9999, got \(n)")
    var out = [Stroke(0, -1, 0, 1)]
    // (digit, x sign, y flip): flipped quadrants measure downward from the bottom of the stave.
    let quadrants: [(Int, Double, Bool)] = [
        (n % 10, 1, false),
        (n / 10 % 10, -1, false),
        (n / 100 % 10, 1, true),
        (n / 1000, -1, true),
    ]
    for (digit, sx, flip) in quadrants {
        for s in DIGITS[digit] {
            // + 0 folds IEEE -0.0 back to 0.0 so equality behaves.
            out.append(flip
                ? Stroke(s.x1 * sx + 0, 1 - s.y1, s.x2 * sx + 0, 1 - s.y2)
                : Stroke(s.x1 * sx + 0, s.y1 - 1, s.x2 * sx + 0, s.y2 - 1))
        }
    }
    return out
}

/// A countdown's remaining milliseconds as the number to draw: whole seconds, rounded up so a
/// fresh interval reads its full duration and only shows 0 at true zero — the same rule `formatMs`
/// uses, so the two clocks never disagree by a second.
///
/// Clamped, because the numeral system stops at 9999 and `cistercian` traps past it. That's a
/// 2h46m interval; a wrong glyph beats a crash on the one workout that reaches it.
public func cistercianSeconds(_ remainingMs: Int) -> Int {
    min((max(remainingMs, 0) + 999) / 1000, 9999)
}
