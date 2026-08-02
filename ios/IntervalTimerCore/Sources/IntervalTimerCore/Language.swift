import Foundation

/// The 11 display languages, plus Cistercian. Chinese and Japanese share a tile, so the
/// picker shows 11 of the 12.
///
/// `digits` maps 0-9 to native glyphs (nil = Western 0-9); `colon` is the clock separator;
/// `work`/`rest`/`ready` are translated phase labels. Values are the research-verified
/// glyphs/labels; the system fonts cover every script here.
///
/// Declaration order is the order of the picker, and `ordinal` seeds each tile's aura, so it is
/// load-bearing rather than cosmetic.
public enum Language: String, CaseIterable, Sendable {
    // Hand-set, not alphabetical and not by speaker count: it is the owner's running order. Chinese
    // and Japanese sit together at the front because they share one tile (see `han`) — the pair takes
    // a single slot wherever it lands.
    case en, ja, zh, es, ko
    // Not a language — a 13th-century monastic cipher with no words of its own, so it borrows
    // English labels. Its glyphs are drawn, not typed (see Cistercian.swift), which is why digits
    // is nil and every render site branches on `cistercian` before reaching for a font. Fifth by
    // request, rather than parked at the end where a curiosity goes to be missed.
    case ci
    case ru, hi, ar, fr, bn, th

    public struct Spec: Sendable {
        public let english: String
        public let digits: [String]?
        public let colon: String
        public let work: String
        public let rest: String
        public let ready: String
    }

    /// One table, the way the Kotlin enum carried its values in its constructor.
    public var spec: Spec {
        switch self {
        case .en: return Spec(english: "English", digits: nil, colon: ":",
                              work: "Work", rest: "Rest", ready: "Get ready")
        case .zh: return Spec(english: "中文 · Chinese",
                              digits: ["〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"],
                              colon: "：", work: "运动", rest: "休息", ready: "准备")
        case .ja: return Spec(english: "日本語 · Japanese",
                              digits: ["〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"],
                              colon: "：", work: "運動", rest: "休憩", ready: "準備")
        case .ko: return Spec(english: "한국어 · Korean", digits: nil, colon: ":",
                              work: "운동", rest: "휴식", ready: "준비")
        case .ru: return Spec(english: "Русский · Russian", digits: nil, colon: ":",
                              work: "Работа", rest: "Отдых", ready: "Приготовься")
        case .hi: return Spec(english: "हिन्दी · Hindi",
                              digits: ["०", "१", "२", "३", "४", "५", "६", "७", "८", "९"],
                              colon: ":", work: "काम", rest: "आराम", ready: "तैयार")
        case .ar: return Spec(english: "العربية · Arabic",
                              digits: ["٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"],
                              colon: ":", work: "تمرين", rest: "راحة", ready: "استعد")
        case .es: return Spec(english: "Español · Spanish", digits: nil, colon: ":",
                              work: "Trabajo", rest: "Descanso", ready: "Prepárate")
        case .fr: return Spec(english: "Français · French", digits: nil, colon: ":",
                              work: "Effort", rest: "Repos", ready: "Prêt")
        case .bn: return Spec(english: "বাংলা · Bengali",
                              digits: ["০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"],
                              colon: ":", work: "কাজ", rest: "বিশ্রাম", ready: "প্রস্তুত")
        case .th: return Spec(english: "ไทย · Thai",
                              digits: ["๐", "๑", "๒", "๓", "๔", "๕", "๖", "๗", "๘", "๙"],
                              colon: ":", work: "ทำงาน", rest: "พัก", ready: "เตรียม")
        case .ci: return Spec(english: "Cistercian", digits: nil, colon: ":",
                              work: "Work", rest: "Rest", ready: "Get ready")
        }
    }

    public var english: String { spec.english }
    public var digits: [String]? { spec.digits }
    public var colon: String { spec.colon }
    public var work: String { spec.work }
    public var rest: String { spec.rest }
    public var ready: String { spec.ready }

    public var code: String { rawValue }

    /// Position in the picker — also the aura seed, so each tile freezes a different frame.
    public var ordinal: Int { Language.allCases.firstIndex(of: self) ?? 0 }

    /// Chinese/Japanese compose numbers with 十 (十, 二十五…) rather than digit-by-digit (一〇).
    public var han: Bool { self == .zh || self == .ja }

    /// Drawn on a canvas rather than set in a font — no text path applies, word mode included.
    public var cistercian: Bool { self == .ci }

    /// Composes numerals of its own, so the clock stacks minutes over seconds instead of spelling
    /// a running total. Sino-Korean belongs here with Chinese and Japanese: 1:30 reads 일 / 삼십,
    /// two short lines, where a spelled total (구십) grows with every extra digit.
    public var stacks: Bool { han || self == .ko }

    /// CJK glyphs come from the system's CJK fallback, which ships Regular only. Asking for bold
    /// makes the renderer synthesise one by inflating the outline, which spikes at acute stroke
    /// joins (visible as a notch in 九). These render at normal weight.
    public var cjk: Bool { han || self == .ko }

    public static func of(_ code: String) -> Language { Language(rawValue: code) ?? .en }
}
