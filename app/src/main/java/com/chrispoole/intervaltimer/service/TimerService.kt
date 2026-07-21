package com.chrispoole.intervaltimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.chrispoole.intervaltimer.MainActivity
import com.chrispoole.intervaltimer.Settings
import com.chrispoole.intervaltimer.model.TimerUiState
import com.chrispoole.intervaltimer.model.Workout
import com.chrispoole.intervaltimer.model.formatMs
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
 * Owns the authoritative clock. Runs as a foregroundServiceType=specialUse service so the
 * workout keeps ticking with the screen off / app backgrounded, and holds a PARTIAL_WAKE_LOCK
 * (an FGS alone does NOT keep the CPU awake). All timing derives from elapsedRealtime() against
 * a stored start anchor, so Activity recreation (fold/rotate/cover-switch) never disturbs it.
 * The UI binds, reads [state], and sends start/pause/resume/stop.
 */
class TimerService : Service() {

    inner class LocalBinder : Binder() {
        val service: TimerService get() = this@TimerService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var beeper: Beeper? = null

    private val _state = MutableStateFlow(TimerUiState.Idle)
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    private var workout: Workout? = null
    private var totalRounds = 0
    private var startElapsed = 0L        // elapsedRealtime that maps to activeElapsed == 0
    private var pausedActive = 0L        // frozen active-elapsed while paused
    private var paused = false
    private var lastNotifText = ""

    private var cues: List<ScheduledCue> = emptyList()
    private var cueIdx = 0

    private data class ScheduledCue(val atMs: Long, val cue: Cue)

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Settings.init(this)
        beeper = Beeper(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        // If the user turned off background running, closing the app ends the workout.
        if (!Settings.runInBackground) stop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly after startForegroundService() to avoid an ANR.
        // The specialUse FGS-type constant is API 34; below that, start untyped (the manifest
        // foregroundServiceType, where supported, still applies).
        val notif = buildNotification(TimerUiState.Idle)
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
        lastNotifText = ""
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
        beeper?.duckEnd()
        publish(w)
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
        beeper?.duckEnd()
        _state.value = TimerUiState.Idle
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun activeElapsed(): Long =
        if (paused) pausedActive else SystemClock.elapsedRealtime() - startElapsed

    /** All cue instants across the whole timeline: 5/3/2/1s before each boundary + GO at it. */
    private fun buildCues(w: Workout): List<ScheduledCue> {
        val list = mutableListOf<ScheduledCue>()
        var end = 0L
        for (iv in w.intervals) {
            end += iv.durationMs
            list += ScheduledCue(end - 5_000, Cue.WARN)
            list += ScheduledCue(end - 3_000, Cue.TICK)
            list += ScheduledCue(end - 2_000, Cue.TICK)
            list += ScheduledCue(end - 1_000, Cue.TICK)
            list += ScheduledCue(end, Cue.GO)
        }
        return list.filter { it.atMs >= 0 }.sortedBy { it.atMs }
    }

    private fun fireDueCues(nowMs: Long) {
        while (cueIdx < cues.size && cues[cueIdx].atMs <= nowMs) {
            when (val cue = cues[cueIdx].cue) {
                // 5s warning beeps over the music untouched; ducking only kicks in at the final 3.
                Cue.WARN -> beeper?.play(cue)
                Cue.TICK -> { beeper?.duckStart(); beeper?.play(cue) }
                Cue.GO -> { beeper?.play(Cue.GO); beeper?.duckEnd() }
            }
            cueIdx++
        }
    }

    private fun loop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            val w = workout ?: return@launch
            while (isActive && !paused) {
                val now = activeElapsed()
                fireDueCues(now)
                val done = publish(w)
                if (done) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // End the started-service lifetime so a finished workout doesn't linger forever.
                    // A bound activity keeps the instance (and the Done state) alive until it unbinds.
                    stopSelf()
                    break
                }
                delay(33L) // ~30fps state for smooth fraction; notification throttled to 1/sec, cues fire on overshoot
            }
        }
    }

    /** Emits the current snapshot; returns true when the workout is done. */
    private fun publish(w: Workout): Boolean {
        val p = w.progressAt(activeElapsed())
        val s = TimerUiState(
            running = true,
            paused = paused,
            phase = p.phase,
            remainingMs = p.remainingMs,
            intervalDurationMs = p.intervalDurationMs,
            fraction = p.fraction,
            round = p.round,
            totalRounds = totalRounds,
            done = p.done,
        )
        _state.value = s
        val text = if (s.done) "Done" else formatMs(s.remainingMs)
        if (text != lastNotifText) {
            lastNotifText = text
            notify(buildNotification(s))
        }
        return p.done
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntervalTimer::clock").also { wakeLock = it }
        if (!wl.isHeld) wl.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun buildNotification(s: TimerUiState): Notification {
        ensureChannel()
        val text = when {
            !s.running -> "Ready"
            s.done -> "Done"
            s.phase.name == "PREPARE" -> "Get ready · ${formatMs(s.remainingMs)}"
            else -> "${s.phase.name.lowercase().replaceFirstChar { it.uppercase() }} · ${formatMs(s.remainingMs)}"
        }
        // Tapping the notification reopens the app, which re-attaches to the live workout.
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Interval Timer")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notify(n: Notification) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        isRunning = false
        tickJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        beeper?.release()
        super.onDestroy()
    }

    companion object {
        /**
         * Whether a service instance is alive. The UI needs this to decide whether to re-attach:
         * bindService()'s return value can NOT be used for that — it returns true whenever the
         * component merely resolves, even with no running service.
         */
        @Volatile var isRunning = false
            private set

        private const val CHANNEL_ID = "timer"
        private const val NOTIF_ID = 1
        // ponytail: 4h ceiling covers any real workout; re-acquire per interval only if someone runs marathons.
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
    }
}
