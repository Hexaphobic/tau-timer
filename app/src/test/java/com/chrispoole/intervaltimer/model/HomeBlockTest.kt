package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A home section is now the same [Block] the editor has always used: a list of intervals with one
 * multiplier meaning "run this whole section that many times". Mirror of
 * ios/IntervalTimerCore/Tests/IntervalTimerCoreTests/HomeBlockTests.swift — keep them in step.
 */
class HomeBlockTest {

    private fun work(s: Int) = SeqInterval(Phase.WORK, s)
    private fun rest(s: Int) = SeqInterval(Phase.REST, s)

    @Test fun theMultiplierRepeatsTheWholeSectionNotEachInterval() {
        // Work 30 / Work 20 / Rest 10, twice over — the user's "work, work, rest" shape.
        val b = Block(listOf(work(30), work(20), rest(10)), 2)
        assertEquals(
            listOf(work(30), work(20), rest(10), work(30), work(20), rest(10)),
            homePreset(listOf(b)).intervals,
        )
    }

    @Test fun sectionsPlayInOrder() {
        val a = Block(listOf(work(30), rest(15)), 2)
        val b = Block(listOf(work(60)), 1)
        assertEquals(
            listOf(work(30), rest(15), work(30), rest(15), work(60)),
            homePreset(listOf(a, b)).intervals,
        )
    }

    /**
     * Dialling rest down to nothing has always been how you say "no rest on this one", and it has to
     * keep working — but the interval stays in the section so the number is still there to dial back
     * up. So the drop happens at playback, not in the UI.
     */
    @Test fun zeroLengthIntervalsAreDroppedAtPlaybackNotDeleted() {
        val b = Block(listOf(work(30), rest(0)), 3)
        assertEquals(listOf(work(30), work(30), work(30)), homePreset(listOf(b)).intervals)
        assertEquals(2, b.items.size)
    }

    @Test fun aSectionOfNothingButZeroesContributesNothing() {
        val b = Block(listOf(work(0), rest(0)), 4)
        assertTrue(homePreset(listOf(b)).intervals.isEmpty())
    }

    /**
     * Only the classic shape takes the [baseWorkout] road; anything with a shape of its own runs as
     * a sequence. Both count work sets, so the timer's "3 / 8" reads the same either way.
     */
    @Test fun onlyOneWorkAndAnOptionalRestCountsAsBasic() {
        assertTrue(Block(listOf(work(30), rest(15)), 8).isBasic)
        assertTrue(Block(listOf(work(30)), 8).isBasic)
        assertFalse(Block(listOf(work(30), work(20), rest(10)), 2).isBasic)
        assertFalse(Block(listOf(work(30), rest(15), rest(5)), 2).isBasic)
        assertFalse(Block(listOf(rest(15), work(30)), 2).isBasic)   // rest first is not the classic home
        assertFalse(Block(emptyList(), 2).isBasic)
    }

    @Test fun aFreshHomeIsBasicAndKeepsItsRestEvenAtZero() {
        val b = basicBlock(DEFAULT_WORK_SEC_FOR_TEST, 0, 5)
        assertTrue(b.isBasic)
        // The rest row survives so the plain home still shows it; playback is what drops it.
        assertEquals(2, b.items.size)
        assertEquals(List(5) { work(DEFAULT_WORK_SEC_FOR_TEST) }, homePreset(listOf(b)).intervals)
    }

    /**
     * A name is a label on the block, not part of what it plays. Worth pinning: the home writes
     * itself to prefs off block equality, so the name has to count as a change there — and it must
     * count for nothing at all by the time the workout is built.
     */
    @Test fun namingASectionChangesWhatItIsCalledAndNothingElse() {
        val plain = Block(listOf(work(30), rest(15)), 3)
        val named = plain.copy(name = "Warm-up")
        assertEquals(homePreset(listOf(plain)).intervals, homePreset(listOf(named)).intervals)
        assertEquals(homeSets(listOf(plain), 1), homeSets(listOf(named), 1))
        assertEquals(homeSeconds(listOf(plain), 1), homeSeconds(listOf(named), 1))
        assertTrue(named.isBasic)
        // Different blocks, so an edit that only renames one still reaches the save path.
        assertFalse(plain == named)
    }

    /** Nothing that reads a flat sequence back into blocks can invent a name for one. */
    @Test fun regroupingAFlatSequenceLeavesTheBlocksUnnamed() {
        assertTrue(groupIntervals(listOf(work(30), rest(15), work(30), rest(15))).all { it.name.isEmpty() })
    }

    private companion object {
        const val DEFAULT_WORK_SEC_FOR_TEST = 30
    }
}
