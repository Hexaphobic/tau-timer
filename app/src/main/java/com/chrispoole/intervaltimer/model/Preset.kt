package com.chrispoole.intervaltimer.model

/**
 * One block in a sequence preset: WORK or REST for a number of seconds.
 *
 * Carries no identity field, unlike the Swift mirror, and that asymmetry is deliberate: SwiftUI's
 * ForEach needs one to tell which row left, while Compose composes these rows positionally
 * (`IntervalRows`, `IntervalStack` — bare forEachIndexed, no `key`) and gives them no exit
 * transition at all, so there is nothing here for an id to be misattributed to. It would also cost
 * more than it does there — a data class can't take a field without it landing in equals(), which
 * `groupIntervals` compares runs with and which the home's save reads.
 */
data class SeqInterval(val phase: Phase, val durationSec: Int)

/**
 * A named, ordered sequence of intervals.
 *
 * [intervals] is the sequence as written once; [repeatAll] is how many times the whole thing plays
 * end to end. Stored unexpanded so the editor can still show — and change — the repeat afterwards.
 */
data class Preset(
    val name: String,
    val intervals: List<SeqInterval>,
    val repeatAll: Int = 1,
)

/** The sequence as it actually plays: the whole of it, [repeatAll] times over. */
fun Preset.expanded(): List<SeqInterval> =
    if (repeatAll <= 1) intervals else List(repeatAll) { intervals }.flatten()

/**
 * A rest after the very last work interval is pointless — drop it, so presets can stay as clean
 * (work, rest) × N groups. Always applied to a fully expanded sequence, so the rest *between* two
 * passes survives and only the one that would end the workout goes.
 *
 * One definition, used by the clock and by every count shown for a sequence: a screen that advertises
 * an interval the timer never plays is just wrong.
 */
fun playable(intervals: List<SeqInterval>): List<SeqInterval> =
    if (intervals.size > 1 && intervals.last().phase == Phase.REST) intervals.dropLast(1) else intervals

/** The sequence as the timer will run it: repeats expanded, trailing rest dropped. */
fun Preset.playbackIntervals(): List<SeqInterval> = playable(expanded())

/**
 * Build a runnable Workout: a PREPARE lead-in, then the sequence.
 *
 * Rounds count WORK, not position. Numbering by position made every rest a round of its own, so a
 * four-set sequence run twice counted to fifteen — eight work, eight rest, less the closing rest the
 * timer drops — and drew fifteen pips for four sets. A rest carries the round of the work it follows,
 * exactly as [baseWorkout] has always numbered the plain home, so both count the same thing.
 */
fun Preset.toWorkout(prepareMs: Long = 5_000): Workout {
    val seq = playbackIntervals()
    var round = 0
    val list = buildList {
        if (prepareMs > 0) add(Interval(Phase.PREPARE, prepareMs))
        seq.forEach { s ->
            if (s.phase == Phase.WORK) round++
            add(Interval(s.phase, s.durationSec * 1000L, round))
        }
    }
    // Only when it runs more than once is there a shape to draw: every pass holds the same work,
    // since expanding repeats the written sequence verbatim and playable() only ever drops a rest.
    val perPass = intervals.count { it.phase == Phase.WORK }
    return Workout(list, roundsPerPass = if (repeatAll > 1) perPass else 0)
}

/**
 * The home screen's sequence: each section's intervals, run its own number of times, in order.
 *
 * A section used to be a fixed (work, rest) pair — [basicBlock] is still what a fresh home starts
 * from, but a section is now the same [Block] the editor has always used, so it can hold work, work,
 * rest and the ×N still means the one thing it ever meant: run this whole section that many times.
 *
 * Zero-length intervals are dropped here rather than forbidden in the UI, because dialling rest down
 * to 0 has always been how you say "no rest on this one" and it should keep working. The interval
 * stays in the section, so the number is still there to dial back up.
 *
 * [repeatAll] is the outer ×N — the whole home, top to bottom, that many times. Same meaning and the
 * same field the editor's "Repeat everything" writes, so a home saved as a preset round-trips.
 */
