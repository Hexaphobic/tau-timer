package com.chrispoole.intervaltimer.wear.timer

import android.content.Context
import android.media.AudioAttributes
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

    // The no-attributes overload is tagged USAGE_UNKNOWN, which the framework drops under Battery
    // Saver and filters under DND — and vibration is this app's only output, so a long workout that
    // slipped into low-power mode ran all the way through with no boundary cues while still holding
    // the wake lock. Alarm usage survives that; the trade is it's now subject to total-silence
    // suppression instead. That's the right way round for a workout timer, so don't swap it back.
    // Built once, not per call: buzz() fires four times per interval.
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun buzz(kind: Buzz) {
        val effect = when (kind) {
            Buzz.TICK -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            Buzz.WORK -> VibrationEffect.createWaveform(longArrayOf(0, 180, 90, 180), -1)   // strong double = go work
            Buzz.REST -> VibrationEffect.createWaveform(longArrayOf(0, 420), -1)            // one long soft = rest
            Buzz.DONE -> VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 200), -1)
        }
        // Deprecated at 33 in favour of the VibrationAttributes overload, which is itself 33+ — so at
        // this module's minSdk 30 it is the only way to attach a usage at all. Suppressed rather than
        // branched: the framework maps USAGE_ALARM straight through, so a second path would be extra
        // code for identical behaviour.
        @Suppress("DEPRECATION") vib.vibrate(effect, attrs)
    }
}
