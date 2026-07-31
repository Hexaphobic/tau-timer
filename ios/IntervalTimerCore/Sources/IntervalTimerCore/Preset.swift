import Foundation

/// One block in a sequence preset: work or rest for a number of seconds.
public struct SeqInterval: Equatable, Codable, Sendable {
    public let phase: Phase
    public let durationSec: Int

    public init(_ phase: Phase, _ durationSec: Int) {
        self.phase = phase
        self.durationSec = durationSec
    }
}

/// A named, ordered sequence of intervals.
///
/// `intervals` is the sequence as written once; `repeatAll` is how many times the whole thing plays
/// end to end. Stored unexpanded so the editor can still show — and change — the repeat afterwards.
public struct Preset: Equatable, Sendable {
    public let name: String
    public let intervals: [SeqInterval]
    public let repeatAll: Int

    public init(_ name: String, _ intervals: [SeqInterval], repeatAll: Int = 1) {
        self.name = name
        self.intervals = intervals
        self.repeatAll = repeatAll
    }

    public func renamed(_ name: String) -> Preset {
        Preset(name, intervals, repeatAll: repeatAll)
    }

    /// The sequence as it actually plays: the whole of it, `repeatAll` times over.
    public func expanded() -> [SeqInterval] {
        repeatAll <= 1 ? intervals : Array(repeating: intervals, count: repeatAll).flatMap { $0 }
    }

    /// The sequence as the timer will run it: repeats expanded, trailing rest dropped.
    public func playbackIntervals() -> [SeqInterval] { playable(expanded()) }

    /// Build a runnable `Workout`: a prepare lead-in, then the sequence (round = 1-based position).
    public func toWorkout(prepareMs: Int = 5_000) -> Workout {
        let seq = playbackIntervals()
        var list: [Interval] = []
        if prepareMs > 0 { list.append(Interval(.prepare, prepareMs)) }
        for (i, s) in seq.enumerated() {
            list.append(Interval(s.phase, s.durationSec * 1000, round: i + 1))
        }
        return Workout(list)
    }
}

/// A rest after the very last work interval is pointless — drop it, so presets can stay as clean
/// (work, rest) × N groups. Always applied to a fully expanded sequence, so the rest *between* two
/// passes survives and only the one that would end the workout goes.
///
/// One definition, used by the clock and by every count shown for a sequence: a screen that
/// advertises an interval the timer never plays is just wrong.
public func playable(_ intervals: [SeqInterval]) -> [SeqInterval] {
    intervals.count > 1 && intervals[intervals.count - 1].phase == .rest
        ? Array(intervals.dropLast())
        : intervals
}

/// The home screen's sequence: each section's intervals, run its own number of times, in order.
///
/// A section used to be a fixed (work, rest) pair — `basicBlock` is still what a fresh home starts
/// from, but a section is now the same `Block` the editor has always used, so it can hold work, work,
/// rest and the ×N still means the one thing it ever meant: run this whole section that many times.
///
/// Zero-length intervals are dropped here rather than forbidden in the UI, because dialling rest down
/// to 0 has always been how you say "no rest on this one" and it should keep working. The interval
/// stays in the section, so the number is still there to dial back up.
///
/// `repeatAll` is the outer ×N — the whole home, top to bottom, that many times. Same meaning and the
/// same field the editor's "Repeat everything" writes, so a home saved as a preset round-trips.
public func homePreset(_ blocks: [Block], repeatAll: Int = 1) -> Preset {
    Preset("", flatten(blocks.map { b in
        Block(b.items.filter { $0.durationSec > 0 }, b.repeatCount)
    }), repeatAll: max(repeatAll, 1))
}

/// The section a fresh home starts from, and what "Add intervals" copies.
public func basicBlock(workSec: Int, restSec: Int, rounds: Int) -> Block {
    Block([SeqInterval(.work, workSec), SeqInterval(.rest, restSec)], rounds)
}

