import XCTest
@testable import IntervalTimerCore

private func w(_ s: Int) -> SeqInterval { SeqInterval(.work, s) }
private func r(_ s: Int) -> SeqInterval { SeqInterval(.rest, s) }

final class GroupingTests: XCTestCase {

    func testTabataGroupsToOnePairBlock() {
        let flat = (1...8).flatMap { _ in [w(20), r(10)] }
        let blocks = groupIntervals(flat)
        XCTAssertEqual([Block([w(20), r(10)], 8)], blocks)
        XCTAssertEqual(flat, flatten(blocks))
    }

    func testEmomGroupsToOneSingleBlock() {
        XCTAssertEqual([Block([w(60)], 10)], groupIntervals((1...10).map { _ in w(60) }))
    }

    /// Nothing repeats, so every group is ×1 — but a work still keeps the rest that follows it.
    func testNonRepeatingLadderPairsEachWorkWithItsRest() {
        let flat = [w(20), r(20), w(30), r(20), w(40)]
        let blocks = groupIntervals(flat)
        XCTAssertEqual([Block([w(20), r(20)], 1), Block([w(30), r(20)], 1), Block([w(40)], 1)], blocks)
        XCTAssertEqual(flat, flatten(blocks))
    }

    /// The pairing must not swallow the first interval of a pattern that starts right after it.
    func testPairingLeavesALaterRepeatIntact() {
        let flat = [w(20), r(20)] + (1...3).flatMap { _ in [w(30), r(10)] }
        XCTAssertEqual([Block([w(20), r(20)], 1), Block([w(30), r(10)], 3)], groupIntervals(flat))
        XCTAssertEqual(flat, flatten(groupIntervals(flat)))
    }

    func testMixedSequenceGroupsGreedily() {
        // (30,15)×3 then a lone 60s work.
        let flat = (1...3).flatMap { _ in [w(30), r(15)] } + [w(60)]
        let blocks = groupIntervals(flat)
        XCTAssertEqual([Block([w(30), r(15)], 3), Block([w(60)], 1)], blocks)
        XCTAssertEqual(flat, flatten(blocks))
    }

    func testToWorkoutDropsTrailingRest() {
        let p = Preset("t", [w(30), r(15), w(30), r(15)])
        XCTAssertEqual([.work, .rest, .work], p.toWorkout(prepareMs: 0).intervals.map(\.phase))
    }

    func testHomePresetPlaysBlocksInOrder() {
        // The rest closing block 1 is mid-sequence, so it must survive; only the rest at the very
        // end of everything is dropped, and that at run time, not here.
        let p = homePreset([basicBlock(workSec: 30, restSec: 15, rounds: 2),
                            basicBlock(workSec: 60, restSec: 45, rounds: 2)])
        XCTAssertEqual([w(30), r(15), w(30), r(15), w(60), r(45), w(60), r(45)], p.intervals)
        XCTAssertEqual([w(30), r(15), w(30), r(15), w(60), r(45), w(60)], p.playbackIntervals())
        // No rest dialled in → none inserted between rounds.
        XCTAssertEqual([w(30), w(30), w(60)],
                       homePreset([basicBlock(workSec: 30, restSec: 0, rounds: 2),
                                   basicBlock(workSec: 60, restSec: 0, rounds: 1)]).intervals)
    }
}
