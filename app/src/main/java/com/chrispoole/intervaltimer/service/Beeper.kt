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
 * Loads the three cue tones and plays them as MEDIA, so they follow the user's active
 * media route (headphones, Bluetooth) exactly like the music does — not the phone's
 * alarm speaker. In-app volume is independent, so we don't fight the media volume.
 * A single MAY_DUCK focus request is held from the 5s warning through the GO tone, then
 * released, so the user's own music ducks for the cluster and returns between clusters.
 */
class Beeper(context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val playbackAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
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
        // The transition whoosh is the one that matters mid-set, so push it louder than the ticks.
        val gain = if (cue == Cue.GO) (v * 1.6f).coerceAtMost(1f) else v
        pool.play(id, gain, gain, 1, 0, 1f)
    }

    fun release() {
        duckEnd()
        pool.release()
    }
}
