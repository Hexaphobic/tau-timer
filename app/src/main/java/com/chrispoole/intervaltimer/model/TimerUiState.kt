package com.chrispoole.intervaltimer.model

/** Immutable snapshot the service publishes and the UI collects. The single contract. */
data class TimerUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val phase: Phase = Phase.PREPARE,
    val remainingMs: Long = 0L,
    val fraction: Float = 0f,
    val round: Int = 0,
    val totalRounds: Int = 0,
    val done: Boolean = false,
) {
    companion object {
        val Idle = TimerUiState()
    }
}

/** Ceil-to-second MM:SS so a fresh interval reads its full duration and only hits 00:00 at true zero. */
fun formatMs(ms: Long): String {
    val totalSec = ((ms.coerceAtLeast(0L) + 999) / 1000).toInt()
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
