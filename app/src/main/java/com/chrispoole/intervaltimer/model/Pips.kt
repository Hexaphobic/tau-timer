package com.chrispoole.intervaltimer.model

/**
 * How the timer's round pips wrap — one square per round, and the same rule on both platforms.
 *
 * Squares whatever the count: the old layout drew wide pills up to eight and squares above that, so
 * adding a ninth round changed the shape of the thing rather than just adding to it.
 */
object Pips {

    /** Widest a wrapped row gets. */
    const val PER_ROW = 8

    /** Up to this many stay on one line, even though it runs past [PER_ROW]. */
    const val SINGLE_ROW_MAX = 15

    /** Past this even a grid is a wall of dots, and the caller draws a plain bar instead. */
    const val MAX = 32

    /** Tallest the grid gets, which is what the timer screen reserves room for. */
    const val MAX_ROWS = (MAX + PER_ROW - 1) / PER_ROW

    /**
     * Pips per row, or empty when [total] is past [MAX] and should be drawn as a bar.
     *
     * Two rules, and the second is the one that isn't obvious. Rows never fill to the cap and leave a
     * stub — eleven is 6 and 5, not 10 and 1 — because a short last row reads as a count that ran out
     * of room rather than as a shape. And a count that only just exceeds the cap stays on one line
     * anyway: twelve in a row is still countable at a glance, whereas splitting it wastes a whole
     * second line on six.
     */
    fun rows(total: Int): List<Int> {
        if (total !in 1..MAX) return emptyList()
        if (total <= SINGLE_ROW_MAX) return listOf(total)
        val rows = (total + PER_ROW - 1) / PER_ROW
        return List(rows) { total / rows + if (it < total % rows) 1 else 0 }
    }
}
