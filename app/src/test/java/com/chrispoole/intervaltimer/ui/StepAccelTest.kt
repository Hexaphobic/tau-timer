package com.chrispoole.intervaltimer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StepAccelTest {
    @Test fun sustainedTapsEscalateThenCap() {
        val a = StepAccel(5)
        var t = 0L
        // First few taps stay at the base step.
        repeat(4) { assertEquals(5, a.step(1, t.also { t += 200 })) }
        // Then it doubles...
        repeat(5) { assertEquals(10, a.step(1, t.also { t += 200 })) }
        // ...and finally jumps to the big step and stays there.
        repeat(5) { assertEquals(30, a.step(1, t.also { t += 200 })) }
    }

    @Test fun pausingResets() {
        val a = StepAccel(5)
        var t = 0L
        repeat(6) { a.step(1, t.also { t += 200 }) }
        assertEquals(5, a.step(1, t + 5_000)) // thought about it — back to fine control
    }

    @Test fun reversingResets() {
        val a = StepAccel(5)
        var t = 0L
        repeat(6) { a.step(1, t.also { t += 200 }) }
        assertEquals(5, a.step(-1, t)) // overshot, correcting — don't fly back the other way
    }
}
