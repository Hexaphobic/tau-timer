package com.chrispoole.intervaltimer.service

import com.chrispoole.intervaltimer.model.Interval
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildCuesTest {

    /**
     * 5s is both the default prepare and the editor's work floor, so `end - 5_000` lands on the
     * previous interval's boundary. Two cues on one instant come due in a single fireDueCues pass:
     * the warn tone plays over the transition whoosh, outside the duck window GO just tore down.
     */
    @Test fun cueInstantsAreUnique() {
        val cues = buildCues(
            Workout(listOf(Interval(Phase.PREPARE, 5_000), Interval(Phase.WORK, 5_000, 1)))
        )
        assertEquals(cues.size, cues.map { it.atMs }.distinct().size)

        // …and the guard drops only the colliding warns: an interval long enough to hold its own
        // 5s warning still gets one, 5s into itself.
        val tenSec = buildCues(Workout(listOf(Interval(Phase.WORK, 10_000, 1))))
        assertEquals(listOf(5_000L), tenSec.filter { it.cue == Cue.WARN }.map { it.atMs })
    }
}
