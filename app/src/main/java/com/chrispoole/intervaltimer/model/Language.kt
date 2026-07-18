package com.chrispoole.intervaltimer.model

/**
 * The 13 display languages. [digits] maps 0-9 to native glyphs (null = Western 0-9); [colon] is the
 * clock separator. [work]/[rest]/[ready] are translated phase labels. [latinWords] is true when the
 * language's spelled-out numbers use the Latin alphabet (so an ICU result with Latin letters is
 * legitimate, not an English fallback). Values are the research-verified glyphs/labels; system
 * fonts (Noto) cover every script here.
 */
enum class Language(
    val code: String,
    val english: String,
    val digits: List<String>?,
    val colon: String,
    val work: String,
    val rest: String,
    val ready: String,
    val latinWords: Boolean = false,
) {
    EN("en", "English", null, ":", "Work", "Rest", "Get ready", latinWords = true),
    ZH("zh", "中文 · Chinese", listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"), "：", "运动", "休息", "准备"),
    JA("ja", "日本語 · Japanese", listOf("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"), "：", "運動", "休憩", "準備"),
    KO("ko", "한국어 · Korean", null, ":", "운동", "휴식", "준비"),
    RU("ru", "Русский · Russian", null, ":", "Работа", "Отдых", "Приготовься"),
    HI("hi", "हिन्दी · Hindi", listOf("०", "१", "२", "३", "४", "५", "६", "७", "८", "९"), ":", "काम", "आराम", "तैयार"),
    AR("ar", "العربية · Arabic", listOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"), ":", "تمرين", "راحة", "استعد"),
    ES("es", "Español · Spanish", null, ":", "Trabajo", "Descanso", "Prepárate", latinWords = true),
    FR("fr", "Français · French", null, ":", "Effort", "Repos", "Prêt", latinWords = true),
    BN("bn", "বাংলা · Bengali", listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"), ":", "কাজ", "বিশ্রাম", "প্রস্তুত"),
    TH("th", "ไทย · Thai", listOf("๐", "๑", "๒", "๓", "๔", "๕", "๖", "๗", "๘", "๙"), ":", "ทำงาน", "พัก", "เตรียม"),
    BO("bo", "བོད་ཡིག · Tibetan", listOf("༠", "༡", "༢", "༣", "༤", "༥", "༦", "༧", "༨", "༩"), ":", "ལས་ཀ", "ངལ་གསོ", "གྲ་སྒྲིག");

    companion object {
        fun of(code: String): Language = entries.firstOrNull { it.code == code } ?: EN
    }
}
