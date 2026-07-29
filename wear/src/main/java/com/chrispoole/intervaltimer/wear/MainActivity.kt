package com.chrispoole.intervaltimer.wear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.chrispoole.intervaltimer.wear.timer.Phase
import com.chrispoole.intervaltimer.wear.timer.Preset
import com.chrispoole.intervaltimer.wear.timer.WearTimerService
import com.chrispoole.intervaltimer.wear.timer.WearUiState
import com.chrispoole.intervaltimer.wear.timer.Workout
import com.chrispoole.intervaltimer.wear.timer.baseWorkout
import com.chrispoole.intervaltimer.wear.timer.expanded
import com.chrispoole.intervaltimer.wear.timer.formatMs
import com.chrispoole.intervaltimer.wear.timer.toWorkout
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<WearTimerService?>(null)
    private var pending: Workout? = null

    private var bound = false
    /** True while re-attaching to a workout already in progress, so we don't flash the home screen. */
    private var attaching by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as WearTimerService.LocalBinder).service
            service = svc
            attaching = false
            pending?.let { svc.start(it); pending = null }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            attaching = false
        }
    }

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PresetRepo.init(this)
        if (Build.VERSION.SDK_INT >= 33) requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        pullPhonePresets()
        reattachToRunningWorkout()

        setContent {
            MaterialTheme {
                val ui = service?.state?.collectAsStateWithLifecycle()?.value ?: WearUiState.Idle
                var showPresets by remember { mutableStateOf(false) }
                // Safety net: the re-attach placeholder can never be terminal.
                if (attaching) {
                    LaunchedEffect(Unit) { delay(800); attaching = false }
                }
                when {
                    // Reopening mid-workout lands back on the live timer, never the home screen.
                    attaching && service == null -> Box(Modifier.fillMaxSize().background(Color.Black))
                    ui.running -> RunningScreen(ui, onPause = { service?.pause() }, onResume = { service?.resume() }, onEnd = { endWorkout() })
                    showPresets -> PresetListScreen(onStart = { launchWorkout(it.toWorkout()) }, onBack = { showPresets = false })
                    else -> HomeScreen(onStart = { launchWorkout(it) }, onPresets = { showPresets = true })
                }
            }
        }
    }

    /** One-time pull of the current preset list already sitting in the Data Layer. */
    private fun pullPhonePresets() {
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { buffer ->
            for (item in buffer) {
                if (item.uri.path == "/presets") {
                    val map = DataMapItem.fromDataItem(item).dataMap
                    map.getString("json")?.let {
                        PresetRepo.setFromPhone(it, map.getStringArrayList("hidden") ?: arrayListOf(), this)
                    }
                }
            }
            buffer.release()
        }
    }

    /**
     * Attach only if a workout is genuinely already running. Gate on WearTimerService.isRunning,
     * NOT bindService()'s return value — that returns true whenever the component merely resolves,
     * which would leave us waiting forever on a connection that never arrives.
     */
    private fun reattachToRunningWorkout() {
        if (bound || !WearTimerService.isRunning) return
        attaching = true
        bound = bindService(Intent(this, WearTimerService::class.java), connection, 0)
        if (!bound) attaching = false
    }

    private fun launchWorkout(workout: Workout) {
        val svc = service
        if (svc != null) {
            svc.start(workout)
        } else {
            pending = workout
            val intent = Intent(this, WearTimerService::class.java)
            ContextCompat.startForegroundService(this, intent)
            if (!bound) bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun endWorkout() {
        service?.stop()
        releaseBinding()
        service = null
        pending = null
        attaching = false
    }

    private fun releaseBinding() {
        if (bound) {
            runCatching { unbindService(connection) }
            bound = false
        }
    }

    override fun onDestroy() {
        releaseBinding()
        super.onDestroy()
    }
}

/** Default screen: a quick Work / Rest / Rounds timer, mirroring the phone. Presets are one tap away. */
@Composable
private fun HomeScreen(onStart: (Workout) -> Unit, onPresets: () -> Unit) {
    var workSec by remember { mutableStateOf(30) }
    var restSec by remember { mutableStateOf(15) }
    var rounds by remember { mutableStateOf(8) }
    // Plain centered column: the three steppers + Start sit in one screenful; Presets is just below,
    // reached with a short scroll. No title — the controls are the whole point.
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        WearStepper("Work", "${workSec}s", { workSec = (workSec - 5).coerceAtLeast(5) }, { workSec += 5 })
        WearStepper("Rest", "${restSec}s", { restSec = (restSec - 5).coerceAtLeast(0) }, { restSec += 5 })
        WearStepper("Rounds", "$rounds", { rounds = (rounds - 1).coerceAtLeast(1) }, { rounds += 1 })
        Spacer(Modifier.height(2.dp))
        WearPill("Start", { onStart(baseWorkout(prepareMs = 5_000, workMs = workSec * 1000L, restMs = restSec * 1000L, rounds = rounds)) }, Color(0xFF22E06A))
        WearPill("Presets", onPresets, Color.White)
    }
}

