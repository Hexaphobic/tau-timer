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
    val list = buildList {
        if (prepareMs > 0) add(Interval(Phase.PREPARE, prepareMs))
        intervals.forEachIndexed { i, s -> add(Interval(s.phase, s.durationSec * 1000L, i + 1)) }
    }
    return Workout(list)
}

private fun w(sec: Int) = SeqInterval(Phase.WORK, sec)
private fun r(sec: Int) = SeqInterval(Phase.REST, sec)

val BUILTIN_PRESETS: List<Preset> = listOf(
    Preset("Ladder", listOf(w(20), r(20), w(30), r(20), w(40), r(20), w(50), r(20), w(60))),
    Preset("Pyramid", listOf(w(20), r(15), w(40), r(15), w(60), r(15), w(40), r(15), w(20))),
    Preset("Tabata", (1..8).flatMap { listOf(w(20), r(10)) }),
    Preset("EMOM 10", (1..10).map { w(60) }),
)
