import XCTest
@testable import IntervalTimerCore

private func w(_ s: Int) -> SeqInterval { SeqInterval(.work, s) }
private func r(_ s: Int) -> SeqInterval { SeqInterval(.rest, s) }

final class RepeatAndRestTests: XCTestCase {

    /// Reference expansion, kept here as the oracle the fast structural count is checked against.
    /// Production never needs it — `backToBackRests` answers the only question anyone asks of an
    /// expanded sequence, and it answers it without building one.
    private func expand(_ blocks: [Block], _ repeatAll: Int = 1) -> [SeqInterval] {
        let once = flatten(blocks)
        return repeatAll <= 1 ? once : Array(repeating: once, count: repeatAll).flatMap { $0 }
    }

    // ---- repeat everything ----

    func testRepeatAllPlaysTheWholeSequenceAgain() {
        let p = Preset("t", [w(30), r(15)], repeatAll: 3)
        XCTAssertEqual([w(30), r(15), w(30), r(15), w(30), r(15)], p.expanded())
        // The rest that would end the workout never plays, so it isn't counted either.
        XCTAssertEqual([w(30), r(15), w(30), r(15), w(30)], p.playbackIntervals())
        XCTAssertEqual(120, p.playbackIntervals().reduce(0) { $0 + $1.durationSec })
    }

    func testPlayableOnlyDropsATrailingRest() {
        XCTAssertEqual([w(30), r(15), w(20)], playable([w(30), r(15), w(20)]))
        XCTAssertEqual([w(30), r(15), w(20)], playable([w(30), r(15), w(20), r(10)]))
        // A lone rest is the whole sequence — dropping it would leave nothing to run.
        XCTAssertEqual([r(10)], playable([r(10)]))
        XCTAssertEqual([], playable([]))
    }

    func testOnceThroughIsUntouched() {
        let once = [w(30), r(15), w(20)]
        XCTAssertEqual(once, Preset("t", once).expanded())
    }

    /// The rest *between* two passes has to survive — only the one that would end the workout goes.
    func testOnlyTheVeryLastRestIsDropped() {
        let p = Preset("t", [w(30), r(15)], repeatAll: 3)
        XCTAssertEqual([.work, .rest, .work, .rest, .work],
                       p.toWorkout(prepareMs: 0).intervals.map(\.phase))
    }

    func testRoundsAreNumberedAcrossTheWholeExpansion() {
        let p = Preset("t", [w(10)], repeatAll: 3)
        XCTAssertEqual([1, 2, 3], p.toWorkout(prepareMs: 0).intervals.map(\.round))
    }

    func testGroupingRoundTripsWithRepeatAll() {
        let p = Preset("t", (1...3).flatMap { _ in [w(30), r(15)] }, repeatAll: 4)
        let blocks = groupIntervals(p.intervals)
        XCTAssertEqual([Block([w(30), r(15)], 3)], blocks)
        XCTAssertEqual(p, Preset("t", flatten(blocks), repeatAll: 4))
    }

    // ---- no two rests in a row ----

    func testCountsAdjacentRests() {
        XCTAssertEqual(0, backToBackRests([w(10), r(5), w(10)]))
        XCTAssertEqual(1, backToBackRests([r(5), r(5)]))
        XCTAssertEqual(2, backToBackRests([r(5), r(5), r(5)]))
        XCTAssertEqual(0, backToBackRests([w(10), r(5)]))
        XCTAssertEqual(1, backToBackRests([w(10), r(5), r(5)]))
    }

    func testAGroupsOwnRepeatCanWrapRestOntoRest() {
        XCTAssertEqual(1, backToBackRests([Block([r(10), w(30), r(10)], 2)], 1))
    }

    func testRepeatingEverythingCanWrapRestOntoRest() {
        let blocks = [Block([r(10), w(30), r(10)], 1)]
        XCTAssertEqual(0, backToBackRests(blocks, 1))
        XCTAssertEqual(1, backToBackRests(blocks, 2))
        XCTAssertEqual(2, backToBackRests(blocks, 3))
    }

    func testRestEndingOneGroupMeetsRestOpeningTheNext() {
        let blocks = [Block([w(30), r(15)], 1), Block([r(10), w(20)], 1)]
        XCTAssertEqual(1, backToBackRests(blocks, 1))
    }

    /// The fast structural count must agree with actually expanding the thing, in every shape.
    func testStructuralCountMatchesTheExpandedOne() {
        let cases: [([Block], Int)] = [
            ([Block([w(30), r(15)], 1)], 3),
            ([Block([r(15), w(30), r(15)], 2)], 2),
            ([Block([w(30), r(15)], 2), Block([r(10), w(20)], 1)], 4),
            ([Block([w(30)], 5)], 2),
            ([Block([r(30)], 3)], 2),
            ([], 3),
            ([Block([w(30), r(15)], 1)], 1),
        ]
        for (blocks, repeatAll) in cases {
            XCTAssertEqual(backToBackRests(expand(blocks, repeatAll)),
                           backToBackRests(blocks, repeatAll),
                           "blocks=\(blocks) repeat=\(repeatAll)")
        }
    }

    func testExpandIsFlattenTimesRepeat() {
        let blocks = [Block([w(30), r(15)], 2)]
        XCTAssertEqual(4, flatten(blocks).count)
        XCTAssertEqual(12, expand(blocks, 3).count)
        XCTAssertEqual(flatten(blocks), expand(blocks, 1))
    }
}
