import XCTest
@testable import IntervalTimerCore

final class NumbersTests: XCTestCase {

    private func zh(_ ms: Int) -> String { Numbers.clock(ms, .zh) }

    func testHanNumeralsCompose() {
        XCTAssertEqual("五", zh(5_000))         // 5
        XCTAssertEqual("十", zh(10_000))        // 10 (not 一〇)
        XCTAssertEqual("十五", zh(15_000))       // 15
        XCTAssertEqual("二十五", zh(25_000))      // 25
        XCTAssertEqual("三十", zh(30_000))       // 30 (not 三〇)
        XCTAssertEqual("五十九", zh(59_000))      // 59
        XCTAssertEqual("一：〇", zh(60_000))      // 1:00
        XCTAssertEqual("一：三十", zh(90_000))     // 1:30
    }

    func testHanMinutesStackButSecondsStaySingleLine() {
        XCTAssertEqual(["三十"], Numbers.clockLines(30_000, .zh))
        XCTAssertEqual(["一", "三十"], Numbers.clockLines(90_000, .zh))
        // The flat clock string still carries the colon.
        XCTAssertEqual("一：三十", zh(90_000))
        // Non-Han languages never stack.
        XCTAssertEqual(["1:20"], Numbers.clockLines(80_000, .en))
    }

    func testWesternDigitLanguagesStillDropLeadingZeros() {
        XCTAssertEqual("16", Numbers.clock(16_000, .en))
        XCTAssertEqual("1:20", Numbers.clock(80_000, .en))
        XCTAssertEqual("१६", Numbers.clock(16_000, .hi)) // Hindi glyphs, digit-by-digit is correct here
    }

    func testDurationsReadAsMinutesPastSixty() {
        XCTAssertEqual("45s", secLabel(45))
        XCTAssertEqual("59s", secLabel(59))
        XCTAssertEqual("1:00", secLabel(60))
        XCTAssertEqual("1:30", secLabel(90))   // not "90s"
        XCTAssertEqual("2:05", secLabel(125))
    }

    // Word mode fires under a minute, so the speller only needs 0...60. Spot-check the tricky joins.
    private func word(_ sec: Int, _ lang: Language) -> String { Numbers.words(sec * 1000, lang) }

    func testSpellsNumbersWithoutIcu() {
        XCTAssertEqual("zero", word(0, .en))
        XCTAssertEqual("twenty-one", word(21, .en))
        XCTAssertEqual("forty", word(40, .en))
        XCTAssertEqual("sixty", word(60, .en))

        XCTAssertEqual("veintiuno", word(21, .es))   // one word in Spanish
        XCTAssertEqual("treinta y uno", word(31, .es))
        XCTAssertEqual("sesenta", word(60, .es))

        XCTAssertEqual("vingt et un", word(21, .fr)) // "et un", not "-un"
        XCTAssertEqual("vingt-deux", word(22, .fr))
        XCTAssertEqual("soixante", word(60, .fr))

        XCTAssertEqual("двадцать один", word(21, .ru))
        XCTAssertEqual("сорок", word(40, .ru))
    }

    /// Korean composes numerals of its own, so it takes the stacked clock with Chinese and Japanese
    /// rather than the speller — 1:30 is two short lines, not one growing word.
    func testSinoKoreanComposesAndStacks() {
        XCTAssertEqual("십", Numbers.clock(10_000, .ko))       // 10, not 일십
        XCTAssertEqual("이십일", Numbers.clock(21_000, .ko))
        XCTAssertEqual("오십구", Numbers.clock(59_000, .ko))
        XCTAssertEqual(["일", "삼십"], Numbers.clockLines(90_000, .ko))
        XCTAssertEqual(["십", "영"], Numbers.clockLines(600_000, .ko))  // 10:00
        // Past ninety-nine the hundreds form kicks in, so a long interval's minute line holds up.
        XCTAssertEqual("백오십", Numbers.count(150, .ko))
        XCTAssertEqual("구백구십구", Numbers.count(999, .ko))
        // One size for the interval: seconds always pass through 59 on the way down.
        XCTAssertEqual(["일", "오십구"], Numbers.widestClockLines(90_000, .ko))
    }

    /// The clock is sized once per interval against the widest value it will ever show. Composed
    /// numerals don't shrink with the number, so "the value it starts at" is not that: a 30s
    /// interval opens on 三十 and immediately passes through 二十九, a glyph wider.
    func testWidestLineCoversEveryValueTheCountPassesThrough() {
        for lang in Language.allCases where lang.stacks {
            for intervalSec in [5, 30, 59, 60, 90, 125, 600] {
                let widest = Numbers.widestClockLines(intervalSec * 1000, lang)
                let budget = widest.map(\.count).max() ?? 0
                for remaining in 0...intervalSec {
                    let lines = Numbers.clockLines(remaining * 1000, lang)
                    XCTAssertLessThanOrEqual(
                        lines.map(\.count).max() ?? 0, budget,
                        "\(lang) \(intervalSec)s interval: \(lines) is wider than the fitted \(widest)"
                    )
                    // A long interval drops from two lines to one as it passes under a minute.
                    // Fewer lines is fine — the fitted size still holds; more would overflow.
                    XCTAssertLessThanOrEqual(lines.count, widest.count,
                                             "\(lang) \(intervalSec)s: \(lines) stacks taller than \(widest)")
                }
            }
        }
    }

    /// A round counter is unbounded — nothing clamps `rounds`, a group's ×N or the overall ×N, and
    /// they multiply. At 1000 the Korean composer used to index its 10-entry digit array with
    /// `n / 100`, trapping inside a view body: the timer died, and because a running workout is
    /// re-attached to on launch, it died again on every relaunch. Every composing script must
    /// return *something* for any Int a workout can produce.
    func testEveryComposingScriptSurvivesAnUnboundedRoundCount() {
        for lang in Language.allCases {
            for n in [0, 1, 99, 100, 999, 1_000, 1_001, 9_999, 100_000, Int.max] {
                XCTAssertFalse(Numbers.count(n, lang).isEmpty, "\(lang) count(\(n)) came out blank")
            }
        }
    }
}
