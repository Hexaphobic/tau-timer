package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupingTest {
    private fun w(s: Int) = SeqInterval(Phase.WORK, s)
    private fun r(s: Int) = SeqInterval(Phase.REST, s)

    @Test fun tabataGroupsToOnePairBlock() {
        val flat = (1..8).flatMap { listOf(w(20), r(10)) }
        val blocks = groupIntervals(flat)
        assertEquals(listOf(Block(listOf(w(20), r(10)), 8)), blocks)
        assertEquals(flat, flatten(blocks))
    }

    @Test fun emomGroupsToOneSingleBlock() {
        assertEquals(listOf(Block(listOf(w(60)), 10)), groupIntervals((1..10).map { w(60) }))
    }

    /** Nothing repeats, so every group is ×1 — but a work still keeps the rest that follows it. */
    @Test fun nonRepeatingLadderPairsEachWorkWithItsRest() {
        val flat = listOf(w(20), r(20), w(30), r(20), w(40))
        val blocks = groupIntervals(flat)
        assertEquals(
            listOf(
                Block(listOf(w(20), r(20)), 1),
                Block(listOf(w(30), r(20)), 1),
                Block(listOf(w(40)), 1),
            ),
            blocks,
        )
        assertEquals(flat, flatten(blocks))
    }

    /** The pairing must not swallow the first interval of a pattern that starts right after it. */
    @Test fun pairingLeavesALaterRepeatIntact() {
        val flat = listOf(w(20), r(20)) + (1..3).flatMap { listOf(w(30), r(10)) }
        assertEquals(
            listOf(Block(listOf(w(20), r(20)), 1), Block(listOf(w(30), r(10)), 3)),
            groupIntervals(flat),
        )
        assertEquals(flat, flatten(groupIntervals(flat)))
    }

    @Test fun mixedSequenceGroupsGreedily() {
        // (30,15)×3 then a lone 60s work.
        val flat = (1..3).flatMap { listOf(w(30), r(15)) } + w(60)
        val blocks = groupIntervals(flat)
        assertEquals(listOf(Block(listOf(w(30), r(15)), 3), Block(listOf(w(60)), 1)), blocks)
        assertEquals(flat, flatten(blocks))
    }

    @Test fun toWorkoutDropsTrailingRest() {
        val p = Preset("t", listOf(w(30), r(15), w(30), r(15)))
        val phases = p.toWorkout(prepareMs = 0).intervals.map { it.phase }
        assertEquals(listOf(Phase.WORK, Phase.REST, Phase.WORK), phases)
    }

    @Test fun homePresetPlaysBlocksInOrder() {
        // The rest closing block 1 is mid-sequence, so it must survive; only the rest at the very
        // end of everything is dropped, and that at run time, not here.
        val p = homePreset(listOf(basicBlock(30, 15, 2), basicBlock(60, 45, 2)))
        assertEquals(listOf(w(30), r(15), w(30), r(15), w(60), r(45), w(60), r(45)), p.intervals)
        assertEquals(listOf(w(30), r(15), w(30), r(15), w(60), r(45), w(60)), p.playbackIntervals())
        // No rest dialled in → none inserted between rounds.
        assertEquals(listOf(w(30), w(30), w(60)), homePreset(listOf(basicBlock(30, 0, 2), basicBlock(60, 0, 1))).intervals)
    }
}
