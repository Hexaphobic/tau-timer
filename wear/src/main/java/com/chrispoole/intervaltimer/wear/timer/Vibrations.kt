package com.chrispoole.intervaltimer.wear.timer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The distinct haptic cues. WORK vs REST feel different so you know which is starting without looking. */
enum class Buzz { TICK, WORK, REST, DONE }

class Vibrations(context: Context) {
    private val vib: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    fun buzz(kind: Buzz) {
        val effect = when (kind) {
            Buzz.TICK -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            Buzz.WORK -> VibrationEffect.createWaveform(longArrayOf(0, 180, 90, 180), -1)   // strong double = go work
            Buzz.REST -> VibrationEffect.createWaveform(longArrayOf(0, 420), -1)            // one long soft = rest
            Buzz.DONE -> VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 200), -1)
        }
        vib.vibrate(effect)
    }
}