fun homePreset(blocks: List<Block>, repeatAll: Int = 1): Preset =
    Preset(
        "",
        flatten(blocks.map { b -> b.copy(items = b.items.filter { it.durationSec > 0 }) }),
        repeatAll.coerceAtLeast(1),
    )

/**
 * The workout the home's GO will run — the ONE builder, used by the button and by the total printed
 * above it.
 *
 * It was two: GO branched here while the total was measured off [homePreset] alone, and the two
 * branches do not agree in every state you can reach. Dial a Rest to 0 and tap its label to make it
 * a Work — the flip re-applies Work's 5s floor now, but every preset saved before it didn't, so the
 * state still arrives from disk — and a single basic section of Work 0 / Rest 15
 * at one round has [baseWorkout] playing a lone zero-length work (nothing at all) while [homePreset]
 * drops the empty work, leaves one rest, and keeps it because a lone interval is the whole sequence.
 * The label said 15s over a button that ran 0. One builder cannot disagree with itself.
 */
fun homeWorkout(blocks: List<Block>, repeatAll: Int, prepareMs: Long): Workout {
    val single = blocks.singleOrNull()
    // repeatAll == 1 because an outer ×N is exactly what stops this being a plain "n / rounds"
    // workout, so a leftover one must not silently double a single basic section.
    //
    // durationSec > 0 because [baseWorkout] keeps a zero-length work and stamps rounds 1..N on the
    // rests that follow it: Work 0 / Rest 15 × 5 printed "5 sets" over a workout that never enters
    // WORK once, and the timer's counter and pips read the same rounds. Sent the sequence way it
    // meets homePreset's "drop the empties" filter, which is the only rule that should be in play
    // here, and the count becomes what actually plays. Every other basic shape lands in exactly the
    // branch it did before; totals are unchanged bar the one round case, where the lone rest is now
    // played instead of thrown away — [baseWorkout]'s `r < rounds` guard dropped it, so that shape
    // used to run to nothing at all.
    return if (single != null && single.isBasic && single.items[0].durationSec > 0 && repeatAll == 1) {
        baseWorkout(
            prepareMs = prepareMs,
            workMs = single.items[0].durationSec * 1000L,
            restMs = (single.items.getOrNull(1)?.durationSec ?: 0) * 1000L,
            rounds = single.repeat,
        )
    } else {
        homePreset(blocks, repeatAll).toWorkout(prepareMs)
    }
}

/** How long that workout plays, the lead-in excluded — half of what the home prints up top. */
fun homeSeconds(blocks: List<Block>, repeatAll: Int): Long =
    homeWorkout(blocks, repeatAll, prepareMs = 0L).totalMs / 1000L

/**
 * Work sets in the whole workout — the other half, and the same number the timer will count you
 * through, because it is read off the same built workout rather than worked out again from blocks.
 */
fun homeSets(blocks: List<Block>, repeatAll: Int): Int =
    homeWorkout(blocks, repeatAll, prepareMs = 0L).intervals.maxOfOrNull { it.round } ?: 0

/** The section a fresh home starts from, and what "Add intervals" copies. */
fun basicBlock(workSec: Int, restSec: Int, rounds: Int): Block =
    Block(listOf(SeqInterval(Phase.WORK, workSec), SeqInterval(Phase.REST, restSec)), rounds)

/**
 * The classic home shape: one work, optionally one rest, and nothing else.
 *
 * Worth a name because it decides how the workout is *built*: a basic single section runs as a
 * [baseWorkout], anything else — two sections, or one section holding work/work/rest — runs as a
 * sequence. Both count the same thing either way, one round per work interval, so the "3 / 8" on the
 * timer means the same in both.
 */
val Block.isBasic: Boolean
    get() = items.firstOrNull()?.phase == Phase.WORK &&
        (items.size == 1 || (items.size == 2 && items[1].phase == Phase.REST))

