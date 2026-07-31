package com.chrispoole.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    }

    // Korean composes numerals of its own, so it takes the stacked clock with Chinese and Japanese
    // rather than the speller — 1:30 is two short lines, not one growing word.
    @Test fun sinoKoreanComposesAndStacks() {
        assertEquals("십", Numbers.clock(10_000, Language.KO))        // 10, not 일십
        assertEquals("이십일", Numbers.clock(21_000, Language.KO))
        assertEquals("오십구", Numbers.clock(59_000, Language.KO))
        assertEquals(listOf("일", "삼십"), Numbers.clockLines(90_000, Language.KO))
        assertEquals(listOf("십", "영"), Numbers.clockLines(600_000, Language.KO))  // 10:00
        // Past ninety-nine the hundreds form kicks in, so a long interval's minute line holds up.
        assertEquals("백오십", Numbers.count(150, Language.KO))
        assertEquals("구백구십구", Numbers.count(999, Language.KO))
        // One size for the interval: seconds always pass through 59 on the way down.
        assertEquals(listOf("일", "오십구"), Numbers.widestClockLines(90_000, Language.KO))
    }

    /**
     * The clock is sized once per interval against the widest value it will ever show. Composed
     * numerals don't shrink with the number, so "the value it starts at" is not that: a 30s interval
     * opens on 三十 and immediately passes through 二十九, a glyph wider — which ran off the screen.
     */
    @Test fun widestLineCoversEveryValueTheCountPassesThrough() {
        for (lang in Language.entries.filter { it.stacks }) {
            for (intervalSec in listOf(5, 30, 59, 60, 90, 125, 600)) {
                val widest = Numbers.widestClockLines(intervalSec * 1000L, lang)
                val budget = widest.maxOf { it.length }
                for (remaining in 0..intervalSec) {
                    val lines = Numbers.clockLines(remaining * 1000L, lang)
                    assertTrue(
                        "$lang ${intervalSec}s interval: $lines is wider than the fitted $widest",
                        lines.maxOf { it.length } <= budget,
                    )
                    // A long interval drops from two lines to one as it passes under a minute.
                    // Fewer lines is fine — the fitted size still holds; more would overflow.
                    assertTrue(
                        "$lang ${intervalSec}s: $lines stacks taller than $widest",
                        lines.size <= widest.size,
                    )
                }
            }
        }
    }

    /**
     * A round counter is unbounded — nothing clamps `rounds`, a group's ×N or the overall ×N, and
     * they multiply. At 1000 the Korean composer used to index its 10-entry digit array with
     * `n / 100`, throwing inside composition: the timer died, and because a running workout is
     * re-attached to on launch, it died again on every relaunch. Every composing script must
     * return *something* for any Int a workout can produce.
     */
    @Test fun everyComposingScriptSurvivesAnUnboundedRoundCount() {
        for (lang in Language.entries) {
            for (n in listOf(0, 1, 99, 100, 999, 1_000, 1_001, 9_999, 100_000, Int.MAX_VALUE)) {
                assertTrue("$lang count($n) came out blank", Numbers.count(n, lang).isNotEmpty())
            }
        }
    }
}
