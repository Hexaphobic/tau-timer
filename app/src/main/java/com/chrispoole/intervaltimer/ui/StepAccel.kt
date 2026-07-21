package com.chrispoole.intervaltimer.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Escalating step size for +/- buttons: keep tapping the same way and the increment grows, so
 * dragging a duration from 30s up to 5:00 isn't sixty taps. Pausing between taps, or reversing
 * direction, drops straight back to the base step — you're aiming, not travelling.
 *
 * Deliberately not used for round counts, where the whole range is a dozen taps wide and
 * overshooting is worse than tapping.
 */
class StepAccel(private val base: Int) {
    private var dir = 0
    private var streak = 0
    private var lastAt = 0L

    /** Step size for a tap in [direction] (-1 or +1). [now] is injectable for tests. */
    fun step(direction: Int, now: Long = SystemClock.elapsedRealtime()): Int {
        streak = if (direction == dir && now - lastAt <= GAP_MS) streak + 1 else 0
        dir = direction
        lastAt = now
        return base * when {
            streak >= 9 -> 6
            streak >= 4 -> 2
            else -> 1
        }
    }

    private companion object {
        /** Longer than this between taps and you've stopped travelling. */
        const val GAP_MS = 700L
    }
}

@Composable
fun rememberStepAccel(base: Int): StepAccel = remember(base) { StepAccel(base) }
