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
    /// The sets half is swept across the whole space rather than a handful of cases because the one
    /// that broke it was not a case anyone would think to write down: a single basic section of
    /// Work 0 / Rest 15 at one round. `baseWorkout` plays a lone zero-length work and nothing else,
    /// while the sequence builder drops the empty work, is left holding one rest, and keeps it
    /// because a lone interval is the whole sequence. Two builders, 0s against 15s. Work's floor is 5
    /// so you cannot dial it to zero — but you can dial a Rest to 0 and tap its label to make it a
    /// Work. Reachable, therefore real, and the home is written back on every edit, so a home once
    /// put in that shape comes back in it however the flip behaves afterwards.
    ///
    /// The seconds half cannot be swept the same way. It used to compare the built workout's played
    /// total against `homeSeconds`, but both are now the same `homeWorkout` call differing only in
    /// prepare, and prepare adds nothing but a prepare interval — so that assertion held by
    /// construction and could not fail whatever the builder did. Nor is there a block-level formula
    /// to sweep against instead: any oracle that drops the empty intervals and keeps the rest
    /// disagrees with `baseWorkout` on Work 0 / Rest 15 × 1, which is shipped behaviour. So the
    /// seconds are pinned as hand-computed constants over shapes that reach both branches — an oracle
    /// that cannot go vacuous because the arithmetic was done off the code, not by it.
    func testTheTotalOverGoIsTheWorkoutGoRuns() {
        // (sets, seconds), worked out from the shape by hand.
        let cases: [([Block], Int, (sets: Int, seconds: Int))] = [
            // baseWorkout branch: 3 works, and a rest between rounds but not after the last.
            ([basicBlock(workSec: 30, restSec: 15, rounds: 3)], 1, (3, 120)),
            // Rest dialled to 0 puts nothing at all between the rounds.
            ([basicBlock(workSec: 30, restSec: 0, rounds: 2)], 1, (2, 60)),
            // The empty work fails homeWorkout's durationSec guard, so this builds as a sequence: the
            // work is filtered out and the lone rest is the whole workout. Through baseWorkout it
            // would be 0s and one set instead.
            ([basicBlock(workSec: 0, restSec: 15, rounds: 1)], 1, (0, 15)),
            // Same shape at 3 rounds: three rests, the closing one dropped. No work asked of you is
            // no sets, however many rounds the section says.
            ([basicBlock(workSec: 0, restSec: 15, rounds: 3)], 1, (0, 30)),
            // A lone rest survives playable() — dropping it would leave nothing to run.
            ([Block([r(15)], 1)], 1, (0, 15)),
            // Sequence branch: 120s a pass, twice, less the 10s rest that would end the workout.
            ([basicBlock(workSec: 30, restSec: 15, rounds: 2),
              basicBlock(workSec: 20, restSec: 10, rounds: 1)], 2, (6, 230)),
        ]
        for (blocks, repeatAll, expected) in cases {
            XCTAssertEqual(expected.seconds, homeSeconds(blocks, repeatAll: repeatAll),
                           "blocks=\(blocks) repeatAll=\(repeatAll)")
            XCTAssertEqual(expected.sets, homeSets(blocks, repeatAll: repeatAll),
                           "blocks=\(blocks) repeatAll=\(repeatAll)")
        }

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
            let workout = homeWorkout(blocks, repeatAll: effective, prepareMs: 5_000)
            // The count beside the total: a set you are never asked to do is not a set. Work 0 /
            // Rest 15 × 5 said "5 sets" over five zero-length works the clock walks straight past.
            // The built workout's own work intervals are the second opinion here — homeSets reads
            // rounds off that same workout, and rounds are stamped by position, not by duration.
            XCTAssertEqual(homeSets(blocks, repeatAll: effective),
                           workout.intervals.filter { $0.phase == .work && $0.durationMs > 0 }.count,
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

    /// Work, work, rest is one section the home builds first-class, so a preset saved from one has to
    /// come back out of the flat list as one section — mis-grouped it reopens as six separate ×1 rows
    /// and the ×3 the user wrote is gone from the screen.
    ///
    /// Both works are the same length deliberately: the len 1 scan finds a decoy [w30] ×2 covering
    /// two before the len 3 match covering nine is ever tried, so this fails the moment the scan
    /// settles for the first repeat it finds rather than the one covering most.
    func testAWorkWorkRestSectionGroupsAsOneBlock() {
        let flat = (1...3).flatMap { _ in [w(30), w(30), r(15)] }
        let blocks = groupIntervals(flat)
        XCTAssertEqual([Block([w(30), w(30), r(15)], 3)], blocks)
        XCTAssertEqual(flat, flatten(blocks))
    }

    /// The longest pattern the scan reaches for. Aperiodic at len 2 — the two works differ — so no
    /// shorter length can match it, and no two rests fall together, so it is the ordinary shape of a
    /// sequence somebody actually builds.
    func testAFourLongPatternGroupsAsOneBlock() {
        let flat = (1...2).flatMap { _ in [w(30), r(10), w(20), r(10)] }
        let blocks = groupIntervals(flat)
        XCTAssertEqual([Block([w(30), r(10), w(20), r(10)], 2)], blocks)
        XCTAssertEqual(flat, flatten(blocks))
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
