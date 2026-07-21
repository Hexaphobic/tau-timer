package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NumbersTest {
    private fun zh(ms: Long) = Numbers.clock(ms, Language.ZH)

    @Test fun hanNumeralsCompose() {
        assertEquals("五", zh(5_000))        // 5
        assertEquals("十", zh(10_000))       // 10 (not 一〇)
        assertEquals("十五", zh(15_000))      // 15
        assertEquals("二十五", zh(25_000))     // 25
        assertEquals("三十", zh(30_000))      // 30 (not 三〇)
        assertEquals("五十九", zh(59_000))     // 59
        assertEquals("一：〇", zh(60_000))     // 1:00
        assertEquals("一：三十", zh(90_000))    // 1:30
    }

    @Test fun hanMinutesStackButSecondsStaySingleLine() {
        assertEquals(listOf("三十"), Numbers.clockLines(30_000, Language.ZH))
        assertEquals(listOf("一", "三十"), Numbers.clockLines(90_000, Language.ZH))
        // The flat clock string (cover screen, tests) still carries the colon.
        assertEquals("一：三十", zh(90_000))
        // Non-Han languages never stack.
        assertEquals(listOf("1:20"), Numbers.clockLines(80_000, Language.EN))
    }

    @Test fun westernDigitLanguagesStillDropLeadingZeros() {
        assertEquals("16", Numbers.clock(16_000, Language.EN))
        assertEquals("1:20", Numbers.clock(80_000, Language.EN))
        assertEquals("१६", Numbers.clock(16_000, Language.HI)) // Hindi glyphs, digit-by-digit is correct here
    }

    @Test fun durationsReadAsMinutesPastSixty() {
        assertEquals("45s", secLabel(45))
        assertEquals("59s", secLabel(59))
        assertEquals("1:00", secLabel(60))
        assertEquals("1:30", secLabel(90))   // not "90s"
        assertEquals("2:05", secLabel(125))
    }

    // Word mode fires under a minute, so the speller only needs 0..60. Spot-check the tricky joins.
    private fun w(sec: Int, lang: Language) = Numbers.words(sec * 1000L, lang)

    @Test fun spellsNumbersWithoutIcu() {
        assertEquals("zero", w(0, Language.EN))
        assertEquals("twenty-one", w(21, Language.EN))
        assertEquals("forty", w(40, Language.EN))
        assertEquals("sixty", w(60, Language.EN))

        assertEquals("veintiuno", w(21, Language.ES))   // one word in Spanish
        assertEquals("treinta y uno", w(31, Language.ES))
        assertEquals("sesenta", w(60, Language.ES))

        assertEquals("vingt et un", w(21, Language.FR))  // "et un", not "-un"
        assertEquals("vingt-deux", w(22, Language.FR))
        assertEquals("soixante", w(60, Language.FR))

        assertEquals("двадцать один", w(21, Language.RU))
        assertEquals("сорок", w(40, Language.RU))

        assertEquals("십", w(10, Language.KO))
        assertEquals("이십일", w(21, Language.KO))
    }
}
