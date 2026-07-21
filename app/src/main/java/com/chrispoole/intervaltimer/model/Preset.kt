package com.chrispoole.intervaltimer.model

/** One block in a sequence preset: WORK or REST for a number of seconds. */
data class SeqInterval(val phase: Phase, val durationSec: Int)

/** A named, ordered sequence of intervals. */
data class Preset(
    val name: String,
    val intervals: List<SeqInterval>,
)

/** Build a runnable Workout: a PREPARE lead-in, then the sequence (round = 1-based position). */
fun Preset.toWorkout(prepareMs: Long = 5_000): Workout {
    // A rest after the very last work interval is pointless — drop it at run time so presets
    // can stay as clean (work, rest) × N groups.
    val seq = if (intervals.size > 1 && intervals.last().phase == Phase.REST) intervals.dropLast(1) else intervals
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
