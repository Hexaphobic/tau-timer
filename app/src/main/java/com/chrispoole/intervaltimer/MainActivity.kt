package com.chrispoole.intervaltimer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrispoole.intervaltimer.model.Language
import com.chrispoole.intervaltimer.model.Numbers
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.Preset
import com.chrispoole.intervaltimer.model.SeqInterval
import com.chrispoole.intervaltimer.model.TimerUiState
import com.chrispoole.intervaltimer.model.Workout
import com.chrispoole.intervaltimer.model.baseWorkout
import com.chrispoole.intervaltimer.model.formatMs
import com.chrispoole.intervaltimer.model.secLabel
import com.chrispoole.intervaltimer.model.toWorkout
import com.chrispoole.intervaltimer.service.TimerService
import com.chrispoole.intervaltimer.ui.AuraBackground
import com.chrispoole.intervaltimer.ui.DangerRed
import com.chrispoole.intervaltimer.ui.DoneGray
import com.chrispoole.intervaltimer.ui.EditorScreen
import com.chrispoole.intervaltimer.ui.PrepColor
import com.chrispoole.intervaltimer.ui.RestColor
import com.chrispoole.intervaltimer.ui.WorkColor
import com.chrispoole.intervaltimer.ui.GlassCircle
import com.chrispoole.intervaltimer.ui.GlassFill
import com.chrispoole.intervaltimer.ui.GlassPill
import com.chrispoole.intervaltimer.ui.HomeBackground
import com.chrispoole.intervaltimer.ui.noRippleClickable
import com.chrispoole.intervaltimer.ui.Palette
import com.chrispoole.intervaltimer.ui.PresetsScreen
import com.chrispoole.intervaltimer.ui.glassBorder
import com.chrispoole.intervaltimer.ui.SplitProgress