/// A repeated run of intervals — the editor's unit, and now the home's. Flat storage stays the
/// source of truth.
public struct Block: Equatable, Sendable {
    public var items: [SeqInterval]
    public var repeatCount: Int

    public init(_ items: [SeqInterval], _ repeatCount: Int) {
        self.items = items
        self.repeatCount = repeatCount
    }

    /// The classic home shape: one work, optionally one rest, and nothing else.
    ///
    /// Worth a name because it decides what the timer's counter says. A basic single section runs as
    /// a `baseWorkout` and counts "3 / 8" in rounds, which is the number you actually care about
    /// mid-set. Anything else — two sections, or one section holding work/work/rest — has no single
    /// "round" to count, so it runs as a sequence and counts interval positions instead.
    public var isBasic: Bool {
        items.first?.phase == .work
            && (items.count == 1 || (items.count == 2 && items[1].phase == .rest))
    }
}

public func flatten(_ blocks: [Block]) -> [SeqInterval] {
    blocks.flatMap { b in Array(repeating: b.items, count: max(b.repeatCount, 0)).flatMap { $0 } }
}

/// Two rests back to back is just one longer pause, so the editor steers around it (see
/// `Settings.noDoubleRest`).
///
/// Always asked of a fully expanded sequence, which is what makes it catch the cases you can't see
/// by looking at one row: rest ending one group and opening the next, or a group whose own ×N wraps
/// its closing rest onto its opening one.
public func backToBackRests(_ intervals: [SeqInterval]) -> Int {
    guard intervals.count > 1 else { return 0 }
    return zip(intervals, intervals.dropFirst())
        .filter { $0.phase == .rest && $1.phase == .rest }
        .count
}

/// The same count for `blocks` played `repeatAll` times, without building the expanded list — the
/// editor asks this of every row on every frame of a drag, and × 20 of a long sequence is a lot of
/// list to allocate for a question about two neighbours.
///
/// Each pass has the same joins inside it; the only extra ones are where a pass ending in rest meets
/// the next pass opening with one.
public func backToBackRests(_ blocks: [Block], _ repeatAll: Int) -> Int {
    let once = flatten(blocks)
    guard let first = once.first, let last = once.last else { return 0 }
    let passes = max(repeatAll, 1)
    let seam = (passes > 1 && first.phase == .rest && last.phase == .rest) ? passes - 1 : 0
    return backToBackRests(once) * passes + seam
}

/// Recover ×N grouping from a flat list, greedily from the left: at each position try pattern
/// lengths 1...4 and take whichever repeated pattern covers the most intervals. Non-repeating
/// stretches fall out as single-interval ×1 blocks.
public func groupIntervals(_ flat: [SeqInterval]) -> [Block] {
    var blocks: [Block] = []
    var i = 0
    while i < flat.count {
        var best = Block([flat[i]], 1)
        var covered = 1
        for len in 1...4 {
            if i + 2 * len > flat.count { break }
            let pattern = Array(flat[i..<(i + len)])
            var reps = 1
            while i + (reps + 1) * len <= flat.count,
                  Array(flat[(i + reps * len)..<(i + (reps + 1) * len)]) == pattern {
                reps += 1
            }
            if reps > 1 && reps * len > covered {
                best = Block(pattern, reps)
                covered = reps * len
            }
        }
        blocks.append(best)
        i += covered
    }
    return blocks
}

private func w(_ sec: Int) -> SeqInterval { SeqInterval(.work, sec) }
private func r(_ sec: Int) -> SeqInterval { SeqInterval(.rest, sec) }

public let BUILTIN_PRESETS: [Preset] = [
    Preset("Ladder", [w(20), r(20), w(30), r(20), w(40), r(20), w(50), r(20), w(60)]),
    Preset("Pyramid", [w(20), r(15), w(40), r(15), w(60), r(15), w(40), r(15), w(20)]),
    Preset("Tabata", (1...8).flatMap { _ in [w(20), r(10)] }),
    Preset("EMOM 10", (1...10).map { _ in w(60) }),
]
