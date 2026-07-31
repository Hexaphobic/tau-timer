import Foundation

/// Renders the countdown in a chosen `Language`: native-digit clock, and spelled-out word mode.
public enum Numbers {

    /// MM:SS with native digit glyphs + colon when the language has them, else Western.
    public static func clock(_ remainingMs: Int, _ lang: Language) -> String {
        if lang.stacks { return clockLines(remainingMs, lang).joined(separator: lang.colon) }
        let ascii = formatMs(remainingMs)
        guard let glyphs = lang.digits else { return ascii }
        return ascii.map { c -> String in
            if let d = asciiDigit(c) { return glyphs[d] }
            if c == ":" { return lang.colon }
            return String(c)
        }.joined()
    }

    /// A composing language's numeral for `n`: Han for Chinese/Japanese, Sino-Korean for Korean.
    private static func numeral(_ n: Int, _ lang: Language) -> String {
        lang.han ? han(n, lang) : korean(n)
    }

    /// The clock split into display lines. Composed minute forms are wide — 一：三十 is four
    /// full-width glyphs — so minutes and seconds stack, letting each line roughly double in size on
    /// the same screen. The caller draws its own separator between them. Everything else is one line.
    public static func clockLines(_ remainingMs: Int, _ lang: Language) -> [String] {
        if !lang.stacks { return [clock(remainingMs, lang)] }
        let total = (max(remainingMs, 0) + 999) / 1000
        let min = total / 60
        let sec = total % 60
        return min == 0 ? [numeral(sec, lang)] : [numeral(min, lang), numeral(sec, lang)]
    }

    /// A plain whole number in the language's own numerals — for the round counter, which used to
    /// print Western digits whatever language you were in. Han languages get proper numerals
    /// (十六, not 一六); scripts with their own digit glyphs get those; everyone else keeps 0-9.
    ///
    /// Han composition stops at 99 and falls back to digit-by-digit above it — 100+ rounds is a
    /// number no workout reaches, and the hundreds forms are not worth the risk of getting the
    /// 〇-filler and the 一百/百 rules subtly wrong. Korean composes further because it can do so
    /// with one recursive line.
    public static func count(_ n: Int, _ lang: Language) -> String {
        if lang.stacks { return numeral(n, lang) }
        guard let g = lang.digits else { return String(n) }
        return String(n).map { c in asciiDigit(c).map { g[$0] } ?? String(c) }.joined()
    }

    /// The widest strings this interval will ever show, so the clock can be sized once and held
    /// there instead of refitting every second.
    ///
    /// Minutes only ever shrink, so the interval's own minute count is the widest it gets. Seconds
    /// pass through 59 on the way down whenever the interval runs a minute or more — which is the
    /// real point: 三十九 is three glyphs and 四十 is two, so a size fitted to whatever is on screen
    /// jumps every time the count crosses a ten.
    public static func widestClockLines(_ intervalMs: Int, _ lang: Language) -> [String] {
        let totalSec = max(intervalMs, 0) / 1000
        if !lang.stacks { return [clock(intervalMs, lang)] }
        if totalSec < 60 { return [widest(upTo: totalSec, lang)] }
        return [widest(upTo: totalSec / 60, lang), widest(upTo: 59, lang)]
    }

    /// The widest numeral the count will actually pass through on its way down to zero.
    ///
    /// Not simply the starting value, which is what this used to assume. Composed numerals do not
    /// shrink with the number: a 30s interval opens on 三十, two glyphs, and one second later shows
    /// 二十九, which is three — so a size fitted to the start ran that third glyph clean off both
    /// edges of the screen. Every reachable value is cheap to check, and it happens once per
    /// interval rather than once per second.
    ///
    /// Ties go to the larger number, so the seconds line still settles on 五十九 / 오십구.
    private static func widest(upTo n: Int, _ lang: Language) -> String {
        var widest = numeral(0, lang)
        guard n > 0 else { return widest }
        for i in 1...n {
            let s = numeral(i, lang)
            if s.count >= widest.count { widest = s }
        }
        return widest
    }

