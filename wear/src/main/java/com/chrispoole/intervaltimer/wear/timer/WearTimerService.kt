package com.chrispoole.intervaltimer.wear.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the workout clock on the watch. Foreground service so it keeps running and buzzing when the
 * app isn't in front / the screen is off, holding a partial wake lock. Vibrates cues — never plays
 * audio. Mirrors the phone's drift-free architecture.
 */
class WearTimerService : Service() {

    inner class LocalBinder : Binder() {
        val service: WearTimerService get() = this@WearTimerService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrations: Vibrations? = null

    private val _state = MutableStateFlow(WearUiState.Idle)
    val state: StateFlow<WearUiState> = _state.asStateFlow()

    private var workout: Workout? = null
    private var totalRounds = 0
    private var startElapsed = 0L
    private var pausedActive = 0L
    // Written on the main thread (pause/resume), read on the tick dispatcher. Volatile so the tick
    // sees the flip — and, since pausedActive is written first, sees that too.
    @Volatile private var paused = false
    private var lastNotif = ""

    private var cues: List<Cue> = emptyList()
    private var cueIdx = 0

    private data class Cue(val atMs: Long, val buzz: Buzz)

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        vibrations = Vibrations(this)
        // Once per service, not once per notification — buildNotification runs every second.
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // specialUse FGS-type constant is API 34; below that (Wear OS 3/4) start untyped.
        val notif = buildNotification(WearUiState.Idle)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY
    }

    fun start(w: Workout) {
        workout = w
        totalRounds = w.intervals.maxOfOrNull { it.round } ?: 0
        startElapsed = SystemClock.elapsedRealtime()
        pausedActive = 0L
        paused = false
        lastNotif = ""
        cues = buildCues(w)
        cueIdx = 0
        acquireWakeLock()
        loop()
    }

    fun pause() {
        val w = workout ?: return
        if (paused) return
        pausedActive = activeElapsed()
        paused = true
        tickJob?.cancel()
        publish(w)
        // Nothing to keep the (much smaller) watch CPU awake for while frozen. resume() re-acquires.
        releaseWakeLock()
    }

    fun resume() {
        val w = workout ?: return
        if (!paused) return
        startElapsed = SystemClock.elapsedRealtime() - pausedActive
        paused = false
        acquireWakeLock()
        loop()
    }

    fun stop() {
        tickJob?.cancel()
        workout = null
        paused = false
        _state.value = WearUiState.Idle
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun activeElapsed(): Long =
        if (paused) pausedActive else SystemClock.elapsedRealtime() - startElapsed

    /** Ticks at 3/2/1s before each boundary, then a transition buzz keyed to the phase that's starting. */
    private fun buildCues(w: Workout): List<Cue> {
        val out = mutableListOf<Cue>()
        var end = 0L
        for (i in w.intervals.indices) {
            end += w.intervals[i].durationMs
            out += Cue(end - 3_000, Buzz.TICK)
            out += Cue(end - 2_000, Buzz.TICK)
            out += Cue(end - 1_000, Buzz.TICK)
            val buzz = when (w.intervals.getOrNull(i + 1)?.phase) {
                Phase.WORK, Phase.PREPARE -> Buzz.WORK
                Phase.REST -> Buzz.REST
                else -> Buzz.DONE // no next interval => workout finished
            }
            out += Cue(end, buzz)
        }
        return out.filter { it.atMs >= 0 }.sortedBy { it.atMs }
    }

    private fun fireDueCues(nowMs: Long) {
        while (cueIdx < cues.size && cues[cueIdx].atMs <= nowMs) {
            vibrations?.buzz(cues[cueIdx].buzz)
            cueIdx++
        }
    }

    private fun loop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            val w = workout ?: return@launch
            while (isActive && !paused) {
                fireDueCues(activeElapsed())
                if (publish(w)) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // End the started-service lifetime, matching the phone: dropping foreground
                    // status alone leaves the instance (and isRunning) alive forever, so the next
                    // launch would re-attach to a stale Done screen. A bound activity keeps the
                    // instance until it unbinds, so tapping Done still works.
                    stopSelf()
                    break
                }
                delay(40L)
            }
        }
    }

    /** Emits the snapshot; returns true when done. */
    private fun publish(w: Workout): Boolean {
        val p = w.progressAt(activeElapsed())
        val s = WearUiState(
            running = true,
            paused = paused,
            phase = p.phase,
            remainingMs = p.remainingMs,
            round = p.round,
            totalRounds = totalRounds,
            done = p.done,
        )
        // tickJob?.cancel() is cooperative, so a tick that read `paused` as false before pause()
        // flipped it can still land here afterwards. Dropping that stale snapshot is what stops it
        // overwriting the paused one and stranding the UI on a frozen, running-looking timer.
        if (paused && !s.paused) return false
        _state.value = s
        // Nothing to notify on the last tick: loop() tears the notification down immediately after.
        if (!p.done) {
            val text = formatMs(p.remainingMs)
            if (text != lastNotif) {
                lastNotif = text
                notify(buildNotification(s))
            }
        }
        return p.done
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tau::wear").also { wakeLock = it }
        if (!wl.isHeld) wl.acquire(4L * 60 * 60 * 1000)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(s: WearUiState): Notification {
        val text = when {
            !s.running -> "Ready"
            s.done -> "Done"
            else -> "${s.phase.name.lowercase().replaceFirstChar { it.uppercase() }} · ${formatMs(s.remainingMs)}"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Tau")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notify(n: Notification) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    override fun onDestroy() {
        isRunning = false
        tickJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        /**
         * Whether a service instance is alive. bindService()'s return value can NOT be used for
         * this — it returns true whenever the component merely resolves, live service or not.
         */
        @Volatile var isRunning = false
            private set

        private const val CHANNEL_ID = "wear_timer"
        private const val NOTIF_ID = 1
    }
}
