import XCTest
@testable import IntervalTimerCore

final class CistercianTests: XCTestCase {

    func testZeroIsJustTheStave() {
        XCTAssertEqual([Stroke(0, -1, 0, 1)], cistercian(0))
    }

    func testOneIsATopRightBar() {
        XCTAssertEqual(Stroke(0, -1, 1, -1), cistercian(1)[1])
    }

    func testTenMirrorsOneToTheLeft() {
        XCTAssertEqual(Stroke(0, -1, -1, -1), cistercian(10)[1])
    }

    func testHundredFlipsOneToTheBottom() {
        XCTAssertEqual(Stroke(0, 1, 1, 1), cistercian(100)[1])
    }

    func testEachQuadrantReadsItsOwnDigit() {
        // 1234: units 4, tens 3, hundreds 2, thousands 1 — one stroke each, plus the stave.
        XCTAssertEqual(5, cistercian(1234).count)
        XCTAssertEqual(12 + 1, cistercian(9999).count) // 9 is three strokes per quadrant
    }

    func testSecondsRoundUpAndStayInRange() {
        XCTAssertEqual(30, cistercianSeconds(30_000))   // a fresh interval reads its full duration
        XCTAssertEqual(1, cistercianSeconds(1))         // and only hits 0 at true zero
        XCTAssertEqual(0, cistercianSeconds(0))
        XCTAssertEqual(0, cistercianSeconds(-5_000))
        _ = cistercian(cistercianSeconds(9_999_000_000)) // clamped, not trapped
    }

    func testAllStrokesStayInsideTheUnitBox() {
        for n in 0...9999 {
            XCTAssertTrue(cistercian(n).allSatisfy {
                (-1...1).contains($0.x1) && (-1...1).contains($0.y1)
                    && (-1...1).contains($0.x2) && (-1...1).contains($0.y2)
            })
        }
    }
}
