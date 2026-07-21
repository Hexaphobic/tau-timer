package com.chrispoole.intervaltimer.model

/** Renders the countdown in a chosen [Language]: native-digit clock, and spelled-out word mode. */
object Numbers {

    /** MM:SS with native digit glyphs + colon when the language has them, else Western. */
    fun clock(remainingMs: Long, lang: Language): String {
        if (lang.han) return hanClock(remainingMs, lang)
        val ascii = formatMs(remainingMs)
        val glyphs = lang.digits ?: return ascii
        val sb = StringBuilder(ascii.length)
        for (c in ascii) {
            when {
                c in '0'..'9' -> sb.append(glyphs[c - '0'])
                c == ':' -> sb.append(lang.colon)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Chinese/Japanese clock: proper Han numerals (十, 二十五) per component, not digit glyphs (一〇). */
    private fun hanClock(remainingMs: Long, lang: Language): String =
        clockLines(remainingMs, lang).joinToString(lang.colon)

    /**
     * The clock split into display lines. Han minute forms are wide — 一：三十 is four full-width
     * glyphs — so minutes and seconds stack, letting each line roughly double in size on the same
     * screen. The caller draws its own separator between them. Everything else is one line.
     */
    fun clockLines(remainingMs: Long, lang: Language): List<String> {
        if (!lang.han) return listOf(clock(remainingMs, lang))
        val total = ((remainingMs.coerceAtLeast(0L) + 999) / 1000).toInt()
        val min = total / 60
        val sec = total % 60
        return if (min == 0) listOf(han(sec, lang)) else listOf(han(min, lang), han(sec, lang))
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
     * hand for the five non-glyph languages, which is why the app carries no ICU library.
     */
    fun words(remainingMs: Long, lang: Language): String {
        val n = ((remainingMs.coerceAtLeast(0L) + 999) / 1000).toInt().coerceIn(0, 60)
        return when (lang.code) {
            "es" -> spanish(n)
            "fr" -> french(n)
            "ru" -> russian(n)
            "ko" -> korean(n)
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

    // Sino-Korean (used for clock time): 일 이 삼…, 십 for ten, composed without spaces.
    private val koOnes = arrayOf("영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private fun korean(n: Int): String = when {
        n < 10 -> koOnes[n]
        else -> {
            val tens = if (n / 10 == 1) "십" else koOnes[n / 10] + "십"
            if (n % 10 == 0) tens else tens + koOnes[n % 10]
        }
    }
}