// Grayscale chrome — colour is reserved for communicating the interval phase in the timer.
private val MonoScheme = darkColorScheme(
    primary = Color(0xFFEDEDED),
    onPrimary = Color(0xFF141414),
    secondary = Color(0xFFB8B8B8),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF2A2A2A),
)

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<TimerService?>(null)
    private var pending: Workout? = null

    private var bound = false
    /** True while we're re-attaching to a workout already in progress, so we don't flash the setup screen. */
    private var attaching by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as TimerService.LocalBinder).service
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
        Settings.init(this)
        PresetStore.init(this)
        enableEdgeToEdge()
        hideSystemBars()
        // Only ask when we don't already have it: onCreate runs again on every Activity recreation,
        // and re-launching the request popped the system dialog over a live workout.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        reattachToRunningWorkout()

        setContent {
            MaterialTheme(colorScheme = MonoScheme) {
                val ui = service?.state?.collectAsStateWithLifecycle()?.value ?: TimerUiState.Idle
                var screen by remember { mutableStateOf("setup") }
                var editIndex by remember { mutableStateOf<Int?>(null) }
                // A sequence carried in from the main screen's steppers (not a saved preset yet).
                var seeded by remember { mutableStateOf<Preset?>(null) }
                // Safety net: the re-attach placeholder can never be terminal, whatever goes wrong.
                if (attaching) {
                    LaunchedEffect(Unit) { delay(800); attaching = false }
                }
                when {
                    // Reopening mid-workout lands straight back on the live timer, never the setup screen.
                    attaching && service == null -> Box(Modifier.fillMaxSize().background(Color.Black))
                    ui.running -> TimerScreen(
                        ui = ui,
                        onPause = { service?.pause() },
                        onResume = { service?.resume() },
                        onEnd = { endWorkout() },
                    )
                    screen == "settings" -> SettingsScreen(onBack = { screen = "setup" })
                    screen == "presets" -> PresetsScreen(
                        onBack = { screen = "setup" },
                        onStart = { launchWorkout(it.toWorkout(Settings.prepareSec * 1000L)) },
                        onNew = { editIndex = null; seeded = null; screen = "editor" },
                        onEdit = { idx -> editIndex = idx; seeded = null; screen = "editor" },
                    )
                    screen == "editor" -> EditorScreen(
                        initial = editIndex?.let { PresetStore.saved.getOrNull(it) } ?: seeded,
                        onStart = { launchWorkout(it.toWorkout(Settings.prepareSec * 1000L)) },
                        onSave = { p ->
                            val idx = editIndex
                            if (idx == null) PresetStore.add(p) else PresetStore.update(idx, p)
                            screen = "presets"
                        },
                        onCancel = { screen = "setup" },
                    )
                    else -> SetupScreen(
                        onGo = ::startWorkout,
                        onSettings = { screen = "settings" },
                        onPresets = { screen = "presets" },
                        onCustom = { wSec, rSec, rds ->
                            editIndex = null
                            // Full rounds including trailing rest, so the editor groups it as one
                            // (work, rest) × rounds block. toWorkout drops a trailing rest at run time.
                            seeded = Preset(
                                "",
                                buildList {
                                    repeat(rds) {
                                        add(SeqInterval(Phase.WORK, wSec))
                                        if (rSec > 0) add(SeqInterval(Phase.REST, rSec))
                                    }
                                },
                            )
                            screen = "editor"
                        },
                    )
                }
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun startWorkout(workMs: Long, restMs: Long, rounds: Int) {
        launchWorkout(baseWorkout(prepareMs = Settings.prepareSec * 1000L, workMs = workMs, restMs = restMs, rounds = rounds))
    }

    /**
     * Attach to a workout that's already running (app was closed and reopened).
     *
     * Gate on TimerService.isRunning, NOT on bindService()'s return value — that returns true
     * whenever the component merely resolves, even with no live service, which would leave us
     * waiting forever for an onServiceConnected that never comes.
     */
    private fun reattachToRunningWorkout() {
        if (bound || !TimerService.isRunning) return
        attaching = true
        bound = bindService(Intent(this, TimerService::class.java), connection, 0)
        if (!bound) attaching = false
    }

    private fun launchWorkout(workout: Workout) {
        val svc = service
        if (svc != null) {
            svc.start(workout)
        } else {
            pending = workout
            val intent = Intent(this, TimerService::class.java)
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
        // "Run in background" off means closing the app ends the workout, but onTaskRemoved only
        // fires on a swipe from recents — a back-press finishes the Activity and left the timer
        // running regardless of the setting. isFinishing keeps a config change from stopping it.
        if (isFinishing && !Settings.runInBackground) service?.stop()
        releaseBinding()
        super.onDestroy()
    }
}

// ---- Setup / home ----

@Composable
private fun SetupScreen(
    onGo: (workMs: Long, restMs: Long, rounds: Int) -> Unit,
    onSettings: () -> Unit,
    onPresets: () -> Unit,
    onCustom: (workSec: Int, restSec: Int, rounds: Int) -> Unit,
) {
    // Seeded from (and written back to) Settings, so stopping and restarting keeps your last values.
    val workSec = Settings.workSec
    val restSec = Settings.restSec
    val rounds = Settings.rounds

    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            TextButton(onPresets, "Presets", Modifier.align(Alignment.TopStart).padding(4.dp))
            TextButton(onSettings, "Settings", Modifier.align(Alignment.TopEnd).padding(4.dp))

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Δτ", color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(40.dp))
                Stepper(
                    "Work", secLabel(workSec),
                    { m -> Settings.updateWorkSec((workSec - 5 * m).coerceAtLeast(5)) },
                    { m -> Settings.updateWorkSec(workSec + 5 * m) },
                    onReset = { Settings.updateWorkSec(DEFAULT_WORK_SEC) },
                )
                Spacer(Modifier.height(16.dp))
                Stepper(
                    "Rest", secLabel(restSec),
                    { m -> Settings.updateRestSec((restSec - 5 * m).coerceAtLeast(0)) },
                    { m -> Settings.updateRestSec(restSec + 5 * m) },
                    onReset = { Settings.updateRestSec(DEFAULT_REST_SEC) },
                )
                Spacer(Modifier.height(16.dp))
                Stepper(
                    "Rounds", "$rounds",
                    { m -> Settings.updateRounds((rounds - m).coerceAtLeast(1)) },
                    { m -> Settings.updateRounds(rounds + m) },
                    onReset = { Settings.updateRounds(DEFAULT_ROUNDS) },
                )
                Spacer(Modifier.height(32.dp))
                GlassPill("GO", { onGo(workSec * 1000L, restSec * 1000L, rounds) }, Modifier.fillMaxWidth(), big = true)
                Spacer(Modifier.height(12.dp))
                // Carries these values into the editor: add more intervals, then save it as a preset.
                GlassPill("+  Add intervals", { onCustom(workSec, restSec, rounds) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TextButton(onClick: () -> Unit, text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
    }
}

/** Double-tapping the number puts it back to the stock value — the way out of a hold that overshot. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: (Int) -> Unit,
    onPlus: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 20.sp, modifier = Modifier.width(90.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassCircle("−", onMinus)
            Text(
                value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(96.dp)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) },
                textAlign = TextAlign.Center,
            )
            GlassCircle("+", onPlus)
        }
    }
}

// ---- Settings ----

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    var langOpen by remember { mutableStateOf(false) }
    val current = Language.of(Settings.languageCode)
    Box(Modifier.fillMaxSize()) {
      HomeBackground(Modifier.fillMaxSize())
      Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack, "‹ Back")
            Spacer(Modifier.width(8.dp))
            Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))

        SettingsCard("Settings") {
            ToggleRow("Mute", Settings.muted) { Settings.updateMuted(it) }
            Spacer(Modifier.height(16.dp))
            Text("Volume", color = Color.White, fontSize = 18.sp)
            VolumeSlider()
            Spacer(Modifier.height(16.dp))
            ToggleRow("Run in background", Settings.runInBackground, sub = "Keep the timer running if you close the app") { Settings.updateRunInBackground(it) }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Get ready", color = Color.White, fontSize = 18.sp)
                    Text(
                        "Countdown before the first interval",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle("−", { m -> Settings.updatePrepareSec(Settings.prepareSec - 5 * m) })
                    Text(
                        if (Settings.prepareSec == 0) "Off" else secLabel(Settings.prepareSec),
                        color = Color.White,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(64.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { Settings.updatePrepareSec(DEFAULT_PREPARE_SEC) })
                            },
                    )
                    GlassCircle("+", { m -> Settings.updatePrepareSec(Settings.prepareSec + 5 * m) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingsCard("Fun") {
            PalettePicker()
            Spacer(Modifier.height(18.dp))
            // Orthogonal to the palette on purpose: Minimal + Vesper is a black timer with Vesper
            // on the edge. Pick Mono as well and you get the plain black-and-white one.
            ToggleRow(
                "Minimal",
                Settings.minimalBg,
                sub = "Black background, colour only on the edge",
            ) { Settings.updateMinimalBg(it) }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().clickable { langOpen = !langOpen }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Language", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${current.english}  ${if (langOpen) "▲" else "▼"}", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
            }
            if (langOpen) {
                Spacer(Modifier.height(8.dp))
                ToggleRow("Word mode", Settings.wordMode, sub = "Spell numbers for Western-numeral languages") { Settings.updateWordMode(it) }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Pure script", Settings.pureScript, sub = "Hide the Western numeral fallback") { Settings.updatePureScript(it) }
                Spacer(Modifier.height(12.dp))
                Language.entries.forEach { lang ->
                    val selected = lang.code == Settings.languageCode
                    Row(
                        Modifier.fillMaxWidth().clickable { Settings.updateLanguage(lang.code) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(lang.english, color = if (selected) Color.White else Color.White.copy(alpha = 0.55f), fontSize = 17.sp)
                        if (selected) Text("✓", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }
      }
    }
}

/**
 * Theme picker: each swatch is the palette's own work/rest/prepare colours, in that order, so you
 * choose by looking at the actual thing rather than by reading a name you've never heard of.
 *
 * Three across, growing downward. Plain Rows over a chunked list rather than a LazyVerticalGrid:
 * this sits inside the settings screen's verticalScroll, which hands children unbounded height and
 * would crash a lazy grid outright — and with a fixed couple of dozen swatches there is nothing to
 * be lazy about anyway.
 */
@Composable
private fun PalettePicker() {
    Text("Theme", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Palette.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { p -> PaletteSwatch(p, Modifier.weight(1f)) }
            // Keeps a short final row left-aligned at the same cell width instead of stretching it.
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PaletteSwatch(p: Palette, modifier: Modifier = Modifier) {
    val selected = p == Settings.palette
    val shape = RoundedCornerShape(12.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(shape)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                    shape,
                )
                .clickable { Settings.updatePalette(p) }
                .padding(3.dp)
                .clip(RoundedCornerShape(9.dp)),
        ) {
            listOf(p.work, p.rest, p.prep).forEach { c ->
                Box(Modifier.weight(1f).fillMaxHeight().background(c))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            p.label,
            color = Color.White.copy(alpha = if (selected) 1f else 0.5f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), shape)
            .padding(20.dp),
    ) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** Touch-friendly volume slider with a big round dot thumb and a simple rounded track. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSlider() {
    // Muted pins the slider to 0; dragging up un-mutes.
    val displayed = if (Settings.muted) 0f else Settings.volume
    Slider(
        value = displayed,
        onValueChange = { v ->
            if (Settings.muted && v > 0f) Settings.updateMuted(false)
            Settings.updateVolume(v)
        },
        onValueChangeFinished = { Settings.persistVolume() },
        valueRange = 0f..1f,
        thumb = {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        },
        track = { state ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.value.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.7f)),
                )
            }
        },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 18.sp)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- Running timer ----

/** Deliberate hold needed to pause, so a pocket brush or a stray palm can't stop a set. */
private const val HOLD_TO_PAUSE_MS = 400L

// Blue reads far dimmer than green at equal saturation, so rest uses a bright sky tone to stay
// legible once the progress arms shrink.
private fun glowColor(phase: Phase): Color = when (phase) {
    Phase.PREPARE -> PrepColor
    Phase.WORK -> WorkColor
    Phase.REST -> RestColor
    Phase.DONE -> DoneGray
}

@Composable
private fun TimerScreen(ui: TimerUiState, onPause: () -> Unit, onResume: () -> Unit, onEnd: () -> Unit) {
    val glow by animateColorAsState(glowColor(ui.phase), tween(700), label = "glow")
    val lang = Language.of(Settings.languageCode)

    // Hold the display awake AND at full brightness — keepScreenOn alone stops the sleep timer
    // but not the OS's slow auto-dim, which was darkening the screen a few minutes in. Held for
    // the whole screen including the Done state (releasing on `done` visibly dimmed the finish);
    // both release when the screen leaves.
    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    DisposableEffect(view) {
        view.keepScreenOn = true
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
        onDispose {
            view.keepScreenOn = false
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // Pause is a deliberate hold, not a tap. A tap just says so.
    var holding by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    if (showHint) LaunchedEffect(showHint) { delay(1_600); showHint = false }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().pointerInput(ui.done, ui.paused) {
            if (ui.done || ui.paused) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                showHint = false
                holding = true
                // Null means the timeout won: still held, so pause. A lift or a cancel returns
                // Unit and counts as a tap.
                val lifted = withTimeoutOrNull(HOLD_TO_PAUSE_MS) { waitForUpOrCancellation(); Unit }
                holding = false
                if (lifted == null) {
                    onPause()
                    waitForUpOrCancellation()
                } else {
                    showHint = true
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        // Budgets are in dp but font sizes are sp, which the system font-scale multiplies. Divide it
        // out so a user running large text doesn't blow past every fit we compute below.
        val fontScale = LocalDensity.current.fontScale
        val labelSize = (w / 7.0f / fontScale).coerceIn(24f, 72f).sp
        val counterSize = (w / 14f / fontScale).coerceIn(14f, 34f).sp

        // Pause and finish never blur or dim this — they only swap out the centre text, so the
        // glow, the progress arms and the round counter stay exactly as they were.
        Box(Modifier.fillMaxSize()) {
            // Done keeps the glow at full bloom — letting the progress dim it made the whole
            // finish read as the screen going dark.
            AuraBackground(glow = glow, progress = if (ui.done) 1f else ui.fraction, modifier = Modifier.fillMaxSize())
            if (!ui.done) SplitProgress(remaining = 1f - ui.fraction, color = glow, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                TimerContent(ui, lang, w, h, labelSize, counterSize)
            }
        }

        if (ui.done) {
            // Two overlapping streams rather than one, so shells land on top of each other.
            ConfettiBurst(11L, 350, Modifier.fillMaxSize())
            ConfettiBurst(77L, 900, Modifier.fillMaxSize())
            // The whole screen dismisses — no aiming for a button when you're spent.
            Box(
                Modifier.fillMaxSize().noRippleClickable(onEnd),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        } else if (ui.paused) {
            PauseMenu(onResume, onEnd)
        }

        // Bottom hint: fills while you hold so a 3s press reads as progress, not a dead screen.
        if (!ui.done && !ui.paused && (holding || showHint)) {
            val fill by animateFloatAsState(
                targetValue = if (holding) 1f else 0f,
                animationSpec = tween(if (holding) HOLD_TO_PAUSE_MS.toInt() else 180, easing = LinearEasing),
                label = "hold",
            )
            HoldHint(fill, Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp))
        }
    }
}

/** "Hold to pause" pill; [fill] 0..1 sweeps a brighter bar across it as the hold progresses. */
@Composable
private fun HoldHint(fill: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, glassBorder(), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().drawBehind {
            drawRect(Color.White.copy(alpha = 0.22f), size = size.copy(width = size.width * fill))
        })
        Text(
            "Hold to pause",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }
}

/**
 * Bold only where a real bold face exists. The system CJK fonts are Regular-only, so a bold
 * request there gets synthesised by inflating the outline — which spikes at acute stroke joins.
 */
private fun glyphWeight(lang: Language): FontWeight =
    if (lang.cjk) FontWeight.Normal else FontWeight.Bold

/**
 * A big round glyph button in the app's tinted-glass language — the same treatment as the coloured
 * pills elsewhere, just circular and scaled up for a thumb.
 */
@Composable
private fun PauseAction(play: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.22f))
            .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val s = size.minDimension
            if (play) {
                // Vertices chosen so the triangle's centroid — not its bounding box — lands on the
                // centre. A box-centred play triangle always looks shifted left.
                drawPath(
                    Path().apply {
                        moveTo(s * 0.267f, s * 0.11f)
                        lineTo(s * 0.967f, s * 0.5f)
                        lineTo(s * 0.267f, s * 0.89f)
                        close()
                    },
                    Color.White,
                )
            } else {
                val i = s * 0.10f
                val w = s * 0.15f
                drawLine(Color.White, Offset(i, i), Offset(s - i, s - i), w, StrokeCap.Round)
                drawLine(Color.White, Offset(s - i, i), Offset(i, s - i), w, StrokeCap.Round)
            }
        }
    }
}


private class Spark(val angle: Float, val speed: Float, val radius: Float, val color: Color)

/**
 * A one-shot firework at the finish: sparks thrown out from the centre, heavily blurred so they
 * read as soft blooms of colour rather than confetti shapes, gone inside a second.
 */
@Composable
private fun ConfettiBurst(seed: Long, startDelayMs: Long, modifier: Modifier = Modifier) {
    // Each round is its own shell, launched from a different spot. Fixed seeds: it's decoration,
    // and a stable pattern can't randomly land badly.
    val shells = remember(seed) {
        List(BURST_COUNT) { i ->
            val rnd = Random(seed + i)
            val palette = listOf(WorkColor, RestColor, PrepColor, Color.White)
            val sparks = List(38) {
                Spark(
                    angle = rnd.nextFloat() * 2f * PI.toFloat(),
                    speed = 0.30f + rnd.nextFloat() * 0.70f,
                    radius = 22f + rnd.nextFloat() * 46f,
                    color = palette[rnd.nextInt(palette.size)],
                )
            }
            sparks to Offset(rnd.nextFloat() * 0.6f - 0.3f, rnd.nextFloat() * 0.5f - 0.25f)
        }
    }
    var shell by remember { mutableStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(seed) {
        delay(startDelayMs) // let the blur settle, so the fireworks read as a reward not a transition
        repeat(BURST_COUNT) { i ->
            shell = i
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 1_100, easing = LinearOutSlowInEasing))
        }
    }

    val (sparks, origin) = shells[shell]
    Canvas(modifier.blur(26.dp)) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val maxDist = size.minDimension * 0.55f
        val from = Offset(center.x + size.width * origin.x, center.y + size.height * origin.y)
        val alpha = (1f - p).coerceAtMost(0.6f)
        sparks.forEach { s ->
            val d = maxDist * s.speed * p
            drawCircle(
                color = s.color.copy(alpha = alpha),
                radius = s.radius * (1f - 0.45f * p),
                center = Offset(from.x + cos(s.angle) * d, from.y + sin(s.angle) * d),
            )
        }
    }
}

