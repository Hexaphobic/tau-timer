package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirror of ios/IntervalTimerCore/Tests/IntervalTimerCoreTests/PipsTests.swift — keep them in step. */
class PipsTest {

    @Test fun shortCountsStayOnOneLine() {
        assertEquals(listOf(1), Pips.rows(1))
        assertEquals(listOf(8), Pips.rows(8))
        assertEquals(listOf(12), Pips.rows(12))   // not 6 + 6
        assertEquals(listOf(15), Pips.rows(15))
    }

    @Test fun wrappedRowsSplitEvenlyRatherThanLeavingAStub() {
        assertEquals(listOf(8, 8), Pips.rows(16))
        assertEquals(listOf(6, 6, 5), Pips.rows(17))   // not 8 + 8 + 1
        assertEquals(listOf(7, 7, 6), Pips.rows(20))
        assertEquals(listOf(8, 8, 8), Pips.rows(24))
        assertEquals(listOf(7, 6, 6, 6), Pips.rows(25))
        assertEquals(listOf(8, 8, 8, 8), Pips.rows(32))
    }

    @Test fun pastThirtyTwoTheCallerDrawsABar() {
        assertEquals(emptyList<Int>(), Pips.rows(33))
        assertEquals(emptyList<Int>(), Pips.rows(500))
        // A round count is unbounded — a group's ×N and the overall ×N multiply — so the grid has to
        // decline politely rather than try to lay out Int.MAX_VALUE squares.
        assertEquals(emptyList<Int>(), Pips.rows(Int.MAX_VALUE))
        // Nothing produces these, but neither may throw: the timer is what draws them.
        assertEquals(emptyList<Int>(), Pips.rows(0))
        assertEquals(emptyList<Int>(), Pips.rows(-1))
    }

    /**
     * The three things the drawing code assumes and never re-checks: every round gets exactly one
     * square, no row runs past the cap once it has wrapped, and the rows are within one of each
     * other — which is what lets the cells be sized once, off the widest row, and centred.
     */
    @Test fun everyLayoutIsWholeEvenAndWithinTheCap() {
        for (n in 1..Pips.MAX) {
            val rows = Pips.rows(n)
            assertEquals("$n lost or gained a square", n, rows.sum())
            assertTrue("$n produced no rows", rows.isNotEmpty())
            assertTrue("$n is taller than the reserved $rows", rows.size <= Pips.MAX_ROWS)
            if (rows.size > 1) {
                assertTrue("$n runs past the cap: $rows", rows.max() <= Pips.PER_ROW)
                assertTrue("$n is lopsided: $rows", rows.max() - rows.min() <= 1)
            }
        }
    }
}
