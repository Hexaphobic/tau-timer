package com.chrispoole.intervaltimer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import com.chrispoole.intervaltimer.R
import com.chrispoole.intervaltimer.Settings

enum class Cue { WARN, TICK, GO }

/**
 * Loads the three cue tones and plays them on the ALARM stream (so they cut through
 * gym music and sound in Do-Not-Disturb), and transiently ducks the user's own music
 * only while a countdown cluster is sounding — one MAY_DUCK focus request held from the
 * 5s warning through the GO tone, then released, so music returns between clusters.
 */
class Beeper(context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val playbackAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val focusAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(playbackAttrs)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(focusAttrs)
        .setWillPauseWhenDucked(false)
        .build()

    private var warnId = 0
    private var tickId = 0
    private var goId = 0

    @Volatile private var ready = false
    private var ducking = false

    init {
        var pending = 3
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0 && --pending == 0) ready = true
        }
        warnId = pool.load(context, R.raw.warn5, 1)
        tickId = pool.load(context, R.raw.tick, 1)
        goId = pool.load(context, R.raw.go, 1)
    }

    val isReady: Boolean get() = ready

    fun duckStart() {
        if (Settings.muted) return
        if (!ducking) {
            am.requestAudioFocus(focusRequest)
            ducking = true
        }
    }

    fun duckEnd() {
        if (ducking) {
            am.abandonAudioFocusRequest(focusRequest)
            ducking = false
        }
    }

    fun play(cue: Cue) {
        if (Settings.muted) return
        val v = Settings.volume
        val id = when (cue) {
            Cue.WARN -> warnId
            Cue.TICK -> tickId
            Cue.GO -> goId
        }
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() {
        duckEnd()
        pool.release()
    }
}
