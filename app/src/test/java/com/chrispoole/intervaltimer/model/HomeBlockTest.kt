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
     * Only the classic shape keeps the timer's "3 / 8" round counter; anything with a shape of its
     * own counts interval positions instead, because there is no single round to count.
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

    private companion object {
        const val DEFAULT_WORK_SEC_FOR_TEST = 30
    }
}
