package com.chrispoole.intervaltimer

import com.chrispoole.intervaltimer.ui.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two gate rules. Not the BillingClient — that needs a Play Store — but these are what decides
 * whether a tap paywalls, and getting either backwards would either give the app away or lock a
 * paying customer out of what they bought.
 */
class BillingGateTest {

    @Test fun `default and mono are free`() {
        assertFalse(paletteLocked(Palette.DEFAULT, unlocked = false))
        assertFalse(paletteLocked(Palette.MONO, unlocked = false))
    }

    @Test fun `the other six are locked until you own it`() {
        val paid = Palette.entries.filter { it !in FREE_PALETTES }
        assertEquals(6, paid.size)
        paid.forEach { assertTrue("$it should be locked", paletteLocked(it, unlocked = false)) }
        paid.forEach { assertFalse("$it should be free once unlocked", paletteLocked(it, unlocked = true)) }
    }

    @Test fun `three saved presets are free, the fourth is not`() {
        assertFalse(presetsFull(0, unlocked = false))
        assertFalse(presetsFull(2, unlocked = false))
        assertTrue(presetsFull(3, unlocked = false))
        // A grandfathered install can be over the cap already; that must never lock them out of
        // their own library.
        assertFalse(presetsFull(40, unlocked = true))
    }
}
