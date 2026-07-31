package com.chrispoole.intervaltimer.model

/** Renders the countdown in a chosen [Language]: native-digit clock, and spelled-out word mode. */
object Numbers {

    /** MM:SS with native digit glyphs + colon when the language has them, else Western. */
    fun clock(remainingMs: Long, lang: Language): String {
        if (lang.stacks) return stackedClock(remainingMs, lang)
        val ascii = formatMs(remainingMs)
        val glyphs = lang.digits ?: return ascii
        return ascii.map { c ->
            when {
                c in '0'..'9' -> glyphs[c - '0']
                c == ':' -> lang.colon
                else -> c.toString()
            }
        }.joinToString("")
    }

    /** Composed numerals per component (十, 二十五 / 십, 이십오) rather than digit glyphs (一〇). */
    private fun stackedClock(remainingMs: Long, lang: Language): String =
        clockLines(remainingMs, lang).joinToString(lang.colon)

    /** A composing language's numeral for [n]: Han for Chinese/Japanese, Sino-Korean for Korean. */
    private fun numeral(n: Int, lang: Language): String =
        if (lang.han) han(n, lang) else korean(n)

    /**
     * The clock split into display lines. Composed minute forms are wide — 一：三十 is four
     * full-width glyphs — so minutes and seconds stack, letting each line roughly double in size on
     * the same screen. The caller draws its own separator between them. Everything else is one line.
     */
    fun clockLines(remainingMs: Long, lang: Language): List<String> {
        if (!lang.stacks) return listOf(clock(remainingMs, lang))
        val total = ((remainingMs.coerceAtLeast(0L) + 999) / 1000).toInt()
        val min = total / 60
        val sec = total % 60
        return if (min == 0) listOf(numeral(sec, lang))
        else listOf(numeral(min, lang), numeral(sec, lang))
    }

    /**
     * A plain whole number in the language's own numerals — for the round counter, which used to
     * print Western digits whatever language you were in. Han languages get proper numerals
     * (十六, not 一六); scripts with their own digit glyphs get those; everyone else keeps 0-9.
     *
     * Han composition stops at 99 and falls back to digit-by-digit above it — 100+ rounds is a
     * number no workout reaches, and the hundreds forms are not worth the risk of getting the
     * 〇-filler and the 一百/百 rules subtly wrong. Korean composes further because it can do so
     * with one recursive line.
     */
    fun count(n: Int, lang: Language): String {
        if (lang.stacks) return numeral(n, lang)
        val g = lang.digits ?: return n.toString()
        return buildString { for (c in n.toString()) append(g[c - '0']) }
    }

    /**
     * The widest strings this interval will ever show, so the clock can be sized once and held
     * there instead of refitting every second.
     *
     * Minutes only ever shrink, so the interval's own minute count is the widest it gets. Seconds
     * pass through 59 on the way down whenever the interval runs a minute or more — which is the
     * real point: 三十九 is three glyphs and 四十 is two, so a size fitted to whatever is on screen
     * jumps every time the count crosses a ten.
     */
    fun widestClockLines(intervalMs: Long, lang: Language): List<String> {
        val totalSec = (intervalMs.coerceAtLeast(0L) / 1000).toInt()
        if (!lang.stacks) return listOf(clock(intervalMs, lang))
        if (totalSec < 60) return listOf(widest(totalSec, lang))
        return listOf(widest(totalSec / 60, lang), widest(59, lang))
    }

    /**
     * The widest numeral the count will actually pass through on its way down to zero.
     *
     * Not simply the starting value, which is what this used to take. Composed numerals do not
     * shrink with the number: a 30s interval opens on 三十, two glyphs, and one second later shows
     * 二十九, which is three — so a size pinned to the start ran that third glyph off both edges of
     * the screen. Every reachable value is cheap to check, and it happens once per interval rather
     * than once per second.
     *
     * Ties go to the larger number, so the seconds line still settles on 五十九 / 오십구.
     */
    private fun widest(upTo: Int, lang: Language): String {
        var widest = numeral(0, lang)
        for (i in 1..upTo) {
            val s = numeral(i, lang)
            if (s.length >= widest.length) widest = s
        }
        return widest
    }

