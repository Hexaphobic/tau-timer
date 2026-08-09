package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Section names: stamped flat by [flatten], recovered by [groupIntervals], worn by the timer. */
class SectionNameTest {
    private fun w(s: Int, l: String = "") = SeqInterval(Phase.WORK, s, l)
    private fun r(s: Int, l: String = "") = SeqInterval(Phase.REST, s, l)

    private val home = listOf(
        Block(listOf(w(30), r(15)), 2, "Splits"),
        Block(listOf(w(45), r(15)), 1, "Quads"),
        Block(listOf(w(30), r(15)), 2, "Pistols"),
    )

    @Test fun flattenStampsTheBlockNameOnEveryInterval() {
        assertEquals(
            listOf(
                w(30, "Splits"), r(15, "Splits"), w(30, "Splits"), r(15, "Splits"),
                w(45, "Quads"), r(15, "Quads"),
                w(30, "Pistols"), r(15, "Pistols"), w(30, "Pistols"), r(15, "Pistols"),
            ),
            flatten(home),
        )
    }

    /** Same shape, different names — the label sits in equality, so the sections stay separate. */
    @Test fun groupingRoundTripsNamesAndKeepsNamedTwinsApart() {
        assertEquals(home, groupIntervals(flatten(home)))
    }

    @Test fun workWearsItsOwnNameAndRestWearsTheOneAhead() {
        val ivs = Preset("", flatten(home)).toWorkout(prepareMs = 5_000).intervals
        assertEquals(
            listOf("Splits", "Splits", "Quads", "Pistols", "Pistols"),
            ivs.filter { it.phase == Phase.WORK }.map { it.label },
        )
        // Four rests play — the trailing one is dropped. Each points at the work AHEAD of it, so
        // the one closing Splits' last pass already says Quads. The lead-in points at the first.
        assertEquals("Splits", ivs.first { it.phase == Phase.PREPARE }.label)
        assertEquals(
            listOf("Splits", "Quads", "Pistols", "Pistols"),
            ivs.filter { it.phase == Phase.REST }.map { it.label },
        )
    }

    /**
     * The presets list reads a saved preset back through [groupIntervals] — the same call the editor
     * makes — so a home saved and reopened must come back as its named sections, not as the flat
     * work/rest/work/rest the ×N expands to.
     */
    @Test fun aSavedHomeReopensAsItsNamedSections() {
        val reopened = groupIntervals(homePreset(home).intervals)
        assertEquals(listOf("Splits", "Quads", "Pistols"), reopened.map { it.name })
        assertEquals(listOf(2, 1, 2), reopened.map { it.repeat })
    }

    /** No work left ahead — nothing to point at, so a trailing rest stays blank rather than
     *  reaching backwards for the set that just ended. */
    @Test fun aRestWithNoWorkAheadStaysBlank() {
        val trailing = listOf(Block(listOf(w(30), r(15)), 1, "Splits"), Block(listOf(r(60)), 1, "Cooldown"))
        val ivs = Preset("", flatten(trailing)).toWorkout(prepareMs = 0).intervals
        assertEquals("", ivs.last().label)
    }
}
