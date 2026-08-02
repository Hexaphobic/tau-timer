package com.chrispoole.intervaltimer.model

/**
 * The 12 display languages (Chinese and Japanese share a tile, so the picker shows 11). [digits] maps 0-9 to native glyphs (null = Western 0-9); [colon] is the
 * clock separator. [work]/[rest]/[ready] are translated phase labels. Values are the
 * research-verified glyphs/labels; system fonts (Noto) cover every script here.
 */
enum class Language(
    val code: String,
    val english: String,
    val digits: List<String>?,
    val colon: String,
    val work: String,
    val rest: String,
    val ready: String,
) {
    // Declaration order IS the order of the picker. Hand-set, not alphabetical, and not by speaker
    // count: it is the owner's running order. Chinese and Japanese sit together at the front because
    // they share one tile (see [han]) — the pair takes a single slot wherever it lands.
    EN("en", "English", null, ":", "Work", "Rest", "Get ready"),
    JA("ja", "日本語 · Japanese", listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"), "：", "運動", "休憩", "準備"),
    ZH("zh", "中文 · Chinese", listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"), "：", "运动", "休息", "准备"),
    ES("es", "Español · Spanish", null, ":", "Trabajo", "Descanso", "Prepárate"),
    KO("ko", "한국어 · Korean", null, ":", "운동", "휴식", "준비"),

    // Not a language — a 13th-century monastic cipher with no words of its own, so it borrows
    // English labels. Its glyphs are drawn, not typed (see ui/Cistercian.kt), which is why digits
    // is null and every render site branches on [cistercian] before reaching for a font. Fifth by
    // request, rather than parked at the end where a curiosity goes to be missed.
    CI("ci", "Cistercian", null, ":", "Work", "Rest", "Get ready"),

    RU("ru", "Русский · Russian", null, ":", "Работа", "Отдых", "Приготовься"),
    HI("hi", "हिन्दी · Hindi", listOf("०", "१", "२", "३", "४", "५", "६", "७", "८", "९"), ":", "काम", "आराम", "तैयार"),
    AR("ar", "العربية · Arabic", listOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"), ":", "تمرين", "راحة", "استعد"),
    FR("fr", "Français · French", null, ":", "Effort", "Repos", "Prêt"),
    BN("bn", "বাংলা · Bengali", listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"), ":", "কাজ", "বিশ্রাম", "প্রস্তুত"),
    TH("th", "ไทย · Thai", listOf("๐", "๑", "๒", "๓", "๔", "๕", "๖", "๗", "๘", "๙"), ":", "ทำงาน", "พัก", "เตรียม");

    /** Chinese/Japanese compose numbers with 十 (十, 二十五…) rather than digit-by-digit (一〇). */
    val han: Boolean get() = this == ZH || this == JA

    /** Drawn on a Canvas rather than set in a font — no text path applies, word mode included. */
    val cistercian: Boolean get() = this == CI

    /**
     * Composes numerals of its own, so the clock stacks minutes over seconds instead of spelling
     * a running total. Sino-Korean belongs here with Chinese and Japanese: 1:30 reads 일 / 삼십,
     * two short lines, where a spelled total (구십) grows with every extra digit.
     */
    val stacks: Boolean get() = han || this == KO

    /**
     * CJK glyphs come from the system's CJK fallback, which ships Regular only — no bold face
     * exists on device. Asking for bold makes Android inflate the outline instead, which spikes
     * at acute stroke joins (visible as a notch in 九). These render at normal weight.
     */
    val cjk: Boolean get() = han || this == KO

    companion object {
        fun of(code: String): Language = entries.firstOrNull { it.code == code } ?: EN
    }
}