    /** Han cardinal for 0..99 (clock components); glyphs supply 0-9, 十 is ten. */
    private fun han(n: Int, lang: Language): String {
        val g = lang.digits!!
        return when {
            n < 10 -> g[n]
            n < 20 -> "十" + if (n % 10 == 0) "" else g[n % 10]
            n < 100 -> g[n / 10] + "十" + if (n % 10 == 0) "" else g[n % 10]
            else -> buildString { for (c in n.toString()) append(g[c - '0']) }
        }
    }

    /**
     * The remaining seconds spelled out in the language, for word mode under a minute.
     * Word mode only fires under a minute, so the range is 0..60 — small enough to spell by
     * hand for the four spelling languages, which is why the app carries no ICU library.
     */
    fun words(remainingMs: Long, lang: Language): String {
        val n = ((remainingMs.coerceAtLeast(0L) + 999) / 1000).toInt().coerceIn(0, 60)
        return when (lang.code) {
            "es" -> spanish(n)
            "fr" -> french(n)
            "ru" -> russian(n)
            else -> english(n)
        }
    }

    private val enOnes = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val enTens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty")
    private fun english(n: Int): String = when {
        n < 20 -> enOnes[n]
        n % 10 == 0 -> enTens[n / 10]
        else -> "${enTens[n / 10]}-${enOnes[n % 10]}"
    }

    // 0-29 are irregular in Spanish (veintiuno…); 30+ compose as "treinta y uno".
    private val esUnder30 = arrayOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve",
        "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve",
    )
    private val esTens = arrayOf("", "", "", "treinta", "cuarenta", "cincuenta", "sesenta")
    private fun spanish(n: Int): String = when {
        n < 30 -> esUnder30[n]
        n % 10 == 0 -> esTens[n / 10]
        else -> "${esTens[n / 10]} y ${esUnder30[n % 10]}"
    }

    private val frUnder20 = arrayOf(
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf",
    )
    private val frTens = arrayOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante")
    private fun french(n: Int): String = when {
        n < 20 -> frUnder20[n]
        n % 10 == 0 -> frTens[n / 10]
        n % 10 == 1 -> "${frTens[n / 10]} et un"   // vingt et un
        else -> "${frTens[n / 10]}-${frUnder20[n % 10]}"  // vingt-deux (range caps at 60, no 70/80 forms)
    }

    private val ruUnder20 = arrayOf(
        "ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять",
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать",
    )
    private val ruTens = arrayOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят")
    private fun russian(n: Int): String = when {
        n < 20 -> ruUnder20[n]
        n % 10 == 0 -> ruTens[n / 10]
        else -> "${ruTens[n / 10]} ${ruUnder20[n % 10]}"
    }

    // Sino-Korean (used for clock time): 일 이 삼…, 십 for ten, 백 for hundred, composed without
    // spaces. The leading 일 is dropped at both scales — ten is 십, not 일십; a hundred is 백.
    private val koOnes = arrayOf("영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private fun korean(n: Int): String = when {
        n < 10 -> koOnes[n]
        n < 100 -> {
            val tens = if (n / 10 == 1) "십" else koOnes[n / 10] + "십"
            if (n % 10 == 0) tens else tens + koOnes[n % 10]
        }
        // Thousands would need 천/만 and koOnes[n / 100] indexes past the array at 1000 — an
        // ArrayIndexOutOfBounds inside composition, which takes the whole timer down and then
        // crash-loops, because a running workout is re-attached to on relaunch. Digits instead:
        // the same trade Cistercian makes at its own 9999 ceiling, and han() above 99.
        n >= 1000 -> n.toString()
        else -> {
            val hundreds = if (n / 100 == 1) "백" else koOnes[n / 100] + "백"
            if (n % 100 == 0) hundreds else hundreds + korean(n % 100)
        }
    }
}
