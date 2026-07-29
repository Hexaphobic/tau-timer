package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatAndRestTest {
    private fun w(s: Int) = SeqInterval(Phase.WORK, s)
    private fun r(s: Int) = SeqInterval(Phase.REST, s)

    // ---- repeat everything ----

    @Test fun repeatAllPlaysTheWholeSequenceAgain() {
        val p = Preset("t", listOf(w(30), r(15)), repeatAll = 3)
        assertEquals(listOf(w(30), r(15), w(30), r(15), w(30), r(15)), p.expanded())
        assertEquals(135, p.totalSec())
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

    @Test fun groupingRoundTripsWithRepeatAll() {
        val p = Preset("t", (1..3).flatMap { listOf(w(30), r(15)) }, repeatAll = 4)
        val blocks = groupIntervals(p.intervals)
        assertEquals(listOf(Block(listOf(w(30), r(15)), 3)), blocks)
        assertEquals(p, Preset("t", flatten(blocks), 4))
    }

    // ---- no two rests in a row ----

    @Test fun countsAdjacentRests() {
        assertEquals(0, backToBackRests(listOf(w(10), r(5), w(10))))
        assertEquals(1, backToBackRests(listOf(r(5), r(5))))
        assertEquals(2, backToBackRests(listOf(r(5), r(5), r(5))))
        assertFalse(hasBackToBackRest(listOf(w(10), r(5))))
        assertTrue(hasBackToBackRest(listOf(w(10), r(5), r(5))))
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
