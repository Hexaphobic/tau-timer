import XCTest
@testable import IntervalTimerCore

/// Section names: stamped flat by `flatten`, recovered by `groupIntervals`, worn by the timer.
/// The twin of Android's SectionNameTest — the two builds have to agree on which interval wears
/// whose name, since the same presets.json moves between them.
final class SectionNameTests: XCTestCase {
    private func w(_ s: Int, _ l: String = "") -> SeqInterval { SeqInterval(.work, s, l) }
    private func r(_ s: Int, _ l: String = "") -> SeqInterval { SeqInterval(.rest, s, l) }

    private var home: [Block] {
        [
            Block([w(30), r(15)], 2, "Splits"),
            Block([w(45), r(15)], 1, "Quads"),
            Block([w(30), r(15)], 2, "Pistols"),
        ]
    }

    func testFlattenStampsTheBlockNameOnEveryInterval() {
        XCTAssertEqual(
            [
                w(30, "Splits"), r(15, "Splits"), w(30, "Splits"), r(15, "Splits"),
                w(45, "Quads"), r(15, "Quads"),
                w(30, "Pistols"), r(15, "Pistols"), w(30, "Pistols"), r(15, "Pistols"),
            ],
            flatten(home)
        )
    }

    /// Same shape, different names — the label sits in equality, so the sections stay separate.
    func testGroupingRoundTripsNamesAndKeepsNamedTwinsApart() {
        XCTAssertEqual(home, groupIntervals(flatten(home)))
    }

    func testWorkWearsItsOwnNameAndRestWearsTheOneAhead() {
        let ivs = Preset("", flatten(home)).toWorkout(prepareMs: 5_000).intervals
        XCTAssertEqual(["Splits", "Splits", "Quads", "Pistols", "Pistols"],
                       ivs.filter { $0.phase == .work }.map(\.label))
        // Four rests play — the trailing one is dropped. Each points at the work AHEAD of it, so the
        // one closing Splits' last pass already says Quads. The lead-in points at the first.
        XCTAssertEqual("Splits", ivs.first { $0.phase == .prepare }?.label)
        XCTAssertEqual(["Splits", "Quads", "Pistols", "Pistols"],
                       ivs.filter { $0.phase == .rest }.map(\.label))
    }

    /// The presets list reads a saved preset back through `groupIntervals` — the same call the editor
    /// makes — so a home saved and reopened must come back as its named sections, not as the flat
    /// work/rest/work/rest the ×N expands to.
    func testASavedHomeReopensAsItsNamedSections() {
        let reopened = groupIntervals(homePreset(home).intervals)
        XCTAssertEqual(["Splits", "Quads", "Pistols"], reopened.map(\.name))
        XCTAssertEqual([2, 1, 2], reopened.map(\.repeatCount))
    }

    /// No work left ahead — nothing to point at, so a trailing rest stays blank rather than reaching
    /// backwards for the set that just ended.
    func testARestWithNoWorkAheadStaysBlank() {
        let trailing = [Block([w(30), r(15)], 1, "Splits"), Block([r(60)], 1, "Cooldown")]
        let ivs = Preset("", flatten(trailing)).toWorkout(prepareMs: 0).intervals
        XCTAssertEqual("", ivs.last?.label)
    }

    /// The name reaches the screen, not just the interval list: the timer reads `TimerProgress`.
    /// Splits w30 r15 w30 r15, Quads w45 r15, Pistols w30 r15 w30 — the closing rest dropped.
    func testProgressCarriesTheNameOfTheIntervalItLandsIn() {
        let out = Preset("", flatten(home)).toWorkout(prepareMs: 0)
        XCTAssertEqual("Splits", out.progressAt(1_000).label)     // first work
        XCTAssertEqual("Splits", out.progressAt(31_000).label)    // its rest — the next work is Splits too
        XCTAssertEqual("Quads", out.progressAt(80_000).label)     // the rest that hands over to Quads
        XCTAssertEqual("Quads", out.progressAt(100_000).label)    // Quads' own work
        XCTAssertEqual("Pistols", out.progressAt(200_000).label)  // last work
    }
}
