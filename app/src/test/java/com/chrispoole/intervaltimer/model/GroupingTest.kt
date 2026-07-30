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

    @Test fun nonRepeatingLadderStaysFlat() {
        val flat = listOf(w(20), r(20), w(30), r(20), w(40))
        assertEquals(flat, flatten(groupIntervals(flat)))
        assertEquals(5, groupIntervals(flat).size)
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
        val p = homePreset(listOf(HomeBlock(30, 15, 2), HomeBlock(60, 45, 2)))
        assertEquals(listOf(w(30), r(15), w(30), r(15), w(60), r(45), w(60), r(45)), p.intervals)
        assertEquals(listOf(w(30), r(15), w(30), r(15), w(60), r(45), w(60)), p.playbackIntervals())
        // No rest dialled in → none inserted between rounds.
        assertEquals(listOf(w(30), w(30), w(60)), homePreset(listOf(HomeBlock(30, 0, 2), HomeBlock(60, 0, 1))).intervals)
    }
}
