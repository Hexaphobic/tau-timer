package com.chrispoole.intervaltimer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CistercianTest {
    @Test fun `zero is just the stave`() {
        assertEquals(listOf(Stroke(0f, -1f, 0f, 1f)), cistercian(0))
    }

    @Test fun `one is a top-right bar`() {
        assertEquals(Stroke(0f, -1f, 1f, -1f), cistercian(1)[1])
    }

    @Test fun `ten mirrors one to the left`() {
        assertEquals(Stroke(0f, -1f, -1f, -1f), cistercian(10)[1])
    }

    @Test fun `hundred flips one to the bottom`() {
        assertEquals(Stroke(0f, 1f, 1f, 1f), cistercian(100)[1])
    }

    @Test fun `each quadrant reads its own digit`() {
        // 1234: units 4, tens 3, hundreds 2, thousands 1 — one stroke each, plus the stave.
        assertEquals(5, cistercian(1234).size)
        assertEquals(12 + 1, cistercian(9999).size) // 9 is three strokes per quadrant
    }

    @Test fun `seconds round up and stay in range`() {
        assertEquals(30, cistercianSeconds(30_000))      // a fresh interval reads its full duration
        assertEquals(1, cistercianSeconds(1))            // and only hits 0 at true zero
        assertEquals(0, cistercianSeconds(0))
        assertEquals(0, cistercianSeconds(-5_000))
        cistercian(cistercianSeconds(9_999_000_000L))    // clamped, not thrown
    }

    @Test fun `all strokes stay inside the unit box`() {
        for (n in 0..9999) {
            assertTrue(cistercian(n).all { it.x1 in -1f..1f && it.y1 in -1f..1f && it.x2 in -1f..1f && it.y2 in -1f..1f })
        }
    }
}
