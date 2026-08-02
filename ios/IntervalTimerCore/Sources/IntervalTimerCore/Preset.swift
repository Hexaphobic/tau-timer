import Foundation

/// One block in a sequence preset: work or rest for a number of seconds.
///
/// `id` is for SwiftUI and nothing else. Both interval `ForEach`es keyed on array position, so a
/// middle delete presented as "the last identity left": the bottom row faded out while the tapped
/// one took on its neighbour's phase and duration in place. It is deliberately out of `CodingKeys`
/// and out of `==` — every wire format is written field by field (PresetStore, Settings, and their
/// Android twins), and `==` is load-bearing in two places that must not see it: `groupIntervals`
/// recovers a group's ×N by comparing runs of intervals, and the home saves off
/// `.onChange(of: rows)`, which would then fire on every reload. Decoding therefore mints a fresh
/// id, which is right — rows read off disk are new rows.
///
/// Kotlin's mirror has no counterpart on purpose; the reason is written there.
public struct SeqInterval: Equatable, Codable, Sendable {
    /// `private(set)` so an edit has to go through `with`, which carries `id` across. Rebuilding one
    /// through `init` mints a new identity, and SwiftUI reads that as the row leaving and another
    /// arriving — which tears down the row's `GlassCircle`s mid-press, and their `onDisappear` kills
    /// the hold-to-repeat timer on the very first step.
    public private(set) var phase: Phase
    public private(set) var durationSec: Int
    public let id = UUID()

    private enum CodingKeys: String, CodingKey { case phase, durationSec }

    public init(_ phase: Phase, _ durationSec: Int) {
        self.phase = phase
        self.durationSec = durationSec
    }

    /// The same row with a value changed, rather than a different row holding the new value.
    public func with(phase: Phase? = nil, durationSec: Int? = nil) -> SeqInterval {
        var copy = self
        if let phase { copy.phase = phase }
        if let durationSec { copy.durationSec = durationSec }
        return copy
    }

    public static func == (a: SeqInterval, b: SeqInterval) -> Bool {
        a.phase == b.phase && a.durationSec == b.durationSec
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

    /// Build a runnable `Workout`: a prepare lead-in, then the sequence.
    ///
    /// Rounds count work, not position. Numbering by position made every rest a round of its own, so
    /// a four-set sequence run twice counted to fifteen — eight work, eight rest, less the closing
    /// rest the timer drops — and drew fifteen pips for four sets. A rest carries the round of the
    /// work it follows, exactly as `baseWorkout` has always numbered the plain home, so both count
    /// the same thing.
    public func toWorkout(prepareMs: Int = 5_000) -> Workout {
        let seq = playbackIntervals()
        var list: [Interval] = []
        if prepareMs > 0 { list.append(Interval(.prepare, prepareMs)) }
        var round = 0
        for s in seq {
            if s.phase == .work { round += 1 }
            list.append(Interval(s.phase, s.durationSec * 1000, round: round))
        }
        // Only when it runs more than once is there a shape to draw: every pass holds the same work,
        // since expanding repeats the written sequence verbatim and playable() only ever drops a rest.
        let perPass = intervals.filter { $0.phase == .work }.count
        return Workout(list, roundsPerPass: repeatAll > 1 ? perPass : 0)
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

/// The workout the home's GO will run — the ONE builder, used by the button and by the total printed
/// above it.
///
/// It was two: GO branched here while the total was measured off `homePreset` alone, and the two
/// branches do not agree in every state you can reach. Dial a Rest to 0 and tap its label to make it
/// a Work — the flip re-applies Work's 5s floor now, but every preset saved before it didn't, so the
/// state still arrives from disk — and a single basic section of Work 0 / Rest
/// 15 at one round has `baseWorkout` playing a lone zero-length work (nothing at all) while
/// `homePreset` drops the empty work, leaves one rest, and keeps it because a lone interval is the
/// whole sequence. The label said 15s over a button that ran 0. One builder cannot disagree with
/// itself.
public func homeWorkout(_ blocks: [Block], repeatAll: Int, prepareMs: Int) -> Workout {
    // repeatAll == 1 because an outer ×N is exactly what stops this being a plain "n / rounds"
    // workout, so a leftover one must not silently double a single basic section.
    //
    // durationSec > 0 because `baseWorkout` keeps a zero-length work and stamps rounds 1..N on the
    // rests that follow it: Work 0 / Rest 15 × 5 printed "5 sets" over a workout that never enters
    // work once, and the timer's counter and pips read the same rounds. Sent the sequence way it
    // meets homePreset's "drop the empties" filter, which is the only rule that should be in play
    // here, and the count becomes what actually plays. Every other basic shape lands in exactly the
    // branch it did before; totals are unchanged bar the one round case, where the lone rest is now
    // played instead of thrown away — `baseWorkout`'s `r < rounds` guard dropped it, so that shape
    // used to run to nothing at all.
    if blocks.count == 1, let b = blocks.first, b.isBasic, b.items[0].durationSec > 0, repeatAll == 1 {
        return baseWorkout(
            prepareMs: prepareMs,
            workMs: b.items[0].durationSec * 1000,
            restMs: (b.items.count > 1 ? b.items[1].durationSec : 0) * 1000,
            rounds: b.repeatCount
        )
    }
    return homePreset(blocks, repeatAll: repeatAll).toWorkout(prepareMs: prepareMs)
}

/// How long that workout plays, the lead-in excluded — half of what the home prints up top.
public func homeSeconds(_ blocks: [Block], repeatAll: Int) -> Int {
    homeWorkout(blocks, repeatAll: repeatAll, prepareMs: 0).totalMs / 1000
}

/// Work sets in the whole workout — the other half, and the same number the timer will count you
/// through, because it is read off the same built workout rather than worked out again from blocks.
public func homeSets(_ blocks: [Block], repeatAll: Int) -> Int {
    homeWorkout(blocks, repeatAll: repeatAll, prepareMs: 0).intervals.map(\.round).max() ?? 0
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
    /// Worth a name because it decides how the workout is *built*: a basic single section runs as a
    /// `baseWorkout`, anything else — two sections, or one section holding work/work/rest — runs as a
    /// sequence. Both count the same thing either way, one round per work interval, so the "3 / 8" on
    /// the timer means the same in both.
    public var isBasic: Bool {
        items.first?.phase == .work
            && (items.count == 1 || (items.count == 2 && items[1].phase == .rest))
    }
}

/// A group's ×N repeats its items *as they are*, so a repeated interval comes back carrying the same
/// `id` each time. A list over this output must key on position; `\.id` would be a duplicate key.
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
/// lengths 1...4 and take whichever repeated pattern covers the most intervals.
///
/// Where nothing repeats, a work interval keeps the rest that follows it. A block of one interval is
/// not what a group means to anyone reading it: Ladder climbs 20/30/40/50/60 and never repeats
/// anything, so it used to reopen as nine groups with "work 20" and "rest 20" in separate boxes. A
/// work and its recovery are one thing you do — the same shape the home's sections are built from.
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
        // covered == 1 means nothing repeated here. Absorbing only rests is what keeps this from
        // eating the start of a pattern: a repeat always begins at the interval after the last rest,
        // so the scan at the next position still sees it whole.
        if covered == 1 {
            var end = i + 1
            while end < flat.count, flat[end].phase == .rest { end += 1 }
            best = Block(Array(flat[i..<end]), 1)
            covered = end - i
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
