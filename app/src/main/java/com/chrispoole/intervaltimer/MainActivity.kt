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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.chrispoole.intervaltimer.model.TimerUiState
import com.chrispoole.intervaltimer.model.Workout
import com.chrispoole.intervaltimer.model.baseWorkout
import com.chrispoole.intervaltimer.model.formatMs
import com.chrispoole.intervaltimer.model.toWorkout
import com.chrispoole.intervaltimer.service.TimerService
import com.chrispoole.intervaltimer.ui.AuraBackground
import com.chrispoole.intervaltimer.ui.EditorScreen
import com.chrispoole.intervaltimer.ui.GlassCircle
import com.chrispoole.intervaltimer.ui.GlassFill
import com.chrispoole.intervaltimer.ui.GlassPill
import com.chrispoole.intervaltimer.ui.HomeBackground
import com.chrispoole.intervaltimer.ui.PerimeterProgress
import com.chrispoole.intervaltimer.ui.PresetsScreen
import com.chrispoole.intervaltimer.ui.glassBorder
import com.chrispoole.intervaltimer.ui.rememberDisplayCornerRadius

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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as TimerService.LocalBinder).service
            service = svc
            pending?.let { svc.start(it); pending = null }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
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
        if (Build.VERSION.SDK_INT >= 33) requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            MaterialTheme(colorScheme = MonoScheme) {
                val ui = service?.state?.collectAsStateWithLifecycle()?.value ?: TimerUiState.Idle
                var screen by remember { mutableStateOf("setup") }
                var editIndex by remember { mutableStateOf<Int?>(null) }
                when {
                    ui.running -> TimerScreen(
                        ui = ui,
                        onPause = { service?.pause() },
                        onResume = { service?.resume() },
                        onEnd = { endWorkout() },
                    )
                    screen == "settings" -> SettingsScreen(onBack = { screen = "setup" })
                    screen == "presets" -> PresetsScreen(
                        onBack = { screen = "setup" },
                        onStart = { launchWorkout(it.toWorkout()) },
                        onNew = { editIndex = null; screen = "editor" },
                        onEdit = { idx -> editIndex = idx; screen = "editor" },
                    )
                    screen == "editor" -> EditorScreen(
                        initial = editIndex?.let { PresetStore.saved.getOrNull(it) },
                        onStart = { launchWorkout(it.toWorkout()) },
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
                        onCustom = { editIndex = null; screen = "editor" },
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
        launchWorkout(baseWorkout(prepareMs = 5_000, workMs = workMs, restMs = restMs, rounds = rounds))
    }

    private fun launchWorkout(workout: Workout) {
        val svc = service
        if (svc != null) {
            svc.start(workout)
        } else {
            pending = workout
            val intent = Intent(this, TimerService::class.java)
            ContextCompat.startForegroundService(this, intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun endWorkout() {
        service?.stop()
        runCatching { unbindService(connection) }
        service = null
        pending = null
    }

    override fun onDestroy() {
        runCatching { unbindService(connection) }
        super.onDestroy()
    }
}

// ---- Setup / home ----

@Composable
private fun SetupScreen(
    onGo: (workMs: Long, restMs: Long, rounds: Int) -> Unit,
    onSettings: () -> Unit,
    onPresets: () -> Unit,
    onCustom: () -> Unit,
) {
    var workSec by remember { mutableStateOf(30) }
    var restSec by remember { mutableStateOf(15) }
    var rounds by remember { mutableStateOf(8) }

    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            TextButton(onPresets, "Presets", Modifier.align(Alignment.TopStart).padding(4.dp))
            TextButton(onCustom, "Custom", Modifier.align(Alignment.TopCenter).padding(4.dp))
            TextButton(onSettings, "Settings", Modifier.align(Alignment.TopEnd).padding(4.dp))

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Δτ", color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(44.dp))
                Stepper("Work", "${workSec}s", { workSec = (workSec - 5).coerceAtLeast(5) }, { workSec += 5 })
                Spacer(Modifier.height(16.dp))
                Stepper("Rest", "${restSec}s", { restSec = (restSec - 5).coerceAtLeast(0) }, { restSec += 5 })
                Spacer(Modifier.height(16.dp))
                Stepper("Rounds", "$rounds", { rounds = (rounds - 1).coerceAtLeast(1) }, { rounds += 1 })
                Spacer(Modifier.height(44.dp))
                GlassPill("GO", { onGo(workSec * 1000L, restSec * 1000L, rounds) }, Modifier.fillMaxWidth(), big = true)
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

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
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
                modifier = Modifier.width(96.dp),
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
        }
        Spacer(Modifier.height(16.dp))
        SettingsCard("Fun") {
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

private fun glowColor(phase: Phase): Color = when (phase) {
    Phase.PREPARE -> Color(0xFF8B5CF6) // violet (not yellow)
    Phase.WORK -> Color(0xFF22E06A)
    Phase.REST -> Color(0xFF3B82F6)
    Phase.DONE -> Color(0xFF9CA3AF)
}

@Composable
private fun TimerScreen(ui: TimerUiState, onPause: () -> Unit, onResume: () -> Unit, onEnd: () -> Unit) {
    val glow by animateColorAsState(glowColor(ui.phase), tween(700), label = "glow")
    val corner = rememberDisplayCornerRadius()
    val blurRadius by animateDpAsState(if (ui.paused || ui.done) 26.dp else 0.dp, tween(300), label = "blur")
    val lang = Language.of(Settings.languageCode)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clickable(enabled = !ui.done) { if (!ui.paused) onPause() },
        contentAlignment = Alignment.Center,
    ) {
        val w = maxWidth.value
        val labelSize = (w / 7.0f).coerceIn(32f, 72f).sp
        val counterSize = (w / 14f).coerceIn(18f, 34f).sp

        // Background layer — blurs behind the pause glass.
        Box(Modifier.fillMaxSize().blur(blurRadius)) {
            AuraBackground(glow = glow, progress = ui.fraction, modifier = Modifier.fillMaxSize())
            if (!ui.done) PerimeterProgress(remaining = 1f - ui.fraction, color = glow, cornerRadius = corner, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                TimerContent(ui, ui.phase, lang, w, labelSize, counterSize)
            }
        }

        if (ui.done) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
                GlassPill("Done", onEnd, Modifier.fillMaxWidth(), big = true)
            }
        } else if (ui.paused) {
            PauseMenu(onResume, onEnd)
        }
    }
}

@Composable
private fun TimerContent(ui: TimerUiState, phase: Phase, lang: Language, wDp: Float, labelSize: androidx.compose.ui.unit.TextUnit, counterSize: androidx.compose.ui.unit.TextUnit) {
    val label = when {
        ui.done -> "Done"
        phase == Phase.PREPARE -> lang.ready
        phase == Phase.WORK -> lang.work
        phase == Phase.REST -> lang.rest
        else -> ""
    }
    val underMinute = !ui.done && ui.remainingMs < 60_000
    // Words are only for Western-numeral languages (Russian etc.) — native-glyph scripts (Tibetan,
    // Hindi, Chinese…) already look distinct, so just show the glyphs.
    val showWords = Settings.wordMode && underMinute && lang.digits == null

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (ui.round > 0) {
            Text("${ui.round} / ${ui.totalRounds}", color = Color.White.copy(alpha = 0.80f), fontSize = counterSize, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
        }
        Text(label, color = Color.White, fontSize = labelSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))

        if (showWords) {
            val word = Numbers.words(ui.remainingMs, lang)
            val wordSize = (wDp / (word.length.coerceAtLeast(3) * 0.62f)).coerceIn(30f, 120f).sp
            Text(word, color = Color.White, fontSize = wordSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, softWrap = true)
            if (!Settings.pureScript) {
                Spacer(Modifier.height(8.dp))
                Text(formatMs(ui.remainingMs), color = Color.White.copy(alpha = 0.5f), fontSize = 26.sp, fontFamily = FontFamily.Monospace)
            }
        } else {
            val isWide = lang == Language.ZH || lang == Language.JA
            val clockSize = (wDp / (if (isWide) 5.6f else 3.1f)).coerceIn(52f, 150f).sp
            val clockFont = if (lang.digits == null) FontFamily.Monospace else FontFamily.Default
            Text(
                text = if (ui.done) Numbers.clock(0, lang) else Numbers.clock(ui.remainingMs, lang),
                color = Color.White,
                fontSize = clockSize,
                fontWeight = FontWeight.Bold,
                fontFamily = clockFont,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PauseMenu(onResume: () -> Unit, onEnd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, glassBorder(), shape)
                .padding(horizontal = 30.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Paused", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            GlassPill("Resume", onResume, Modifier.width(220.dp), tint = Color(0xFF22E06A))
            Spacer(Modifier.height(14.dp))
            GlassPill("End workout", onEnd, Modifier.width(220.dp), tint = Color(0xFFEF4444))
        }
    }
}
