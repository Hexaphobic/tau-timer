package com.chrispoole.intervaltimer.service

import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.TimerUiState
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotifTextTest {

    /**
     * publish() dedupes the ongoing notification on this string, and pause() freezes the clock at
     * the second already posted — so if the paused snapshot formats the same as the running one,
     * nothing is re-posted and the shade sits on a live-looking countdown that never moves.
     */
    @Test fun pausedReadsDifferentlyFromRunning() {
        assertNotEquals(
            notifText(TimerUiState(running = true, phase = Phase.WORK, remainingMs = 12_000)),
            notifText(TimerUiState(running = true, paused = true, phase = Phase.WORK, remainingMs = 12_000)),
        )
    }
}
