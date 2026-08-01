package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatAndRestTest {
    private fun w(s: Int) = SeqInterval(Phase.WORK, s)
    private fun r(s: Int) = SeqInterval(Phase.REST, s)

    /**
     * Reference expansion, kept here as the oracle the fast structural count is checked against.
     * Production never needs it — [backToBackRests] answers the only question anyone asks of an
     * expanded sequence, and it answers it without building one.
     */
    private fun expand(blocks: List<Block>, repeatAll: Int = 1): List<SeqInterval> {
        val once = flatten(blocks)
        return if (repeatAll <= 1) once else List(repeatAll) { once }.flatten()
    }

    // ---- repeat everything ----

    @Test fun repeatAllPlaysTheWholeSequenceAgain() {
        val p = Preset("t", listOf(w(30), r(15)), repeatAll = 3)
        assertEquals(listOf(w(30), r(15), w(30), r(15), w(30), r(15)), p.expanded())
        // The rest that would end the workout never plays, so it isn't counted either.
        assertEquals(listOf(w(30), r(15), w(30), r(15), w(30)), p.playbackIntervals())
        assertEquals(120, p.playbackIntervals().sumOf { it.durationSec })
    }

    @Test fun playableOnlyDropsATrailingRest() {
        assertEquals(listOf(w(30), r(15), w(20)), playable(listOf(w(30), r(15), w(20))))
        assertEquals(listOf(w(30), r(15), w(20)), playable(listOf(w(30), r(15), w(20), r(10))))
        // A lone rest is the whole sequence — dropping it would leave nothing to run.
        assertEquals(listOf(r(10)), playable(listOf(r(10))))
        assertEquals(emptyList<SeqInterval>(), playable(emptyList()))
    }

    @Test fun onceThroughIsUntouched() {
        val once = listOf(w(30), r(15), w(20))
        assertEquals(once, Preset("t", once).expanded())
    }

    /** The rest *between* two passes has to survive — only the one that would end the workout goes. */
    @Test fun onlyTheVeryLastRestIsDropped() {
        val p = Preset("t", listOf(w(30), r(15)), repeatAll = 3)
        assertEquals(
            listOf(Phase.WORK, Phase.REST, Phase.WORK, Phase.REST, Phase.WORK),
            p.toWorkout(prepareMs = 0).intervals.map { it.phase },
        )
    }

    @Test fun roundsAreNumberedAcrossTheWholeExpansion() {
        val p = Preset("t", listOf(w(10)), repeatAll = 3)
        assertEquals(listOf(1, 2, 3), p.toWorkout(prepareMs = 0).intervals.map { it.round })
    }

    /** A rest is recovery from a set, not a set of its own, so it carries the round it follows. */
    @Test fun restsShareTheRoundOfTheWorkBeforeThem() {
        val p = Preset("t", listOf(w(30), r(15)), repeatAll = 3)
        assertEquals(listOf(1, 1, 2, 2, 3), p.toWorkout(prepareMs = 0).intervals.map { it.round })
    }

    /** Nothing has been done yet, so there is no round to be in. */
    @Test fun aRestBeforeAnyWorkIsRoundZero() {
        val p = Preset("t", listOf(r(10), w(30)))
        assertEquals(listOf(0, 1), p.toWorkout(prepareMs = 0).intervals.map { it.round })
    }

    /**
     * The shape the home builds, end to end: three sections — the first run twice — with the whole
     * thing run twice over.
     *
     * Four work sets a pass, eight all told, laid out as two rows of four. It counted to FIFTEEN
     * before this: eight work plus eight rest, less the closing rest the timer drops, drawn as one
     * wrapped smear of pips that answered no question anyone was asking.
     */
    @Test fun theHomesOwnShapeCountsWorkSetsAndDrawsAsPasses() {
        val blocks = listOf(
            Block(listOf(w(30), r(15)), 2),
            Block(listOf(w(30), r(15)), 1),
            Block(listOf(w(30), r(15)), 1),
        )
        val workout = homePreset(blocks, 2).toWorkout(prepareMs = 0)

        assertEquals(15, workout.intervals.size)   // what plays is unchanged
        assertEquals(
            listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8),
            workout.intervals.map { it.round },
        )
        assertEquals(8, workout.intervals.maxOf { it.round })
        assertEquals(4, workout.roundsPerPass)
        assertEquals(listOf(4, 4), Pips.rows(8, workout.roundsPerPass))

        // And the time the home puts on GO: 4 × 45s a pass, twice, less the rest that never plays.
        assertEquals(345, homePreset(blocks, 2).playbackIntervals().sumOf { it.durationSec })
    }

    /** Run once there is no shape to show, so the pips fall back to wrapping the count. */
    @Test fun oneWayThroughHasNoPassToDraw() {
        val blocks = listOf(Block(listOf(w(30), r(15)), 4))
        assertEquals(0, homePreset(blocks, 1).toWorkout(prepareMs = 0).roundsPerPass)
    }

    /**
     * The number printed above GO must be the workout GO runs, in every shape the home can be put
     * into — including the daft ones.
     *
     * The sets half is swept across the whole space rather than a handful of cases because the one
     * that broke it was not a case anyone would think to write down: a single basic section of
     * Work 0 / Rest 15 at one round. [baseWorkout] plays a lone zero-length work and nothing else,
     * while the sequence builder drops the empty work, is left holding one rest, and keeps it because
     * a lone interval is the whole sequence. Two builders, 0s against 15s. Work's floor is 5 so you
     * cannot dial it to zero — but you can dial a Rest to 0 and tap its label to make it a Work.
     * Reachable, therefore real, and the home is written back on every edit, so a home once put in
     * that shape comes back in it however the flip behaves afterwards.
     *
     * The seconds half cannot be swept the same way. It used to compare the built workout's played
     * total against [homeSeconds], but both are now the same [homeWorkout] call differing only in
     * prepare, and prepare adds nothing but a PREPARE interval — so that assertion held by
     * construction and could not fail whatever the builder did. Nor is there a block-level formula to
     * sweep against instead: any oracle that drops the empty intervals and keeps the rest disagrees
     * with [baseWorkout] on Work 0 / Rest 15 × 1, which is shipped behaviour. So the seconds are
     * pinned as hand-computed constants over shapes that reach both branches — an oracle that cannot
     * go vacuous because the arithmetic was done off the code, not by it.
     */
    @Test fun theTotalOverGoIsTheWorkoutGoRuns() {
        // (sets, seconds), worked out from the shape by hand.
        val cases = listOf(
            // baseWorkout branch: 3 works, and a rest between rounds but not after the last.
            Triple(listOf(basicBlock(30, 15, 3)), 1, 3 to 120L),
            // Rest dialled to 0 puts nothing at all between the rounds.
            Triple(listOf(basicBlock(30, 0, 2)), 1, 2 to 60L),
            // The empty work fails homeWorkout's durationSec guard, so this builds as a sequence: the
            // work is filtered out and the lone rest is the whole workout. Through baseWorkout it
            // would be 0s and one set instead.
            Triple(listOf(basicBlock(0, 15, 1)), 1, 0 to 15L),
            // Same shape at 3 rounds: three rests, the closing one dropped. No work asked of you is
            // no sets, however many rounds the section says.
            Triple(listOf(basicBlock(0, 15, 3)), 1, 0 to 30L),
            // A lone rest survives playable() — dropping it would leave nothing to run.
            Triple(listOf(Block(listOf(r(15)), 1)), 1, 0 to 15L),
            // Sequence branch: 120s a pass, twice, less the 10s rest that would end the workout.
            Triple(listOf(basicBlock(30, 15, 2), basicBlock(20, 10, 1)), 2, 6 to 230L),
        )
        for ((blocks, repeatAll, expected) in cases) {
            val (sets, secs) = expected
            assertEquals("blocks=$blocks repeatAll=$repeatAll", secs, homeSeconds(blocks, repeatAll))
            assertEquals(
                "blocks=$blocks repeatAll=$repeatAll",
                sets.toLong(),
                homeSets(blocks, repeatAll).toLong(),
            )
        }

        val durations = listOf(0, 5, 30)
        val sections = buildList {
            for (phase in listOf(Phase.WORK, Phase.REST)) {
                for (d in durations) {
                    for (repeat in 1..2) {
                        add(Block(listOf(SeqInterval(phase, d)), repeat))
                        for (phase2 in listOf(Phase.WORK, Phase.REST)) {
                            for (d2 in durations) {
                                add(Block(listOf(SeqInterval(phase, d), SeqInterval(phase2, d2)), repeat))
                            }
                        }
                    }
                }
            }
        }
        fun check(blocks: List<Block>, repeatAll: Int) {
            // What the screen does: an outer ×N only exists once there is more than one section.
            val effective = if (blocks.size > 1) repeatAll else 1
            val workout = homeWorkout(blocks, effective, prepareMs = 5_000)
            // The count beside the total: a set you are never asked to do is not a set. Work 0 /
            // Rest 15 × 5 said "5 sets" over five zero-length works the clock walks straight past.
            // The built workout's own work intervals are the second opinion here — homeSets reads
            // rounds off that same workout, and rounds are stamped by position, not by duration.
            assertEquals(
                "blocks=$blocks repeatAll=$repeatAll",
                homeSets(blocks, effective).toLong(),
                workout.intervals.count { it.phase == Phase.WORK && it.durationMs > 0 }.toLong(),
            )
        }
        for (a in sections) for (repeatAll in 1..3) check(listOf(a), repeatAll)
        for (a in sections) for (b in sections) check(listOf(a, b), 2)
    }

    @Test fun groupingRoundTripsWithRepeatAll() {
        val p = Preset("t", (1..3).flatMap { listOf(w(30), r(15)) }, repeatAll = 4)
        val blocks = groupIntervals(p.intervals)
        assertEquals(listOf(Block(listOf(w(30), r(15)), 3)), blocks)
        assertEquals(p, Preset("t", flatten(blocks), 4))
    }

    /**
     * Work, work, rest is one section the home builds first-class, so a preset saved from one has to
     * come back out of the flat list as one section — mis-grouped it reopens as six separate ×1 rows
     * and the ×3 the user wrote is gone from the screen.
     *
     * Both works are the same length deliberately: the len 1 scan finds a decoy [w30] ×2 covering two
     * before the len 3 match covering nine is ever tried, so this fails the moment the scan settles
     * for the first repeat it finds rather than the one covering most.
     */
    @Test fun aWorkWorkRestSectionGroupsAsOneBlock() {
        val flat = (1..3).flatMap { listOf(w(30), w(30), r(15)) }
        val blocks = groupIntervals(flat)
        assertEquals(listOf(Block(listOf(w(30), w(30), r(15)), 3)), blocks)
        assertEquals(flat, flatten(blocks))
    }

    /**
     * The longest pattern the scan reaches for. Aperiodic at len 2 — the two works differ — so no
     * shorter length can match it, and no two rests fall together, so it is a shape `noDoubleRest`
     * would actually let a user build.
     */
    @Test fun aFourLongPatternGroupsAsOneBlock() {
        val flat = (1..2).flatMap { listOf(w(30), r(10), w(20), r(10)) }
        val blocks = groupIntervals(flat)
        assertEquals(listOf(Block(listOf(w(30), r(10), w(20), r(10)), 2)), blocks)
        assertEquals(flat, flatten(blocks))
    }

    // ---- no two rests in a row ----

    @Test fun countsAdjacentRests() {
        assertEquals(0, backToBackRests(listOf(w(10), r(5), w(10))))
        assertEquals(1, backToBackRests(listOf(r(5), r(5))))
        assertEquals(2, backToBackRests(listOf(r(5), r(5), r(5))))
        assertEquals(0, backToBackRests(listOf(w(10), r(5))))
        assertEquals(1, backToBackRests(listOf(w(10), r(5), r(5))))
    }

    @Test fun aGroupsOwnRepeatCanWrapRestOntoRest() {
        val blocks = listOf(Block(listOf(r(10), w(30), r(10)), 2))
        assertEquals(1, backToBackRests(blocks, 1))
    }

    @Test fun repeatingEverythingCanWrapRestOntoRest() {
        val blocks = listOf(Block(listOf(r(10), w(30), r(10)), 1))
        assertEquals(0, backToBackRests(blocks, 1))
        assertEquals(1, backToBackRests(blocks, 2))
        assertEquals(2, backToBackRests(blocks, 3))
    }

    @Test fun restEndingOneGroupMeetsRestOpeningTheNext() {
        val blocks = listOf(Block(listOf(w(30), r(15)), 1), Block(listOf(r(10), w(20)), 1))
        assertEquals(1, backToBackRests(blocks, 1))
    }

    /** The fast structural count must agree with actually expanding the thing, in every shape. */
    @Test fun structuralCountMatchesTheExpandedOne() {
        val cases: List<Pair<List<Block>, Int>> = listOf(
            listOf(Block(listOf(w(30), r(15)), 1)) to 3,
            listOf(Block(listOf(r(15), w(30), r(15)), 2)) to 2,
            listOf(Block(listOf(w(30), r(15)), 2), Block(listOf(r(10), w(20)), 1)) to 4,
            listOf(Block(listOf(w(30)), 5)) to 2,
            listOf(Block(listOf(r(30)), 3)) to 2,
            emptyList<Block>() to 3,
            listOf(Block(listOf(w(30), r(15)), 1)) to 1,
        )
        for ((blocks, repeat) in cases) {
            assertEquals(
                "blocks=$blocks repeat=$repeat",
                backToBackRests(expand(blocks, repeat)).toLong(),
                backToBackRests(blocks, repeat).toLong(),
            )
        }
    }

    @Test fun expandIsFlattenTimesRepeat() {
        val blocks = listOf(Block(listOf(w(30), r(15)), 2))
        assertEquals(4, flatten(blocks).size)
        assertEquals(12, expand(blocks, 3).size)
        assertEquals(flatten(blocks), expand(blocks, 1))
    }
}
