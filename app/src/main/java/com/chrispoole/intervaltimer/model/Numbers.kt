package com.chrispoole.intervaltimer.model

import com.ibm.icu.text.RuleBasedNumberFormat
import com.ibm.icu.util.ULocale

/** Renders the countdown in a chosen [Language]: native-digit clock, and spelled-out word mode. */
object Numbers {

    /** MM:SS with native digit glyphs + colon when the language has them, else Western. */
    fun clock(remainingMs: Long, lang: Language): String {
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

    /** A bare integer in the language's native digit glyphs (no colon). */
    fun nativeNumber(n: Int, lang: Language): String {
        val glyphs = lang.digits ?: return n.toString()
        return buildString { for (c in n.toString()) append(if (c in '0'..'9') glyphs[c - '0'] else c) }
    }

    /**
     * The number spelled out in the language, for word mode under a minute (standalone ICU4J).
     * If a non-Latin language's ruleset is missing and ICU falls back to English, we substitute
     * the native-digit form instead (fixes e.g. Bengali showing "twenty-six").
     */
    fun words(remainingMs: Long, lang: Language): String {
        val sec = ((remainingMs.coerceAtLeast(0) + 999) / 1000).toInt()
        val spelled = runCatching { formatterFor(lang.code).format(sec.toLong()) }.getOrNull()
            ?: return nativeNumber(sec, lang)
        if (!lang.latinWords && spelled.any { it in 'a'..'z' || it in 'A'..'Z' }) return nativeNumber(sec, lang)
        return spelled
    }

    private val cache = HashMap<String, RuleBasedNumberFormat>()

    private fun formatterFor(code: String): RuleBasedNumberFormat = cache.getOrPut(code) {
        RuleBasedNumberFormat(ULocale.forLanguageTag(code), RuleBasedNumberFormat.SPELLOUT)
    }
}
