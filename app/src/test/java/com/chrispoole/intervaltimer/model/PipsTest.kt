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

    /** A pass is a row: four sets run twice reads as two rows of four, not one row of eight. */
    @Test fun aPassGetsItsOwnRow() {
        assertEquals(listOf(4, 4), Pips.rows(8, 4))
        assertEquals(listOf(3, 3, 3), Pips.rows(9, 3))
        assertEquals(listOf(2, 2, 2, 2), Pips.rows(8, 2))
        // Past PER_ROW is fine here — the row is the shape, and the cells size to whatever it is.
        assertEquals(listOf(12, 12), Pips.rows(24, 12))
    }

    /** A shape that wouldn't read as one is no better than the wrap, so it falls back to it. */
    @Test fun anUndrawableShapeFallsBackToTheWrap() {
        assertEquals(listOf(8), Pips.rows(8, 8))          // one pass: nothing to show
        assertEquals(listOf(8), Pips.rows(8, 0))          // no shape at all
        assertEquals(listOf(2), Pips.rows(2, 1))          // a column of one-pip rows is not a shape
        assertEquals(listOf(6, 6, 5), Pips.rows(17, 4))   // 17 is not four of anything
        assertEquals(listOf(8, 8, 8), Pips.rows(24, 2))   // 12 rows is taller than the screen keeps
        assertEquals(listOf(8, 8, 8, 8), Pips.rows(32, 16)) // a row of 16 is a wall, not a shape
        assertEquals(emptyList<Int>(), Pips.rows(33, 11))   // still a bar past the ceiling
    }

    /**
     * The things the drawing code assumes and never re-checks, now across every shape it can be
     * handed: every round gets exactly one square, the grid is no taller than the screen reserves,
     * and no row runs past what one line can hold — which is what lets the cells be sized once, off
     * the widest row, and centred.
     */
    @Test fun everyLayoutIsWholeAndDrawable() {
        for (n in 1..Pips.MAX) {
            for (perRow in 0..n) {
                val rows = Pips.rows(n, perRow)
                val where = "$n in rows of $perRow"
                assertEquals("$where lost or gained a square", n, rows.sum())
                assertTrue("$where produced no rows", rows.isNotEmpty())
                assertTrue("$where is taller than the reserved $rows", rows.size <= Pips.MAX_ROWS)
                assertTrue("$where has an empty row: $rows", rows.all { it > 0 })
                assertTrue("$where runs past one line: $rows", rows.max() <= Pips.SINGLE_ROW_MAX)
            }
        }
    }

    /** Wrapped rows — no shape given — stay within one of each other and inside the eight cap. */
    @Test fun theWrapIsEvenAndWithinTheCap() {
        for (n in 1..Pips.MAX) {
            val rows = Pips.rows(n)
            if (rows.size > 1) {
                assertTrue("$n runs past the cap: $rows", rows.max() <= Pips.PER_ROW)
                assertTrue("$n is lopsided: $rows", rows.max() - rows.min() <= 1)
            }
        }
    }
}
