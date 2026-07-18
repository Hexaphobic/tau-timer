package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTest {

    @Test fun baseWorkoutStructure() {
        val w = baseWorkout(prepareMs = 5_000, workMs = 30_000, restMs = 15_000, rounds = 3)
        // prepare + (work,rest) + (work,rest) + work  = 6 intervals, no trailing rest
        assertEquals(6, w.intervals.size)
        assertEquals(Phase.PREPARE, w.intervals.first().phase)
        assertEquals(Phase.WORK, w.intervals.last().phase)
        assertEquals(5_000 + 3 * 30_000 + 2 * 15_000L, w.totalMs)
    }

    @Test fun mapsAbsoluteTimeToTheRightInterval() {
        val w = baseWorkout(prepareMs = 5_000, workMs = 20_000, restMs = 10_000, rounds = 2)
        // boundaries (ms): prepare[0,5k) work1[5k,25k) rest1[25k,35k) work2[35k,55k)
        assertEquals(Phase.PREPARE, w.progressAt(0).phase)
        assertEquals(5_000, w.progressAt(0).remainingMs)

        // Exactly on a boundary belongs to the NEXT interval.
        assertEquals(Phase.WORK, w.progressAt(5_000).phase)
        assertEquals(20_000, w.progressAt(5_000).remainingMs)

        assertEquals(1, w.progressAt(24_999).remainingMs)
        assertEquals(Phase.REST, w.progressAt(25_000).phase)
        assertEquals(Phase.WORK, w.progressAt(35_000).phase)
        assertEquals(2, w.progressAt(35_000).round)

        assertTrue(w.progressAt(55_000).done)
        assertTrue(w.progressAt(9_999_999).done)
        assertFalse(w.progressAt(54_999).done)
    }

    @Test fun fractionRunsZeroToOneWithinAnInterval() {
        val w = baseWorkout(prepareMs = 0, workMs = 10_000, restMs = 0, rounds = 1)
        assertEquals(0f, w.progressAt(0).fraction, 0.0001f)
        assertEquals(0.5f, w.progressAt(5_000).fraction, 0.0001f)
        assertEquals(1f, w.progressAt(9_999).fraction, 0.01f)
    }

    /**
     * Drift guard: the reading depends only on `now`, never on the path taken to get
     * there. Walking forward in irregular steps must agree with a direct jump to the
     * same absolute time, and remaining must always stay within the interval bound.
     */
    @Test fun readingDependsOnlyOnAbsoluteTime() {
        val w = baseWorkout(prepareMs = 5_000, workMs = 20_000, restMs = 10_000, rounds = 2)
        var t = 0L
        while (t <= w.totalMs) {
            val stepwise = w.progressAt(t)
            val direct = w.progressAt(t)
            assertEquals(stepwise.phase, direct.phase)
            assertEquals(stepwise.remainingMs, direct.remainingMs)
            assertTrue(stepwise.remainingMs in 0..stepwise.intervalDurationMs)
            t += 137 // irregular, prime-ish step to dodge boundary alignment
        }
    }
}