/** Five one-second shells ≈ five seconds of fireworks, then it settles. */
private const val BURST_COUNT = 5

/**
 * The stacked clock's separator: the colon turned on its side, drawn rather than typed so the dot
 * size and the gaps above and below scale with [size] instead of inheriting a glyph's own metrics.
 */
@Composable
private fun ColonDots(size: androidx.compose.ui.unit.TextUnit) {
    val dot = with(LocalDensity.current) { size.toDp() * 0.10f }
    Row(
        modifier = Modifier.padding(vertical = dot * 1.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dot).background(Color.White, CircleShape))
        Spacer(Modifier.width(dot * 1.4f))
        Box(Modifier.size(dot).background(Color.White, CircleShape))
    }
}

/**
 * Largest font size (sp) at which [text] fits one line inside [availWPx] x [availHPx].
 *
 * Measured with the real font rather than estimated per-glyph: guessing widths kept under-sizing
 * CJK/Hangul, which pushed the text past the edge and let it stack on itself. Text width scales
 * linearly with font size, so one measurement at a reference size gives the exact ratio, and the
 * measured px already include the user's font scale.
 */
private fun fittedSp(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    family: FontFamily,
    weight: FontWeight,
    availWPx: Float,
    availHPx: Float,
    minSp: Float,
    maxSp: Float,
): Float {
    if (text.isEmpty() || availWPx <= 0f || availHPx <= 0f) return minSp
    val refSp = 100f
    val measured = measurer.measure(
        androidx.compose.ui.text.AnnotatedString(text),
        style = androidx.compose.ui.text.TextStyle(fontSize = refSp.sp, fontWeight = weight, fontFamily = family),
        maxLines = 1,
        softWrap = false,
    )
    val wPx = measured.size.width.toFloat()
    val hPx = measured.size.height.toFloat()
    if (wPx <= 0f || hPx <= 0f) return minSp
    return (refSp * minOf(availWPx / wPx, availHPx / hPx)).coerceIn(minSp, maxSp)
}

