import XCTest
@testable import IntervalTimerCore

/// A home section is now the same `Block` the editor has always used: a list of intervals with one
/// multiplier meaning "run this whole section that many times". Mirror of
/// app/src/test/java/com/chrispoole/intervaltimer/model/HomeBlockTest.kt — keep them in step.
final class HomeBlockTests: XCTestCase {

    private func work(_ s: Int) -> SeqInterval { SeqInterval(.work, s) }
    private func rest(_ s: Int) -> SeqInterval { SeqInterval(.rest, s) }

    func testTheMultiplierRepeatsTheWholeSectionNotEachInterval() {
        // Work 30 / Work 20 / Rest 10, twice over — the "work, work, rest" shape.
        let b = Block([work(30), work(20), rest(10)], 2)
        XCTAssertEqual(homePreset([b]).intervals,
                       [work(30), work(20), rest(10), work(30), work(20), rest(10)])
    }

    func testSectionsPlayInOrder() {
        let a = Block([work(30), rest(15)], 2)
        let b = Block([work(60)], 1)
        XCTAssertEqual(homePreset([a, b]).intervals,
                       [work(30), rest(15), work(30), rest(15), work(60)])
    }

    /// Dialling rest down to nothing has always been how you say "no rest on this one", and it has to
    /// keep working — but the interval stays in the section so the number is still there to dial back
    /// up. So the drop happens at playback, not in the UI.
    func testZeroLengthIntervalsAreDroppedAtPlaybackNotDeleted() {
        let b = Block([work(30), rest(0)], 3)
        XCTAssertEqual(homePreset([b]).intervals, [work(30), work(30), work(30)])
        XCTAssertEqual(b.items.count, 2)
    }

    func testASectionOfNothingButZeroesContributesNothing() {
        XCTAssertTrue(homePreset([Block([work(0), rest(0)], 4)]).intervals.isEmpty)
    }

    /// Only the classic shape keeps the timer's "3 / 8" round counter; anything with a shape of its
    /// own counts interval positions instead, because there is no single round to count.
    func testOnlyOneWorkAndAnOptionalRestCountsAsBasic() {
        XCTAssertTrue(Block([work(30), rest(15)], 8).isBasic)
        XCTAssertTrue(Block([work(30)], 8).isBasic)
        XCTAssertFalse(Block([work(30), work(20), rest(10)], 2).isBasic)
        XCTAssertFalse(Block([work(30), rest(15), rest(5)], 2).isBasic)
        XCTAssertFalse(Block([rest(15), work(30)], 2).isBasic)   // rest first is not the classic home
        XCTAssertFalse(Block([], 2).isBasic)
    }

    func testAFreshHomeIsBasicAndKeepsItsRestEvenAtZero() {
        let b = basicBlock(workSec: 30, restSec: 0, rounds: 5)
        XCTAssertTrue(b.isBasic)
        // The rest row survives so the plain home still shows it; playback is what drops it.
        XCTAssertEqual(b.items.count, 2)
        XCTAssertEqual(homePreset([b]).intervals, Array(repeating: work(30), count: 5))
    }
}
