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
     * Drift guard: within an interval, remaining must fall by exactly the elapsed delta — no
     * accumulated rounding. Sampled in an irregular step so boundary alignment can't hide a drift,
     * and remaining must always stay inside the interval bound.
     *
     * This previously compared progressAt(t) with progressAt(t) — the same call twice — so it could
     * not fail on the property it names.
     */
    @Test fun remainingFallsExactlyWithElapsedTime() {
        val w = baseWorkout(prepareMs = 5_000, workMs = 20_000, restMs = 10_000, rounds = 2)
        val step = 137L // irregular, prime-ish step to dodge boundary alignment
        var t = 0L
        var asserted = 0
        while (t + step <= w.totalMs) {
            val a = w.progressAt(t)
            val b = w.progressAt(t + step)
            assertTrue(a.remainingMs in 0..a.intervalDurationMs)
            // Same interval => the clock moved by exactly `step`. Across a boundary the next
            // interval restarts, so remaining goes up instead — that is the reset, not drift.
            if (a.phase == b.phase && a.round == b.round && a.intervalDurationMs == b.intervalDurationMs) {
                assertEquals(a.remainingMs - step, b.remainingMs)
                asserted++
            }
            t += step
        }
        // The predecessor of this test passed while asserting nothing. Pin that the guarded
        // comparison above actually runs, so it can never silently go vacuous again.
        assertTrue("expected many same-interval samples, got $asserted", asserted > 300)
    }

    /** A jump straight to an absolute time agrees with arriving there in many small steps. */
    @Test fun pathToAnInstantDoesNotChangeTheReading() {
        val w = baseWorkout(prepareMs = 3_000, workMs = 7_000, restMs = 4_000, rounds = 3)
        var walked = 0L
        repeat(261) { walked += 97 }            // 25_317ms reached in 261 uneven hops
        val direct = w.progressAt(25_317L)
        val stepwise = w.progressAt(walked)
        assertEquals(direct.phase, stepwise.phase)
        assertEquals(direct.round, stepwise.round)
        assertEquals(direct.remainingMs, stepwise.remainingMs)
    }
}
