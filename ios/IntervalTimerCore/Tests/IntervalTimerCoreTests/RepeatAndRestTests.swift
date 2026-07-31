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

    /// A rest is recovery from a set, not a set of its own, so it carries the round it follows.
    func testRestsShareTheRoundOfTheWorkBeforeThem() {
        let p = Preset("t", [w(30), r(15)], repeatAll: 3)
        XCTAssertEqual([1, 1, 2, 2, 3], p.toWorkout(prepareMs: 0).intervals.map(\.round))
    }

    /// Nothing has been done yet, so there is no round to be in.
    func testARestBeforeAnyWorkIsRoundZero() {
        let p = Preset("t", [r(10), w(30)])
        XCTAssertEqual([0, 1], p.toWorkout(prepareMs: 0).intervals.map(\.round))
    }

    /// The shape the home builds, end to end: three sections — the first run twice — with the whole
    /// thing run twice over.
    ///
    /// Four work sets a pass, eight all told, laid out as two rows of four. It counted to FIFTEEN
    /// before this: eight work plus eight rest, less the closing rest the timer drops, drawn as one
    /// wrapped smear of pips that answered no question anyone was asking.
    func testTheHomesOwnShapeCountsWorkSetsAndDrawsAsPasses() {
        let blocks = [
            Block([w(30), r(15)], 2),
            Block([w(30), r(15)], 1),
            Block([w(30), r(15)], 1),
        ]
        let workout = homePreset(blocks, repeatAll: 2).toWorkout(prepareMs: 0)

        XCTAssertEqual(15, workout.intervals.count)   // what plays is unchanged
        XCTAssertEqual([1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8],
                       workout.intervals.map(\.round))
        XCTAssertEqual(8, workout.intervals.map(\.round).max())
        XCTAssertEqual(4, workout.roundsPerPass)
        XCTAssertEqual([4, 4], Pips.rows(8, pass: workout.roundsPerPass))

        // And the time the home puts on GO: 4 × 45s a pass, twice, less the rest that never plays.
        XCTAssertEqual(345, homePreset(blocks, repeatAll: 2).playbackIntervals()
            .reduce(0) { $0 + $1.durationSec })
    }

    /// Run once there is no shape to show, so the pips fall back to wrapping the count.
    func testOneWayThroughHasNoPassToDraw() {
        let blocks = [Block([w(30), r(15)], 4)]
        XCTAssertEqual(0, homePreset(blocks, repeatAll: 1).toWorkout(prepareMs: 0).roundsPerPass)
    }

    /// The number printed above GO must be the workout GO runs, in every shape the home can be put
    /// into — including the daft ones.
    ///
    /// This is a whole-space sweep rather than a handful of cases because the one that broke it was
    /// not a case anyone would think to write down: a single basic section of Work 0 / Rest 15 at one
    /// round. `baseWorkout` plays a lone zero-length work and nothing else, while the sequence builder
    /// drops the empty work, is left holding one rest, and keeps it because a lone interval is the
    /// whole sequence. Two builders, 0s against 15s. Work's floor is 5 so you cannot dial it to zero
    /// — but you can dial a Rest to 0 and tap its label to make it a Work, and the flip does not
    /// re-apply the floor. Reachable, therefore real.
    func testTheTotalOverGoIsTheWorkoutGoRuns() {
        let durations = [0, 5, 30]
        var sections: [Block] = []
        for phase in [Phase.work, Phase.rest] {
            for d in durations {
                for repeatCount in 1...2 {
                    sections.append(Block([SeqInterval(phase, d)], repeatCount))
                    for phase2 in [Phase.work, Phase.rest] {
                        for d2 in durations {
                            sections.append(Block([SeqInterval(phase, d), SeqInterval(phase2, d2)], repeatCount))
                        }
                    }
                }
            }
        }
        func check(_ blocks: [Block], _ repeatAll: Int) {
            // What the screen does: an outer ×N only exists once there is more than one section.
            let effective = blocks.count > 1 ? repeatAll : 1
            let played = homeWorkout(blocks, repeatAll: effective, prepareMs: 5_000)
                .intervals.filter { $0.phase != .prepare }.reduce(0) { $0 + $1.durationMs }
            XCTAssertEqual(played, homeSeconds(blocks, repeatAll: effective) * 1000,
                           "blocks=\(blocks) repeatAll=\(repeatAll)")
        }
        for a in sections { for repeatAll in 1...3 { check([a], repeatAll) } }
        for a in sections { for b in sections { check([a, b], 2) } }
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