/**
 * A repeated run of intervals — the editor's unit, and now the home's. Flat storage stays the source
 * of truth.
 *
 * [name] is what the home screen calls this block: "Warm-up", "Legs", "Cool down". It belongs to the
 * block rather than to the screen, so it survives a reorder, a delete of the block above it and a
 * relaunch — the three ways a name kept on the side would have gone missing.
 *
 * Defaulted, and last, so every existing `Block(items, repeat)` call site still reads the same. It
 * lands in equals() like any data-class field, which is correct here and needs saying: two blocks
 * with the same intervals but different names are not interchangeable, and `groupIntervals` builds
 * unnamed blocks either way, so nothing it compares changes meaning.
 */
data class Block(val items: List<SeqInterval>, val repeat: Int, val name: String = "")

fun flatten(blocks: List<Block>): List<SeqInterval> =
    blocks.flatMap { b -> List(b.repeat) { b.items }.flatten() }

/**
 * Two rests back to back is just one longer pause, so the editor steers around it (see
 * `Settings.noDoubleRest`).
 *
 * Always asked of a fully expanded sequence, which is what makes it catch the cases you can't see
 * by looking at one row: rest ending one group and opening the next, or a group whose own ×N wraps
 * its closing rest onto its opening one.
 */
fun backToBackRests(intervals: List<SeqInterval>): Int =
    intervals.zipWithNext().count { (a, b) -> a.phase == Phase.REST && b.phase == Phase.REST }

/**
 * The same count for [blocks] played [repeatAll] times, without building the expanded list — the
 * editor asks this of every row on every frame of a drag, and × 20 of a long sequence is a lot of
 * list to allocate for a question about two neighbours.
 *
 * Each pass has the same joins inside it; the only extra ones are where a pass ending in rest meets
 * the next pass opening with one.
 */
fun backToBackRests(blocks: List<Block>, repeatAll: Int): Int {
    val once = flatten(blocks)
    if (once.isEmpty()) return 0
    val passes = repeatAll.coerceAtLeast(1)
    val seam = if (passes > 1 && once.first().phase == Phase.REST && once.last().phase == Phase.REST) passes - 1 else 0
    return backToBackRests(once) * passes + seam
}

/**
 * Recover ×N grouping from a flat list, greedily from the left: at each position try pattern
 * lengths 1..4 and take whichever repeated pattern covers the most intervals.
 *
 * Where nothing repeats, a work interval keeps the rest that follows it. A block of one interval is
 * not what a group means to anyone reading it: Ladder climbs 20/30/40/50/60 and never repeats
 * anything, so it used to reopen as nine groups with "work 20" and "rest 20" in separate boxes. A
 * work and its recovery are one thing you do — the same shape the home's sections are built from.
 */
fun groupIntervals(flat: List<SeqInterval>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0
    while (i < flat.size) {
        var best = Block(listOf(flat[i]), 1)
        var covered = 1
        for (len in 1..4) {
            if (i + 2 * len > flat.size) break
            val pattern = flat.subList(i, i + len)
            var reps = 1
            while (i + (reps + 1) * len <= flat.size &&
                flat.subList(i + reps * len, i + (reps + 1) * len) == pattern
            ) reps++
            if (reps > 1 && reps * len > covered) {
                best = Block(pattern.toList(), reps)
                covered = reps * len
            }
        }
        // covered == 1 means nothing repeated here. Absorbing only rests is what keeps this from
        // eating the start of a pattern: a repeat always begins at the interval after the last rest,
        // so the scan at the next position still sees it whole.
        if (covered == 1) {
            var end = i + 1
            while (end < flat.size && flat[end].phase == Phase.REST) end++
            best = Block(flat.subList(i, end).toList(), 1)
            covered = end - i
        }
        blocks.add(best)
        i += covered
    }
    return blocks
}

private fun w(sec: Int) = SeqInterval(Phase.WORK, sec)
private fun r(sec: Int) = SeqInterval(Phase.REST, sec)

val BUILTIN_PRESETS: List<Preset> = listOf(
    Preset("Ladder", listOf(w(20), r(20), w(30), r(20), w(40), r(20), w(50), r(20), w(60))),
    Preset("Pyramid", listOf(w(20), r(15), w(40), r(15), w(60), r(15), w(40), r(15), w(20))),
    Preset("Tabata", (1..8).flatMap { listOf(w(20), r(10)) }),
    Preset("EMOM 10", (1..10).map { w(60) }),
)
