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
 * The accepted cost of MEDIA over ALARM: media volume at zero means no cues — which is the user
 * saying "no sounds", the same answer every other app gives them. Losing the route mid-workout
 * isn't a cost at all; the framework rehomes the stream to the speaker rather than silencing it.
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

    private var ducking = false

    // The service is created at workout launch, not app launch, so the decode starts only moments
    // before the first cue is due and the very first tone of a workout could lose that race and never
    // sound. With the default 5s prepare that first cue is now the tick at 2000ms — buildCues drops
    // the warn that used to land on atMs == 0 — which is far more headroom than it had, but not a
    // guarantee on a cold device. One slot, not a queue: only the earliest cue can lose, and by the
    // time a later decode landed the workout would have moved on.
    private var missed: Cue? = null

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            // Any successful decode is a chance to retry: if the tone we owe is still the one that
            // hasn't landed, play() just re-stashes it and the next callback tries again.
            if (status == 0) synchronized(this) { missed?.let { missed = null; play(it) } }
        }
        warnId = pool.load(context, R.raw.warn5, 1)
        tickId = pool.load(context, R.raw.tick, 1)
        goId = pool.load(context, R.raw.go, 1)
    }

    // duckStart runs on the tick dispatcher, duckEnd on both it and the main thread (pause/stop).
    // Unsynchronised, the two can interleave so that nobody abandons focus and the user's music
    // stays ducked for good.
    @Synchronized
    fun duckStart() {
        if (Settings.muted) return
        if (!ducking) {
            am.requestAudioFocus(focusRequest)
            ducking = true
        }
    }

    @Synchronized
    fun duckEnd() {
        if (ducking) {
            am.abandonAudioFocusRequest(focusRequest)
            ducking = false
        }
    }

    // Shares release()'s monitor: play() runs on the tick dispatcher, release() on the main thread
    // via onDestroy, so unsynchronised the pool could be freed between the id lookup and the call
    // into it.
    @Synchronized
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
        // play() returns 0 when the sample isn't loaded yet. It also returns 0 on a released pool or
        // with no free channel, neither of which happens here — re-stashing on those would be
        // harmless anyway, since nothing would ever complete a load to replay it.
        if (pool.play(id, gain, gain, 1, 0, 1f) == 0) missed = cue
    }

    @Synchronized
    fun release() {
        duckEnd()
        pool.release()
    }
}
