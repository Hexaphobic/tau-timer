package com.chrispoole.intervaltimer.model

/** Which kind of interval is active. PREPARE is the one-time get-ready lead-in. */
enum class Phase { PREPARE, WORK, REST, DONE }

/** One block of the workout. [round] is 1-based for WORK/REST, 0 for PREPARE. */
data class Interval(val phase: Phase, val durationMs: Long, val round: Int = 0)

/** Snapshot of where the clock is at a given moment. */
data class TimerProgress(
    val phase: Phase,
    val round: Int,
    val remainingMs: Long,
    val intervalDurationMs: Long,
    val done: Boolean,
) {
    /** 0f at interval start, 1f at interval end — drives the aura + perimeter stroke. */
    val fraction: Float
        get() = if (intervalDurationMs <= 0) 1f
        else 1f - (remainingMs.toFloat() / intervalDurationMs.toFloat())
}

/**
 * Pure, device-independent interval clock.
 *
 * Every reading derives from a single absolute `activeElapsedMs` (milliseconds of
 * *running* time since the workout started, excluding paused time) mapped against
 * the fixed interval durations. Nothing accumulates per-tick deltas, so sampling at
 * any moment yields the exact same answer as any other path to that moment — this is
 * what makes the timer drift-free. The service feeds it `elapsedRealtime()`-based
 * active time; this class has no Android dependencies so it is unit-testable on the JVM.
 */
class Workout(val intervals: List<Interval>) {

    val totalMs: Long = intervals.sumOf { it.durationMs }

    fun progressAt(activeElapsedMs: Long): TimerProgress {
        if (intervals.isEmpty() || activeElapsedMs >= totalMs) {
            val last = intervals.lastOrNull()
            return TimerProgress(
                phase = Phase.DONE,
                round = last?.round ?: 0,
                remainingMs = 0L,
                intervalDurationMs = last?.durationMs ?: 0L,
                done = true,
            )
        }
        val t = activeElapsedMs.coerceAtLeast(0L)
        var acc = 0L
        for (i in intervals.indices) {
            val iv = intervals[i]
            val end = acc + iv.durationMs
            if (t < end) {
                return TimerProgress(
                    phase = iv.phase,
                    round = iv.round,
                    remainingMs = end - t,
                    intervalDurationMs = iv.durationMs,
                    done = false,
                )
            }
            acc = end
        }
        // Unreachable given the totalMs guard above, but keep it total.
        val last = intervals.last()
        return TimerProgress(Phase.DONE, last.round, 0L, last.durationMs, done = true)
    }
}

/**
 * Base mode: a single PREPARE lead-in, then [rounds] of WORK, with REST between rounds
 * (no trailing rest after the final round). Sequence mode will build the interval list
 * directly instead of going through here.
 */
fun baseWorkout(
    prepareMs: Long,
    workMs: Long,
    restMs: Long,
    rounds: Int,
): Workout {
    val list = buildList {
        if (prepareMs > 0) add(Interval(Phase.PREPARE, prepareMs))
        for (r in 1..rounds) {
            add(Interval(Phase.WORK, workMs, r))
            if (restMs > 0 && r < rounds) add(Interval(Phase.REST, restMs, r))
        }
    }
    return Workout(list)
}
