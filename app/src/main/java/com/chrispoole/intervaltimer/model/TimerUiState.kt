package com.chrispoole.intervaltimer.model

/** Immutable snapshot the service publishes and the UI collects. The single contract. */
data class TimerUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val phase: Phase = Phase.PREPARE,
    val remainingMs: Long = 0L,
    val intervalDurationMs: Long = 0L,
    val fraction: Float = 0f,
    val round: Int = 0,
    val totalRounds: Int = 0,
    /** Work sets in one pass, 0 when the workout runs once — the pips' row width. See [Workout]. */
    val roundsPerPass: Int = 0,
    val done: Boolean = false,
    /** Current section's name, "" when unnamed — rides under the phase label. */
    val label: String = "",
) {

    companion object {
        val Idle = TimerUiState()
    }
}

/** A settable duration: "45s" under a minute, "1:30" at a minute or more. */
fun secLabel(sec: Int): String =
    if (sec < 60) "${sec}s" else "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/**
 * Ceil-to-second clock so a fresh interval reads its full duration and only hits 0 at true zero.
 * Under a minute: bare seconds (16, 30). A minute or more: M:SS with no leading zero (1:20).
 */
fun formatMs(ms: Long): String {
    val totalSec = ((ms.coerceAtLeast(0L) + 999) / 1000).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min == 0) sec.toString() else "$min:${sec.toString().padStart(2, '0')}"
}
