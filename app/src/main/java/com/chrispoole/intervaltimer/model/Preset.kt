package com.chrispoole.intervaltimer.model

/** One block in a sequence preset: WORK or REST for a number of seconds. */
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

/** Playing time in seconds, repeats included. */
fun Preset.totalSec(): Int = expanded().sumOf { it.durationSec }

/** Build a runnable Workout: a PREPARE lead-in, then the sequence (round = 1-based position). */
fun Preset.toWorkout(prepareMs: Long = 5_000): Workout {
    val full = expanded()
    // A rest after the very last work interval is pointless — drop it at run time so presets
    // can stay as clean (work, rest) × N groups. Dropped after the repeats are expanded, so the
    // rest *between* two passes survives; only the one that would end the workout goes.
    val seq = if (full.size > 1 && full.last().phase == Phase.REST) full.dropLast(1) else full
    val list = buildList {
        if (prepareMs > 0) add(Interval(Phase.PREPARE, prepareMs))
        seq.forEachIndexed { i, s -> add(Interval(s.phase, s.durationSec * 1000L, i + 1)) }
    }
    return Workout(list)
}

/** A repeated run of intervals — the editor's unit. Flat storage stays the source of truth. */
data class Block(val items: List<SeqInterval>, val repeat: Int)

fun flatten(blocks: List<Block>): List<SeqInterval> =
    blocks.flatMap { b -> List(b.repeat) { b.items }.flatten() }

/** What [blocks] will actually play as, every repeat — group and overall — expanded. */
fun expand(blocks: List<Block>, repeatAll: Int = 1): List<SeqInterval> {
    val once = flatten(blocks)
    return if (repeatAll <= 1) once else List(repeatAll) { once }.flatten()
}

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

fun hasBackToBackRest(intervals: List<SeqInterval>): Boolean = backToBackRests(intervals) > 0

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
 * lengths 1..4 and take whichever repeated pattern covers the most intervals. Non-repeating
 * stretches fall out as single-interval ×1 blocks.
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
