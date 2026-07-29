package com.chrispoole.intervaltimer.wear.timer

// ponytail: this pure timer model is duplicated from the :app module. It's small, stable, and the
// drift-free logic is unit-tested on the phone side; a :shared library is the de-dup path if these
// two ever start changing together often. Kept separate here so the watch app has zero coupling to
// the phone build.

enum class Phase { PREPARE, WORK, REST, DONE }

data class Interval(val phase: Phase, val durationMs: Long, val round: Int = 0)

data class TimerProgress(
    val phase: Phase,
    val round: Int,
    val remainingMs: Long,
    val done: Boolean,
)

/** Drift-free clock: every reading derives from absolute active-elapsed time, never accumulated deltas. */
class Workout(val intervals: List<Interval>) {
    val totalMs: Long = intervals.sumOf { it.durationMs }

    fun progressAt(activeElapsedMs: Long): TimerProgress {
        if (intervals.isEmpty() || activeElapsedMs >= totalMs) {
            return TimerProgress(Phase.DONE, intervals.lastOrNull()?.round ?: 0, 0L, done = true)
        }
        val t = activeElapsedMs.coerceAtLeast(0L)
        var acc = 0L
        for (iv in intervals) {
            val end = acc + iv.durationMs
            if (t < end) return TimerProgress(iv.phase, iv.round, end - t, done = false)
            acc = end
        }
        return TimerProgress(Phase.DONE, intervals.last().round, 0L, done = true)
    }
}

fun baseWorkout(prepareMs: Long, workMs: Long, restMs: Long, rounds: Int): Workout {
    val list = buildList {
        if (prepareMs > 0) add(Interval(Phase.PREPARE, prepareMs))
        for (r in 1..rounds) {
            add(Interval(Phase.WORK, workMs, r))
            if (restMs > 0 && r < rounds) add(Interval(Phase.REST, restMs, r))
        }
    }
    return Workout(list)
}

data class SeqInterval(val phase: Phase, val durationSec: Int)

/** [repeatAll] plays the whole sequence through that many times; the phone syncs it in the JSON. */
data class Preset(val name: String, val intervals: List<SeqInterval>, val repeatAll: Int = 1)

/** The sequence as it actually plays: the whole of it, [repeatAll] times over. */
fun Preset.expanded(): List<SeqInterval> =
    if (repeatAll <= 1) intervals else List(repeatAll) { intervals }.flatten()

/**
 * What the watch will run. Mirrors the phone, including dropping a rest that would end the workout —
 * the same preset has to play the same on both, and it didn't: the watch was tacking a final rest
 * onto every sequence the phone ends cleanly.
 */
fun Preset.playbackIntervals(): List<SeqInterval> {
    val full = expanded()
    return if (full.size > 1 && full.last().phase == Phase.REST) full.dropLast(1) else full
}

fun Preset.toWorkout(prepareMs: Long = 5_000): Workout {
    // Must match the phone's Preset.toWorkout exactly, or the same synced preset runs a different
    // workout on each device: a rest after the very last work interval is dropped at run time.
    val seq = playbackIntervals()
    val list = buildList {
        if (prepareMs > 0) add(Interval(Phase.PREPARE, prepareMs))
        seq.forEachIndexed { i, s -> add(Interval(s.phase, s.durationSec * 1000L, i + 1)) }
    }
    return Workout(list)
}

private fun w(s: Int) = SeqInterval(Phase.WORK, s)
private fun r(s: Int) = SeqInterval(Phase.REST, s)

val BUILTIN_PRESETS: List<Preset> = listOf(
    Preset("Ladder", listOf(w(20), r(20), w(30), r(20), w(40), r(20), w(50), r(20), w(60))),
    Preset("Pyramid", listOf(w(20), r(15), w(40), r(15), w(60), r(15), w(40), r(15), w(20))),
    Preset("Tabata", (1..8).flatMap { listOf(w(20), r(10)) }),
    Preset("EMOM 10", (1..10).map { w(60) }),
)

fun formatMs(ms: Long): String {
    val total = ((ms.coerceAtLeast(0) + 999) / 1000).toInt()
    val min = total / 60
    val sec = total % 60
    return if (min == 0) sec.toString() else "$min:${sec.toString().padStart(2, '0')}"
}

data class WearUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val phase: Phase = Phase.PREPARE,
    val remainingMs: Long = 0L,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val done: Boolean = false,
) {
    companion object { val Idle = WearUiState() }
}
