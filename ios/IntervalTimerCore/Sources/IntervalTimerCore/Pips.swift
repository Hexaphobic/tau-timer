import Foundation

/// How the timer's round pips wrap — one square per round, and the same rule on both platforms.
///
/// Squares whatever the count: the old layout drew wide pills up to eight and squares above that, so
/// adding a ninth round changed the shape of the thing rather than just adding to it.
public enum Pips {

    /// Widest a wrapped row gets.
    public static let perRow = 8

    /// Up to this many stay on one line, even though it runs past `perRow`.
    public static let singleRowMax = 15

    /// Past this even a grid is a wall of dots, and the caller draws a plain bar instead.
    public static let max = 32

    /// Tallest the grid gets, which is what the timer screen reserves room for.
    public static let maxRows = (max + perRow - 1) / perRow

    /// Pips per row, or empty when `total` is past `max` and should be drawn as a bar.
    ///
    /// `pass` is the workout's own shape — the sets in one pass, when the whole thing runs more than
    /// once. Given it, the grid stops being a wrapped count and becomes the structure: four sets run
    /// twice is two rows of four, which says "this again" in a way one row of eight cannot. Zero
    /// means there is no shape to show and the count wraps as below.
    ///
    /// Two wrapping rules, and the second is the one that isn't obvious. Rows never fill to the cap
    /// and leave a stub — eleven is 6 and 5, not 10 and 1 — because a short last row reads as a count
    /// that ran out of room rather than as a shape. And a count that only just exceeds the cap stays
    /// on one line anyway: twelve in a row is still countable at a glance, whereas splitting it
    /// wastes a whole second line on six.
    public static func rows(_ total: Int, pass: Int = 0) -> [Int] {
        guard (1...max).contains(total) else { return [] }
        // One pass is not a shape, one pip per row is a column rather than one, and a row past
        // singleRowMax is not countable at a glance. All three fall through to the wrap, which is
        // bounded by maxRows and perRow and always drawable.
        let passes = pass > 0 ? total / pass : 0
        if (2...singleRowMax).contains(pass), (2...maxRows).contains(passes), passes * pass == total {
            return Array(repeating: pass, count: passes)
        }
        if total <= singleRowMax { return [total] }
        let rows = (total + perRow - 1) / perRow
        return (0..<rows).map { total / rows + ($0 < total % rows ? 1 : 0) }
    }
}