    /// Han cardinal for 0..99 (clock components); glyphs supply 0-9, 十 is ten.
    private static func han(_ n: Int, _ lang: Language) -> String {
        guard let g = lang.digits, n >= 0 else { return String(n) }
        switch n {
        case ..<10: return g[n]
        case ..<20: return "十" + (n % 10 == 0 ? "" : g[n % 10])
        case ..<100: return g[n / 10] + "十" + (n % 10 == 0 ? "" : g[n % 10])
        default: return String(n).map { c in asciiDigit(c).map { g[$0] } ?? String(c) }.joined()
        }
    }

    /// The remaining seconds spelled out in the language, for word mode under a minute.
    /// Word mode only fires under a minute, so the range is 0...60 — small enough to spell by hand
    /// for the four spelling languages, which is why the app carries no ICU dependency.
    public static func words(_ remainingMs: Int, _ lang: Language) -> String {
        let n = Swift.min(Swift.max((Swift.max(remainingMs, 0) + 999) / 1000, 0), 60)
        switch lang {
        case .es: return spanish(n)
        case .fr: return french(n)
        case .ru: return russian(n)
        default: return english(n)
        }
    }

    private static let enOnes = [
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    ]
    private static let enTens = ["", "", "twenty", "thirty", "forty", "fifty", "sixty"]
    private static func english(_ n: Int) -> String {
        if n < 20 { return enOnes[n] }
        if n % 10 == 0 { return enTens[n / 10] }
        return "\(enTens[n / 10])-\(enOnes[n % 10])"
    }

    // 0-29 are irregular in Spanish (veintiuno…); 30+ compose as "treinta y uno".
    private static let esUnder30 = [
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve",
        "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve",
    ]
    private static let esTens = ["", "", "", "treinta", "cuarenta", "cincuenta", "sesenta"]
    private static func spanish(_ n: Int) -> String {
        if n < 30 { return esUnder30[n] }
        if n % 10 == 0 { return esTens[n / 10] }
        return "\(esTens[n / 10]) y \(esUnder30[n % 10])"
    }

    private static let frUnder20 = [
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf",
    ]
    private static let frTens = ["", "", "vingt", "trente", "quarante", "cinquante", "soixante"]
    private static func french(_ n: Int) -> String {
        if n < 20 { return frUnder20[n] }
        if n % 10 == 0 { return frTens[n / 10] }
        if n % 10 == 1 { return "\(frTens[n / 10]) et un" }          // vingt et un
        return "\(frTens[n / 10])-\(frUnder20[n % 10])"              // vingt-deux (range caps at 60, no 70/80 forms)
    }

    private static let ruUnder20 = [
        "ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять",
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать",
    ]
    private static let ruTens = ["", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят"]
    private static func russian(_ n: Int) -> String {
        if n < 20 { return ruUnder20[n] }
        if n % 10 == 0 { return ruTens[n / 10] }
        return "\(ruTens[n / 10]) \(ruUnder20[n % 10])"
    }

    // Sino-Korean (used for clock time): 일 이 삼…, 십 for ten, 백 for hundred, composed without
    // spaces. The leading 일 is dropped at both scales — ten is 십, not 일십; a hundred is 백.
    private static let koOnes = ["영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"]
    private static func korean(_ n: Int) -> String {
        if n < 0 { return String(n) }
        if n < 10 { return koOnes[n] }
        if n < 100 {
            let tens = n / 10 == 1 ? "십" : koOnes[n / 10] + "십"
            return n % 10 == 0 ? tens : tens + koOnes[n % 10]
        }
        // Thousands would need 천/만 and koOnes[n / 100] indexes past the array at 1000 — an
        // out-of-bounds trap inside a view body, which takes the whole timer down and then
        // crash-loops, because a running workout is re-attached to on relaunch. Digits instead:
        // the same trade Cistercian makes at its own 9999 ceiling, and han() above 99.
        if n >= 1000 { return String(n) }
        let hundreds = n / 100 == 1 ? "백" : koOnes[n / 100] + "백"
        return n % 100 == 0 ? hundreds : hundreds + korean(n % 100)
    }

    /// 0-9 for an ASCII digit, nil for anything else — so a stray sign or separator passes through
    /// as itself instead of indexing a ten-entry glyph table off its end.
    private static func asciiDigit(_ c: Character) -> Int? {
        guard c.isASCII, let v = c.wholeNumberValue, (0...9).contains(v) else { return nil }
        return v
    }
}
