import XCTest
@testable import IntervalTimerCore

final class WorkoutTests: XCTestCase {

    func testBaseWorkoutStructure() {
        let w = baseWorkout(prepareMs: 5_000, workMs: 30_000, restMs: 15_000, rounds: 3)
        // prepare + (work,rest) + (work,rest) + work = 6 intervals, no trailing rest
        XCTAssertEqual(6, w.intervals.count)
        XCTAssertEqual(Phase.prepare, w.intervals.first?.phase)
        XCTAssertEqual(Phase.work, w.intervals.last?.phase)
        XCTAssertEqual(5_000 + 3 * 30_000 + 2 * 15_000, w.totalMs)
    }

    func testMapsAbsoluteTimeToTheRightInterval() {
        let w = baseWorkout(prepareMs: 5_000, workMs: 20_000, restMs: 10_000, rounds: 2)
        // boundaries (ms): prepare[0,5k) work1[5k,25k) rest1[25k,35k) work2[35k,55k)
        XCTAssertEqual(Phase.prepare, w.progressAt(0).phase)
        XCTAssertEqual(5_000, w.progressAt(0).remainingMs)

        // Exactly on a boundary belongs to the NEXT interval.
        XCTAssertEqual(Phase.work, w.progressAt(5_000).phase)
        XCTAssertEqual(20_000, w.progressAt(5_000).remainingMs)

        XCTAssertEqual(1, w.progressAt(24_999).remainingMs)
        XCTAssertEqual(Phase.rest, w.progressAt(25_000).phase)
        XCTAssertEqual(Phase.work, w.progressAt(35_000).phase)
        XCTAssertEqual(2, w.progressAt(35_000).round)

        XCTAssertTrue(w.progressAt(55_000).done)
        XCTAssertTrue(w.progressAt(9_999_999).done)
        XCTAssertFalse(w.progressAt(54_999).done)
    }

    func testFractionRunsZeroToOneWithinAnInterval() {
        let w = baseWorkout(prepareMs: 0, workMs: 10_000, restMs: 0, rounds: 1)
        XCTAssertEqual(0, w.progressAt(0).fraction, accuracy: 0.0001)
        XCTAssertEqual(0.5, w.progressAt(5_000).fraction, accuracy: 0.0001)
        XCTAssertEqual(1, w.progressAt(9_999).fraction, accuracy: 0.01)
    }

    /// Drift guard: within an interval, remaining must fall by exactly the elapsed delta — no
    /// accumulated rounding. Sampled in an irregular step so boundary alignment can't hide a drift,
    /// and remaining must always stay inside the interval bound.
    func testRemainingFallsExactlyWithElapsedTime() {
        let w = baseWorkout(prepareMs: 5_000, workMs: 20_000, restMs: 10_000, rounds: 2)
        let step = 137 // irregular, prime-ish step to dodge boundary alignment
        var t = 0
        var asserted = 0
        while t + step <= w.totalMs {
            let a = w.progressAt(t)
            let b = w.progressAt(t + step)
            XCTAssertTrue((0...a.intervalDurationMs).contains(a.remainingMs))
            // Same interval => the clock moved by exactly `step`. Across a boundary the next
            // interval restarts, so remaining goes up instead — that is the reset, not drift.
            if a.phase == b.phase, a.round == b.round, a.intervalDurationMs == b.intervalDurationMs {
                XCTAssertEqual(a.remainingMs - step, b.remainingMs)
                asserted += 1
            }
            t += step
        }
        // The Android predecessor of this test once passed while asserting nothing. Pin that the
        // guarded comparison above actually runs, so it can never silently go vacuous again.
        XCTAssertTrue(asserted > 300, "expected many same-interval samples, got \(asserted)")
    }

    /// A jump straight to an absolute time agrees with arriving there in many small steps.
    func testPathToAnInstantDoesNotChangeTheReading() {
        let w = baseWorkout(prepareMs: 3_000, workMs: 7_000, restMs: 4_000, rounds: 3)
        var walked = 0
        for _ in 0..<261 { walked += 97 }        // 25_317ms reached in 261 uneven hops
        let direct = w.progressAt(25_317)
        let stepwise = w.progressAt(walked)
        XCTAssertEqual(direct.phase, stepwise.phase)
        XCTAssertEqual(direct.round, stepwise.round)
        XCTAssertEqual(direct.remainingMs, stepwise.remainingMs)
    }
}