@Composable
private fun TimerContent(ui: TimerUiState, lang: Language, wDp: Float, hDp: Float, labelSize: androidx.compose.ui.unit.TextUnit, counterSize: androidx.compose.ui.unit.TextUnit) {
    val label = when {
        // Finished: the big centred "Done" is the whole message, so nothing rides up top.
        ui.done -> ""
        ui.phase == Phase.PREPARE -> lang.ready
        ui.phase == Phase.WORK -> lang.work
        ui.phase == Phase.REST -> lang.rest
        else -> ""
    }
    val underMinute = !ui.done && ui.remainingMs < 60_000
    // Words are only for Western-numeral languages (Russian etc.) — native-glyph scripts (Tibetan,
    // Hindi, Chinese…) already look distinct, so just show the glyphs.
    val showWords = Settings.wordMode && underMinute && lang.digits == null

    // Space the big number may occupy, in px. Width excludes the container's 14dp side padding;
    // height is capped so a tall glyph can't collide with the labels up top (the Flip's cover
    // screen is short enough for that to matter).
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    val availWPx = with(density) { (wDp - 28f).coerceAtLeast(1f).dp.toPx() }
    val availHPx = with(density) { (hDp * 0.42f).coerceAtLeast(1f).dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        // The number owns the true middle of the screen, regardless of the labels riding up top.
        // Finished or paused it steps aside entirely — the centred "Done"/"Paused" takes that spot.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Everything below this point runs only while the clock is live: done and paused both
            // step aside for the centred "Done"/"Paused", so ui.done is always false from here.
            if (ui.done || ui.paused) Unit
            else if (showWords) {
                val word = Numbers.words(ui.remainingMs, lang)
                // Remembered on the word: the state ticks at 30fps but the word changes once a
                // second, and fittedSp is a real main-thread text layout.
                val wordSize = remember(word, lang, availWPx, availHPx) {
                    fittedSp(measurer, word, FontFamily.Default, glyphWeight(lang), availWPx, availHPx, 24f, 150f)
                }.sp
                Text(word, color = Color.White, fontSize = wordSize, fontWeight = glyphWeight(lang), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            } else {
                val lines = Numbers.clockLines(ui.remainingMs, lang)
                val clockFont = if (lang.digits == null) FontFamily.Monospace else FontFamily.Default
                val clockWeight = glyphWeight(lang)
                // Size to what's on screen right now, so the count fills the width the whole way
                // down — 二十七 is three glyphs and 十 is one, and holding one size for the interval
                // left the short values as a small mark in the middle of a lot of black.
                //
                // This is a deliberate reversal: it used to pin one size for the interval, measured
                // against the widest second the interval could reach, specifically so the number
                // wouldn't balloon as digits dropped. Growing is the point now.
                //
                // Keyed on `lines`, so it re-measures once a second rather than once a frame. Both
                // lines of a stacked clock take the smaller of the two fits, or MM and SS would
                // disagree. A stacked clock gets a taller budget, since it stops fighting for width.
                val clockSize = remember(lines, lang, availWPx, availHPx) {
                    val perLineH =
                        if (lines.size > 1) availHPx * 1.3f / lines.size * 0.87f else availHPx
                    lines.minOf {
                        fittedSp(measurer, it, clockFont, clockWeight, availWPx, perLineH, 32f, 260f)
                    }
                }.sp
                val clockLine: @Composable (String) -> Unit = { line ->
                    Text(
                        line,
                        color = Color.White,
                        fontSize = clockSize,
                        // No lineHeight override: forcing 1em disagreed with fittedSp, which
                        // measures at the font's own metrics.
                        fontWeight = glyphWeight(lang),
                        fontFamily = clockFont,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (lines.size > 1) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        clockLine(lines[0])
                        ColonDots(clockSize)
                        clockLine(lines[1])
                    }
                } else {
                    clockLine(lines[0])
                }
            }
        }

        // Counter above label, up top. The counter slot is always reserved (rendered invisibly when
        // there's no round, i.e. prepare) so the label never shifts between phases and the two can't
        // overlap regardless of font size.
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hasRound = ui.round > 0
            Text(
                if (hasRound) "${ui.round} / ${ui.totalRounds}" else " ",
                color = Color.White.copy(alpha = if (hasRound) 0.80f else 0f),
                fontSize = counterSize,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color.White, fontSize = labelSize, fontWeight = glyphWeight(lang), textAlign = TextAlign.Center)
        }

        // Word mode's numeric readout is anchored to a fixed spot rather than stacked under the word —
        // stacked, it slid up and down every time the word's length changed.
        if (showWords && !Settings.pureScript) {
            Text(
                formatMs(ui.remainingMs),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 26.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
            )
        }
    }
}

@Composable
private fun PauseMenu(onResume: () -> Unit, onEnd: () -> Unit) {
    // Same voice as the finish screen: no panel, no scrim, nothing floating on top — this simply
    // takes the place of the number, so the glow and progress arms behind it read as untouched.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Paused", color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PauseAction(play = true, accent = WorkColor, onClick = onResume)
                Spacer(Modifier.width(28.dp))
                PauseAction(play = false, accent = DangerRed, onClick = onEnd)
            }
        }
    }
}