@Composable
private fun WearStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    // Not fillMaxWidth — the cluster wraps its content and the parent centres it. The fixed-width,
    // right-aligned label keeps the −/value/+ columns lined up across all three rows.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        Spacer(Modifier.width(6.dp))
        StepButton("−", onMinus)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(46.dp))
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 22.sp, color = Color.White)
    }
}

/** App-style tinted glass pill (green Start, white Presets) — wraps its text, not full width. */
@Composable
private fun WearPill(text: String, onClick: () -> Unit, tint: Color) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.55f), shape)
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PresetListScreen(onStart: (Preset) -> Unit, onBack: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Presets", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
        items(PresetRepo.all()) { preset ->
            Chip(
                label = { Text(preset.name, maxLines = 1) },
                secondaryLabel = { Text(preset.summary(), maxLines = 1) },
                onClick = { onStart(preset) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                label = { Text("‹ Back") },
                onClick = onBack,
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

private fun Preset.summary(): String {
    val seq = expanded()
    val total = seq.sumOf { it.durationSec }
    return "${seq.size} · ${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Deliberate hold to pause. Shorter than the phone's 400ms — a watch face gets brushed less. */
private const val HOLD_TO_PAUSE_MS = 200L

private fun phaseColor(phase: Phase): Color = when (phase) {
    Phase.PREPARE -> Color(0xFF7C3AED)
    Phase.WORK -> Color(0xFF16A34A)
    Phase.REST -> Color(0xFF2563EB)
    Phase.DONE -> Color(0xFF374151)
}

@Composable
private fun RunningScreen(ui: WearUiState, onPause: () -> Unit, onResume: () -> Unit, onEnd: () -> Unit) {
    val label = when {
        ui.done -> "Done"
        ui.phase == Phase.PREPARE -> "Ready"
        ui.phase == Phase.WORK -> "Work"
        ui.phase == Phase.REST -> "Rest"
        else -> ""
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Content layer — blurs behind the pause overlay so the layered text stops competing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (ui.paused) 18.dp else 0.dp)
                .background(phaseColor(ui.phase))
                .pointerInput(ui.done, ui.paused) {
                    if (ui.done || ui.paused) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // Null means the timeout won: still held, so pause. A lift or a cancel
                        // returns Unit and is ignored — a sleeve brush can't stop a set.
                        val lifted = withTimeoutOrNull(HOLD_TO_PAUSE_MS) { waitForUpOrCancellation(); Unit }
                        if (lifted == null) {
                            onPause()
                            waitForUpOrCancellation()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (ui.round > 0) Text("${ui.round} / ${ui.totalRounds}", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = formatMs(ui.remainingMs),
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        if (ui.done) {
            PillButton("Done", onEnd, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        } else if (ui.paused) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Paused", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Resume", onResume)
                    Spacer(Modifier.height(8.dp))
                    PillButton("End", onEnd, tint = Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Box(
        modifier
            .clickable { onClick() }
            .background(tint.copy(alpha = 0.13f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
