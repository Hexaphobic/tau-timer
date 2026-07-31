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

    /// The three things the drawing code assumes and never re-checks: every round gets exactly one
    /// square, no row runs past the cap once it has wrapped, and the rows are within one of each
    /// other — which is what lets the cells be sized once, off the widest row, and centred.
    func testEveryLayoutIsWholeEvenAndWithinTheCap() {
        for n in 1...Pips.max {
            let rows = Pips.rows(n)
            XCTAssertEqual(rows.reduce(0, +), n, "\(n) lost or gained a square")
            XCTAssertFalse(rows.isEmpty, "\(n) produced no rows")
            XCTAssertLessThanOrEqual(rows.count, Pips.maxRows, "\(n) is taller than the reserved \(rows)")
            if rows.count > 1 {
                XCTAssertLessThanOrEqual(rows.max() ?? 0, Pips.perRow, "\(n) runs past the cap: \(rows)")
                XCTAssertLessThanOrEqual((rows.max() ?? 0) - (rows.min() ?? 0), 1, "\(n) is lopsided: \(rows)")
            }
        }
    }
}
