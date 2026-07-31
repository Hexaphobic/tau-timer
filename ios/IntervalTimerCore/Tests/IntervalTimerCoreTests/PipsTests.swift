import XCTest
@testable import IntervalTimerCore

/// Mirror of app/src/test/java/com/chrispoole/intervaltimer/model/PipsTest.kt — keep them in step.
final class PipsTests: XCTestCase {

    func testShortCountsStayOnOneLine() {
        XCTAssertEqual(Pips.rows(1), [1])
        XCTAssertEqual(Pips.rows(8), [8])
        XCTAssertEqual(Pips.rows(12), [12])   // not 6 + 6
        XCTAssertEqual(Pips.rows(15), [15])
    }

    func testWrappedRowsSplitEvenlyRatherThanLeavingAStub() {
        XCTAssertEqual(Pips.rows(16), [8, 8])
        XCTAssertEqual(Pips.rows(17), [6, 6, 5])   // not 8 + 8 + 1
        XCTAssertEqual(Pips.rows(20), [7, 7, 6])
        XCTAssertEqual(Pips.rows(24), [8, 8, 8])
        XCTAssertEqual(Pips.rows(25), [7, 6, 6, 6])
        XCTAssertEqual(Pips.rows(32), [8, 8, 8, 8])
    }

    func testPastThirtyTwoTheCallerDrawsABar() {
        XCTAssertEqual(Pips.rows(33), [])
        XCTAssertEqual(Pips.rows(500), [])
        // A round count is unbounded — a group's ×N and the overall ×N multiply — so the grid has to
        // decline politely rather than try to lay out Int.max squares.
        XCTAssertEqual(Pips.rows(Int.max), [])
        // Nothing produces these, but neither may trap: the timer is what draws them.
        XCTAssertEqual(Pips.rows(0), [])
        XCTAssertEqual(Pips.rows(-1), [])
    }

    /// A pass is a row: four sets run twice reads as two rows of four, not one row of eight.
    func testAPassGetsItsOwnRow() {
        XCTAssertEqual(Pips.rows(8, pass: 4), [4, 4])
        XCTAssertEqual(Pips.rows(9, pass: 3), [3, 3, 3])
        XCTAssertEqual(Pips.rows(8, pass: 2), [2, 2, 2, 2])
        // Past perRow is fine here — the row is the shape, and the cells size to whatever it is.
        XCTAssertEqual(Pips.rows(24, pass: 12), [12, 12])
    }

    /// A shape that wouldn't read as one is no better than the wrap, so it falls back to it.
    func testAnUndrawableShapeFallsBackToTheWrap() {
        XCTAssertEqual(Pips.rows(8, pass: 8), [8])            // one pass: nothing to show
        XCTAssertEqual(Pips.rows(8, pass: 0), [8])            // no shape at all
        XCTAssertEqual(Pips.rows(2, pass: 1), [2])            // a column of one-pip rows is not a shape
        XCTAssertEqual(Pips.rows(17, pass: 4), [6, 6, 5])     // 17 is not four of anything
        XCTAssertEqual(Pips.rows(24, pass: 2), [8, 8, 8])     // 12 rows is taller than the screen keeps
        XCTAssertEqual(Pips.rows(32, pass: 16), [8, 8, 8, 8]) // a row of 16 is a wall, not a shape
        XCTAssertEqual(Pips.rows(33, pass: 11), [])           // still a bar past the ceiling
    }

    /// The things the drawing code assumes and never re-checks, now across every shape it can be
    /// handed: every round gets exactly one square, the grid is no taller than the screen reserves,
    /// and no row runs past what one line can hold — which is what lets the cells be sized once, off
    /// the widest row, and centred.
    func testEveryLayoutIsWholeAndDrawable() {
        for n in 1...Pips.max {
            for perRow in 0...n {
                let rows = Pips.rows(n, pass: perRow)
                let place = "\(n) in rows of \(perRow)"
                XCTAssertEqual(rows.reduce(0, +), n, "\(place) lost or gained a square")
                XCTAssertFalse(rows.isEmpty, "\(place) produced no rows")
                XCTAssertLessThanOrEqual(rows.count, Pips.maxRows, "\(place) is taller than the reserved \(rows)")
                XCTAssertTrue(rows.allSatisfy { $0 > 0 }, "\(place) has an empty row: \(rows)")
                XCTAssertLessThanOrEqual(rows.max() ?? 0, Pips.singleRowMax, "\(place) runs past one line: \(rows)")
            }
        }
    }

    /// Wrapped rows — no shape given — stay within one of each other and inside the eight cap.
    func testTheWrapIsEvenAndWithinTheCap() {
        for n in 1...Pips.max {
            let rows = Pips.rows(n)
            if rows.count > 1 {
                XCTAssertLessThanOrEqual(rows.max() ?? 0, Pips.perRow, "\(n) runs past the cap: \(rows)")
                XCTAssertLessThanOrEqual((rows.max() ?? 0) - (rows.min() ?? 0), 1, "\(n) is lopsided: \(rows)")
            }
        }
    }
}
