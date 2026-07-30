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
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
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
import com.chrispoole.intervaltimer.model.SeqInterval
import com.chrispoole.intervaltimer.model.TimerUiState
import com.chrispoole.intervaltimer.model.Workout
import com.chrispoole.intervaltimer.model.baseWorkout
import com.chrispoole.intervaltimer.model.HomeBlock
import com.chrispoole.intervaltimer.model.formatMs
import com.chrispoole.intervaltimer.model.homePreset
import com.chrispoole.intervaltimer.model.secLabel
import com.chrispoole.intervaltimer.model.toWorkout
import com.chrispoole.intervaltimer.service.TimerService
import com.chrispoole.intervaltimer.ui.AuraBackground
import com.chrispoole.intervaltimer.ui.AuraSwatch
import com.chrispoole.intervaltimer.ui.CistercianNumeral
import com.chrispoole.intervaltimer.ui.cistercianSeconds
import com.chrispoole.intervaltimer.ui.DangerRed
import com.chrispoole.intervaltimer.ui.DoneGray
import com.chrispoole.intervaltimer.ui.DragHandle
import com.chrispoole.intervaltimer.ui.EditorScreen
import com.chrispoole.intervaltimer.ui.rememberDragDropState
import com.chrispoole.intervaltimer.ui.PrepColor
import com.chrispoole.intervaltimer.ui.RestColor
import com.chrispoole.intervaltimer.ui.WorkColor
import com.chrispoole.intervaltimer.ui.GlassCircle
import com.chrispoole.intervaltimer.ui.GlassFill
import com.chrispoole.intervaltimer.ui.GlassPill
import com.chrispoole.intervaltimer.ui.HomeBackground
import com.chrispoole.intervaltimer.ui.NoticePill
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
                // The home screen's sections. Hoisted here so a trip to Settings or Presets
                // doesn't wipe an in-progress sequence; seeded from the last-used values.
                val homeRows = remember {
                    mutableStateListOf(HomeRow(0L, HomeBlock(Settings.workSec, Settings.restSec, Settings.rounds)))
                }
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
                        onNew = { editIndex = null; screen = "editor" },
                        onEdit = { idx -> editIndex = idx; screen = "editor" },
                    )
                    screen == "editor" -> EditorScreen(
                        initial = editIndex?.let { PresetStore.saved.getOrNull(it) },
                        onStart = { launchWorkout(it.toWorkout(Settings.prepareSec * 1000L)) },
                        onSave = { p ->
                            val idx = editIndex
                            if (idx == null) PresetStore.add(p) else PresetStore.update(idx, p)
                            screen = "presets"
                        },
                        onCancel = { screen = "presets" },
                        saveLabel = "Save",
                    )
                    else -> SetupScreen(
                        rows = homeRows,
                        onGo = ::launchWorkout,
                        onSettings = { screen = "settings" },
                        onPresets = { screen = "presets" },
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

/** One home section with a stable id, so drag and the item animations can track it across moves. */
data class HomeRow(val id: Long, val b: HomeBlock)

/**
 * Name-and-save, as one fully rounded glass pill so it sits in the same family as every other
 * control. Material's OutlinedTextField was the only square-ish thing on the screen.
 */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    // Opened deliberately, so it takes the caret without a second tap.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), shape)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Name this preset", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).noRippleClickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
        }
        Spacer(Modifier.width(6.dp))
        // Nothing typed, nothing to save — that's the whole gate, no second step.
        GlassPill("Save", onSave, enabled = value.isNotBlank())
    }
}

@Composable
private fun SetupScreen(
    rows: SnapshotStateList<HomeRow>,
    onGo: (Workout) -> Unit,
    onSettings: () -> Unit,
    onPresets: () -> Unit,
) {
    // One section is the classic home; more than one folds each into its own card.
    val solo = rows.size == 1
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    if (saved) LaunchedEffect(Unit) { delay(2200); saved = false }

    fun change(i: Int, b: HomeBlock) {
        // Guard the captured index: a second tap can land before recomposition.
        if (i !in rows.indices) return
        rows[i] = HomeRow(rows[i].id, b)
        // The first section doubles as the remembered home values, so a relaunch
        // picks up where you left off — same contract as the old single-block home.
        if (i == 0) {
            Settings.updateWorkSec(b.workSec)
            Settings.updateRestSec(b.restSec)
            Settings.updateRounds(b.rounds)
        }
    }

    fun addBlock() {
        rows += HomeRow((rows.maxOfOrNull { it.id } ?: 0L) + 1, rows.last().b)
    }

    val listState = rememberLazyListState()
    // The name field is a list item above the cards whenever it's shown, so it displaces them by one.
    val firstCard = if (solo) 0 else 1
    val dragDrop = rememberDragDropState(
        listState = listState,
        draggable = firstCard until firstCard + rows.size,
        onMove = { from, to ->
            val f = from - firstCard
            val t = to - firstCard
            if (f in rows.indices && t in rows.indices) {
                rows.add(t, rows.removeAt(f))
                true
            } else false
        },
    )

    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 56.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!solo) {
                    item(key = "save") {
                        // animateContentSize turns the swap into the button growing into the
                        // field, rather than one control vanishing and another appearing.
                        Column(Modifier.animateItem().animateContentSize(tween(260))) {
                            if (naming) {
                                NameField(
                                    value = name,
                                    onValueChange = { name = it },
                                    onClose = { naming = false; name = "" },
                                    onSave = {
                                        PresetStore.add(homePreset(rows.map { it.b }).copy(name = name.trim()))
                                        naming = false
                                        name = ""
                                        saved = true
                                    },
                                )
                            } else {
                                GlassPill("Save as preset", { naming = true }, Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
                itemsIndexed(rows, key = { _, r -> r.id }) { i, row ->
                    val floating = !solo && dragDrop.isFloating(row.id)
                    // One wrapper around both shapes. Without it the bare steppers and the card
                    // were separate layouts, so going from one section to two read as the first
                    // one vanishing and two new things flying in. Now the existing section
                    // *resizes* into its card, and only the new one fades — after the shrink, so
                    // the two don't happen on top of each other.
                    Box(
                        Modifier
                            .then(
                                if (floating) Modifier
                                else Modifier.animateItem(fadeInSpec = tween(220, delayMillis = 240)),
                            )
                            .animateContentSize(tween(300)),
                    ) {
                    if (solo) {
                        val b = row.b
                        // No colour here on purpose: the plain home stays super simple, and the
                        // phase colours arrive only once there are sections to tell apart.
                        Column {
                            Stepper(
                                "Work", secLabel(b.workSec),
                                { m -> change(0, b.copy(workSec = (b.workSec - 5 * m).coerceAtLeast(5))) },
                                { m -> change(0, b.copy(workSec = b.workSec + 5 * m)) },
                                onReset = { change(0, b.copy(workSec = DEFAULT_WORK_SEC)) },
                            )
                            Spacer(Modifier.height(16.dp))
                            Stepper(
                                "Rest", secLabel(b.restSec),
                                { m -> change(0, b.copy(restSec = (b.restSec - 5 * m).coerceAtLeast(0))) },
                                { m -> change(0, b.copy(restSec = b.restSec + 5 * m)) },
                                onReset = { change(0, b.copy(restSec = DEFAULT_REST_SEC)) },
                            )
                            Spacer(Modifier.height(16.dp))
                            Stepper(
                                "Rounds", "${b.rounds}",
                                { m -> change(0, b.copy(rounds = (b.rounds - m).coerceAtLeast(1))) },
                                { m -> change(0, b.copy(rounds = b.rounds + m)) },
                                onReset = { change(0, b.copy(rounds = DEFAULT_ROUNDS)) },
                            )
                            Spacer(Modifier.height(32.dp))
                        }
                    } else {
                        val lifted = dragDrop.isLifted(row.id)
                        val scale by animateFloatAsState(if (lifted) 1.03f else 1f, label = "lift")
                        HomeBlockCard(
                            lifted = lifted,
                            b = row.b,
                            onChange = { change(i, it) },
                            onRemove = { if (i in rows.indices) rows.removeAt(i) },
                            handle = {
                                DragHandle(
                                    key = row.id,
                                    label = "section ${i + 1}",
                                    state = dragDrop,
                                    onMoveUp = if (i > 0) ({ rows.add(i - 1, rows.removeAt(i)); Unit }) else null,
                                    onMoveDown = if (i < rows.lastIndex) ({ rows.add(i + 1, rows.removeAt(i)); Unit }) else null,
                                )
                            },
                            // The card under the finger is placed by hand; the wrapper above lets
                            // the lazy list animate everything else aside.
                            modifier = Modifier
                                .zIndex(if (floating) 1f else 0f)
                                .graphicsLayer {
                                    translationY = dragDrop.offsetFor(row.id)
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                    }
                    }
                }
                item(key = "footer") {
                    Column(Modifier.animateItem()) {
                        if (!solo) {
                            GlassPill("+", ::addBlock, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(20.dp))
                        }
                        GlassPill(
                            "GO",
                            {
                                onGo(
                                    if (rows.size == 1) {
                                        val b = rows[0].b
                                        // Kept on baseWorkout so the timer's round counter stays
                                        // "n / rounds"; sequences count interval positions instead.
                                        baseWorkout(
                                            prepareMs = Settings.prepareSec * 1000L,
                                            workMs = b.workSec * 1000L, restMs = b.restSec * 1000L, rounds = b.rounds,
                                        )
                                    } else {
                                        homePreset(rows.map { it.b }).toWorkout(Settings.prepareSec * 1000L)
                                    },
                                )
                            },
                            Modifier.fillMaxWidth(),
                            big = true,
                        )
                        if (solo) {
                            Spacer(Modifier.height(12.dp))
                            GlassPill("+  Add intervals", ::addBlock, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // After the list, so these are hit-tested first. Declared before it, the LazyColumn's
            // scroll gesture sat on top of them and swallowed any tap that drifted a pixel — which
            // is why they only worked sometimes.
            TextButton(onPresets, "Presets", Modifier.align(Alignment.TopStart).padding(4.dp))
            TextButton(onSettings, "Settings", Modifier.align(Alignment.TopEnd).padding(4.dp))
            // Just a mark, not a control: no click, dimmed, and out of the scrolling content so it
            // stays put while the sections move under it.
            Text(
                "Δτ",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
            if (saved) {
                NoticePill(
                    "Saved to presets",
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                )
            }
        }
    }
}

/**
 * One section as a card: how many times it runs and its total up top, then the same tinted
 * work/rest rows as everywhere else. The header is also where you grab it to reorder.
 */
@Composable
private fun HomeBlockCard(
    b: HomeBlock,
    onChange: (HomeBlock) -> Unit,
    onRemove: () -> Unit,
    handle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
) {
    val shape = RoundedCornerShape(28.dp)
    // Same lift language as the presets editor: brighten the glass AND cast a shadow. On a near-black
    // background a shadow alone all but disappears, which is what made a dragged card read as a
    // smear over the one it was passing rather than as something held above it.
    val fill by animateColorAsState(if (lifted) Color.White.copy(alpha = 0.20f) else GlassFill, label = "fill")
    val edge by animateFloatAsState(if (lifted) 0.5f else 0f, label = "edge")
    val elevation by animateDpAsState(if (lifted) 20.dp else 0.dp, label = "elevation")
    // The phase colours fade in as the card lands, rather than popping alongside it.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(600)) }
    Column(
        modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .shadow(elevation, shape, clip = false)
            .background(fill, shape)
            // A brush both ways, so the resting edge keeps the glass gradient every other card has.
            .border(1.dp, if (lifted) SolidColor(Color.White.copy(alpha = edge)) else glassBorder(), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            handle()
            Spacer(Modifier.width(2.dp))
            GlassCircle("−", { m -> onChange(b.copy(rounds = (b.rounds - m).coerceAtLeast(1))) }, size = 36.dp)
            Text(
                "× ${b.rounds}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(52.dp),
            )
            GlassCircle("+", { m -> onChange(b.copy(rounds = b.rounds + m)) }, size = 36.dp)
            Spacer(Modifier.weight(1f))
            // The section's own playing time — the at-a-glance mini total.
            Text(
                formatMs((b.workSec + b.restSec) * b.rounds * 1000L),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).noRippleClickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Stepper(
            "Work", secLabel(b.workSec),
            { m -> onChange(b.copy(workSec = (b.workSec - 5 * m).coerceAtLeast(5))) },
            { m -> onChange(b.copy(workSec = b.workSec + 5 * m)) },
            onReset = { onChange(b.copy(workSec = DEFAULT_WORK_SEC)) },
            compact = true,
            tint = WorkColor.copy(alpha = appear.value),
        )
        Spacer(Modifier.height(6.dp))
        Stepper(
            "Rest", secLabel(b.restSec),
            { m -> onChange(b.copy(restSec = (b.restSec - 5 * m).coerceAtLeast(0))) },
            { m -> onChange(b.copy(restSec = b.restSec + 5 * m)) },
            onReset = { onChange(b.copy(restSec = DEFAULT_REST_SEC)) },
            compact = true,
            tint = RestColor.copy(alpha = appear.value),
        )
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
    compact: Boolean = false,
    tint: Color? = null,
) {
    val shape = RoundedCornerShape(50)
    val minimal = Settings.minimalBg
    // Only glow rows pay for an animation clock; plain and minimal rows never start one.
    val drift = if (tint != null && !minimal) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "drift",
        )
    } else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    tint == null -> Modifier
                    // Minimal mode's timer is black with only the perimeter stroke, so the preview
                    // is the same idea: a bubble around the edge, nothing filled in.
                    minimal -> Modifier
                        .border(1.5.dp, tint.copy(alpha = tint.alpha * 0.55f), shape)
                        .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp)
                    // Otherwise: what GO actually shows is a full wash of the phase colour. So the
                    // row is filled edge to edge, and the only movement is dips in brightness
                    // drifting across it. No shape inside the pill — a shape is what read as a
                    // sticker sitting on the row instead of the row being lit.
                    else -> Modifier
                        .border(1.dp, tint.copy(alpha = tint.alpha * 0.28f), shape)
                        .clip(shape)
                        .drawBehind {
                            val a = tint.alpha
                            // Repeated so it never seams; first and last stop match, and one cycle
                            // of `drift` slides it exactly one span, so the loop is invisible.
                            val span = size.width * 1.5f
                            val shift = drift!!.value * span
                            drawRect(
                                Brush.horizontalGradient(
                                    0.00f to tint.copy(alpha = a * 0.58f),
                                    0.32f to tint.copy(alpha = a * 0.43f),
                                    0.66f to tint.copy(alpha = a * 0.60f),
                                    1.00f to tint.copy(alpha = a * 0.58f),
                                    startX = shift - span,
                                    endX = shift,
                                    tileMode = TileMode.Repeated,
                                ),
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = if (compact) 15.sp else 20.sp,
            modifier = Modifier.width(if (compact) 70.dp else 90.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassCircle("−", onMinus, size = if (compact) 40.dp else 54.dp)
            Text(
                value,
                color = Color.White,
                fontSize = if (compact) 17.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(if (compact) 72.dp else 96.dp)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) },
                textAlign = TextAlign.Center,
            )
            GlassCircle("+", onPlus, size = if (compact) 40.dp else 54.dp)
        }
    }
}

// ---- Settings ----

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
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
            ToggleRow("Run in background", Settings.runInBackground) { Settings.updateRunInBackground(it) }
            Spacer(Modifier.height(16.dp))
            ToggleRow("No back-to-back rests", Settings.noDoubleRest) { Settings.updateNoDoubleRest(it) }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Get ready", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
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
            ToggleRow("Minimal", Settings.minimalBg) { Settings.updateMinimalBg(it) }
        }

        Spacer(Modifier.height(16.dp))

        // Its own panel rather than a dropdown inside Fun: the grid is the biggest thing on this
        // screen, and burying it behind a disclosure row made it feel like a footnote.
        SettingsCard("Language") {
            Text(
                current.english,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow("Word mode", Settings.wordMode, sub = "Spells the last minute — thirty-two, not 32. Languages with numerals of their own keep them.") { Settings.updateWordMode(it) }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(16.dp))
            LanguageGrid()
        }
      }
    }
}

/**
 * Language picker: every language counting 9 down to 1 at once, each over the current theme's work
 * colour, so you pick by watching what the timer will actually look like rather than by reading a
 * list of names.
 *
 * One clock for the whole grid — every tile ticking off the same second stays in step, and it's
 * one recomposition a second rather than one per tile.
 */
@Composable
private fun LanguageGrid() {
    val second by produceState(9) {
        while (true) {
            delay(1000)
            value = if (value <= 1) 9 else value - 1
        }
    }
    // Chinese and Japanese draw the same numerals from the same digits and the same 十 rule —
    // only three phase words tell them apart — so they share one tile that cycles between them,
    // and the tile shows whichever is live.
    val tiles = Language.entries
        .filter { it != Language.JA }
        .map { if (it == Language.ZH && Settings.languageCode == "ja") Language.JA else it }
    // Tight gaps: the tiles are the content here, and wide gutters left the panel mostly black.
    tiles.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { lang -> LanguageTile(lang, second, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LanguageTile(lang: Language, second: Int, modifier: Modifier = Modifier) {
    val selected = lang.code == Settings.languageCode
    // A bubble, not a box: a percentage radius stays organic at any tile size, matching the
    // gradients drifting inside them rather than framing them in hard corners.
    val shape = RoundedCornerShape(percent = 38)
    // Words here whatever the setting says — the picker's job is to tell the languages apart, and
    // English, Korean, Russian, Spanish and French all print the same Western 9. Digits would give
    // five identical tiles; девять / 구 / nueve / neuf are the only thing that separates them.
    val text = if (lang.digits == null && !lang.cistercian && !lang.stacks) {
        Numbers.words(second * 1000L, lang)
    } else {
        Numbers.clockLines(second * 1000L, lang).last()
    }
    // The selected tile previews the theme's *starting* colour and its "get ready" word; every
    // other tile is mid-work. So selection reads as a second colour from the same theme rather
    // than only as a ring — and the phase word is what finally tells 运动 from 運動.
    val phaseWord = if (selected) lang.ready else lang.work
    val phaseColor = if (selected) PrepColor else WorkColor
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                // Taller than wide, like the screen it's previewing — and the extra height is what
                // lets the numeral run big with the label tucked above it.
                .aspectRatio(0.82f)
                .clip(shape)
                .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
                // The shared tile cycles: first tap takes Chinese, each one after flips to the
                // other. Everything else just selects itself.
                .clickable {
                    Settings.updateLanguage(
                        if (lang.han) { if (Settings.languageCode == "zh") "ja" else "zh" }
                        else lang.code
                    )
                }
                .padding(4.dp),
        ) {
            // Seeded off the enum position: without it every tile froze the aura at the same
            // instant and the grid read as one image stamped out thirteen times.
            AuraSwatch(
                phaseColor,
                Modifier.fillMaxSize().clip(shape),
                // Both halves of the shared tile take Chinese's seed, so cycling relabels the tile
                // instead of restamping its aura — otherwise a tap reads as a different tile.
                seed = (if (lang.han) Language.ZH.ordinal else lang.ordinal) * 3.7f,
            )
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = LocalDensity.current
            val weight = glyphWeight(lang)
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            // The numeral is the point of the tile, so it gets most of the box. Now that the
            // fitter is honest about font scaling these budgets are real and don't need slack
            // held back for it.
            val size = remember(text, weight, wPx, hPx, density) {
                fittedSp(measurer, density, text, FontFamily.Default, weight, wPx * 0.80f, hPx * 0.52f, 9f, 46f)
            }.sp
            // The phase word rides up top like the timer's own label, small and out of the way,
            // rather than sharing the middle with the numeral.
            val labelSize = remember(phaseWord, wPx, density) {
                fittedSp(measurer, density, phaseWord, FontFamily.Default, FontWeight.Medium, wPx * 0.80f, hPx * 0.14f, 5f, 9f)
            }.sp
            Text(
                phaseWord,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = labelSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 9.dp),
            )
            if (lang.cistercian) {
                // Tighter than the text budget, not the same: a fitted glyph draws well inside its
                // font's box, but the canvas draws to its edges, so 0.52 of the height put the top
                // of the stave through the phase label.
                val side = minOf(maxWidth * 0.72f, maxHeight * 0.44f)
                // Centred on the stave is not centred to the eye. The tile only ever counts 9 down
                // to 1, so every glyph is stave + one top-right quadrant: ink spans x 0..1 of the
                // 2-wide box, putting its true centre a quarter-box right of where it's drawn.
                // Shifting back by that quarter is what makes it sit in the middle. The drop lands
                // its centre where the neighbouring tiles put theirs — a fitted numeral clears the
                // phase label on its own, but a full-height stave runs right up under it.
                CistercianNumeral(
                    second,
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = -side * 0.25f, y = side * 0.23f)
                        .size(side),
                    strokeWidth = side / 14f,
                )
            } else {
                Text(
                    text,
                    color = Color.White,
                    fontSize = size,
                    fontWeight = weight,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    // Down off dead centre, like the Cistercian glyph: a word sits high in its line
                    // box (the box reserves descender room "cinco" never uses), so centring the box
                    // leaves the ink riding above the middle of the tile.
                    modifier = Modifier.align(Alignment.Center).offset(y = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // Just the endonym: "中文 · Chinese" clips mid-string in a third-width tile, and the
            // native name is the more useful half here anyway.
            lang.english.substringBefore(" ·"),
            color = Color.White.copy(alpha = if (selected) 1f else 0.5f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Theme picker: each swatch is the palette's own colours in the order a workout meets them —
 * prepare, work, rest — so you choose by looking at the actual thing rather than by reading a
 * name you've never heard of.
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
                // Ring only on the selected one. A frame around every swatch was a grid of boxes
                // competing with the colours they were framing; now the ring means something.
                // Padding stays either way, so nothing shifts as the selection moves.
                .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
                .clickable { Settings.updatePalette(p) }
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Each stripe is the timer's actual shader at swatch size, not paint mixed to look
            // like it: every hand-tuned imitation was either too hot or too flat, and would have
            // drifted the moment the real aura changed. Separately rounded and spaced, so the
            // three read as three phases rather than as one band that changes colour twice.
            listOf(p.prep, p.work, p.rest).forEachIndexed { i, c ->
                AuraSwatch(
                    c,
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp)),
                    // Mixing in the stripe index stops one theme's three stripes sharing a frame.
                    seed = (p.ordinal * 3 + i) * 3.7f,
                )
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
            // Sits below the round counter and its pips, not on them: the hint is ~44dp tall, so at
            // the old 44dp offset its top edge landed 4dp under the progress grid and the
            // translucent pill read as covering it.
            HoldHint(fill, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
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
/**
 * How far through the workout you are, counted in repetitions — one pip per round, lit as each one
 * lands. It steps with the "1 / 7" counter above it and holds still in between; a bar that crept
 * every second just duplicated the countdown already filling the screen.
 *
 * White rather than the phase colour: it sits inside the phase-coloured aura, and a coloured bar on
 * a coloured wash reads as a smudge.
 */
@Composable
private fun OverallProgress(round: Int, totalRounds: Int) {
    val done = round.coerceIn(0, totalRounds)
    fun alpha(i: Int) = if (i < done) 0.85f else 0.22f
    when {
        // Eight or fewer: one row of wide pips, which is already easy to count at a glance.
        totalRounds <= 8 -> Row(
            Modifier.fillMaxWidth(0.52f).height(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(totalRounds) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = alpha(i))),
                )
            }
        }
        // More than eight: wrap into rows of eight. Sixteen slivers in a line is a bar you have to
        // measure; two rows of eight squares is a shape you can just read. Fixed eight per row
        // rather than splitting evenly, so the cells stay the same size whatever the count.
        totalRounds <= 8 * MAX_PIP_ROWS -> Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rows = (totalRounds + 7) / 8
            repeat(rows) { r ->
                Row(
                    Modifier.fillMaxWidth(0.46f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(8) { c ->
                        val i = r * 8 + c
                        // Short last row keeps its cells the width of every other row's rather
                        // than stretching to fill.
                        if (i < totalRounds) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(percent = 34))
                                    .background(Color.White.copy(alpha = alpha(i))),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        // Beyond that even a grid is a wall of dots, so it degrades to one bar — still stepping
        // per round, just no longer drawn one-per-round.
        else -> Box(
            Modifier
                .fillMaxWidth(0.52f)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(done.toFloat() / totalRounds)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }
    }
}

/** Rows of eight pips before the grid gives up and becomes a plain bar. */
private const val MAX_PIP_ROWS = 4

private fun fittedSp(
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: Density,
    text: String,
    family: FontFamily,
    weight: FontWeight,
    availWPx: Float,
    availHPx: Float,
    minSp: Float,
    maxSp: Float,
): Float {
    if (text.isEmpty() || availWPx <= 0f || availHPx <= 0f) return minSp
    // Fit in dp, NOT sp. Above font scale 1.03 Android scales sp non-linearly, and this measured
    // once at 100sp then extrapolated as if it were linear. On this phone (font scale 1.15) 100sp
    // is 100dp but 19sp is 20.8dp, so a ratio taken at the 100sp end under-predicts the drawn
    // width by ~10% at tile sizes — which is exactly why "восемь" ran off a language tile while
    // single glyphs, clamped to the 30sp cap where the curve is flat, always looked fine.
    // dp is linear; the density's own converter turns the answer back into the sp that renders at
    // that dp. Clamped in dp too, so the same non-linearity can't creep back in at the limits.
    val refDp = 100.dp
    val measured = measurer.measure(
        androidx.compose.ui.text.AnnotatedString(text),
        style = androidx.compose.ui.text.TextStyle(
            fontSize = with(density) { refDp.toSp() },
            fontWeight = weight,
            fontFamily = family,
        ),
        maxLines = 1,
        softWrap = false,
    )
    // Both dimensions from the INK, not the line box. A line box carries the font's full ascent and
    // descent, and fallback faces for mark-stacking scripts like Arabic reserve enormous vertical
    // room for those marks — so ٥ measured nearly as tall as 五 while drawing a third of the ink, and the
    // height budget throttled it to a speck. Ink is what the eye judges as "fills the box".
    //
    // Width has to come from the ink too, not the advance: mixing the two let a Han clock fit on
    // advance width while its glyphs drew past the screen edge.
    val ink = inkBoundsPx(text, with(density) { refDp.toPx() }, weight, family)
    val wPx = ink?.first ?: measured.size.width.toFloat()
    val hPx = ink?.second ?: measured.size.height.toFloat()
    if (wPx <= 0f || hPx <= 0f) return minSp
    val ratio = minOf(availWPx / wPx, availHPx / hPx)
    return with(density) { (refDp * ratio).coerceIn(minSp.dp, maxSp.dp).toSp().value }
}

/**
 * Width and height of the drawn glyphs alone, in px, at [sizePx], or null if nothing was drawn.
 * Measured through android.graphics because Compose only reports the line box; both go through the
 * same system font fallback, so the ratio this feeds is sound even where the exact face differs.
 */
private fun inkBoundsPx(text: String, sizePx: Float, weight: FontWeight, family: FontFamily): Pair<Float, Float>? {
    if (sizePx <= 0f) return null
    val paint = android.graphics.Paint().apply {
        textSize = sizePx
        typeface = android.graphics.Typeface.create(
            if (family == FontFamily.Monospace) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT,
            if (weight.weight >= 600) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
        )
    }
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    if (bounds.width() <= 0 || bounds.height() <= 0) return null
    return bounds.width().toFloat() to bounds.height().toFloat()
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
    // Words only stand in for languages with no numerals of their own — native-glyph scripts
    // (Thai, Hindi, Chinese…) already look distinct, so they just show their glyphs. The ceiling
    // is per-language: a minute for the Latin ones, far more for Korean, which composes its own.
    val showWords = Settings.wordMode && !ui.done && lang.digits == null &&
        !lang.cistercian && !lang.stacks && ui.remainingMs < 60_000

    // Space the big number may occupy, in px. 26dp of side margin: the fit fills its width budget
    // exactly, and a three-glyph Han line is width-bound, so at 14dp 五十八 came to rest a pixel
    // from the perimeter stroke. Latin is height-bound and doesn't notice the difference.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    val availWPx = with(density) { (wDp - 52f).coerceAtLeast(1f).dp.toPx() }
    val availHPx = with(density) { (hDp * 0.42f).coerceAtLeast(1f).dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        // The number owns the true middle of the screen, regardless of the labels riding up top.
        // Finished or paused it steps aside entirely — the centred "Done"/"Paused" takes that spot.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Everything below this point runs only while the clock is live: done and paused both
            // step aside for the centred "Done"/"Paused", so ui.done is always false from here.
            if (ui.done || ui.paused) Unit
            else if (lang.cistercian) {
                // One glyph for the whole count, not M:SS — the cipher packs four digits into a
                // single figure, so the clock is total seconds and never stacks or resizes.
                val side = minOf(wDp - 52f, hDp * 0.42f).coerceAtLeast(1f).dp
                CistercianNumeral(
                    cistercianSeconds(ui.remainingMs),
                    Modifier.size(side),
                    strokeWidth = side / 18f,
                )
            } else if (showWords) {
                val word = Numbers.words(ui.remainingMs, lang)
                // Remembered on the word: the state ticks at 30fps but the word changes once a
                // second, and fittedSp is a real main-thread text layout.
                val wordSize = remember(word, lang, availWPx, availHPx, density) {
                    fittedSp(measurer, density, word, FontFamily.Default, glyphWeight(lang), availWPx, availHPx, 24f, 150f)
                }.sp
                Text(word, color = Color.White, fontSize = wordSize, fontWeight = glyphWeight(lang), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            } else {
                val lines = Numbers.clockLines(ui.remainingMs, lang)
                val clockFont = if (lang.digits == null && !lang.stacks) FontFamily.Monospace else FontFamily.Default
                val clockWeight = glyphWeight(lang)
                // ONE size for the whole interval, fitted to the widest value the interval will
                // ever reach — not to what happens to be on screen this second.
                //
                // Fitting live meant the number resized whenever the glyph count changed: in Han,
                // 三十九 is three glyphs and 四十 is two, so every crossing of a ten made it lurch
                // bigger. Pinning trades that away — short values now sit smaller in the middle of
                // the screen — for a count that holds still, which is what you want on something
                // you glance at mid-set.
                //
                // Keyed on the interval, so this measures once per interval rather than per second.
                // Both lines of a stacked clock take the smaller fit, or MM and SS would disagree.
                val clockSize = remember(ui.intervalDurationMs, lang, availWPx, availHPx, density) {
                    val widest = Numbers.widestClockLines(ui.intervalDurationMs, lang)
                    val perLineH =
                        if (widest.size > 1) availHPx * 1.3f / widest.size * 0.87f else availHPx
                    widest.minOf {
                        fittedSp(measurer, density, it, clockFont, clockWeight, availWPx, perLineH, 32f, 260f)
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

        // Just the phase label up top now; the count and its progress live at the bottom, so the
        // big number owns the whole middle of the screen.
        Text(
            label,
            color = Color.White,
            fontSize = labelSize,
            fontWeight = glyphWeight(lang),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )

        // How far through the workout, down at the bottom: the count, then a pip per repetition.
        // Sits above the hold-to-pause hint, which keeps its own 44dp.
        if (ui.totalRounds > 0) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val hasRound = ui.round > 0
                Text(
                    // In the chosen language's own numerals — this printed Western digits in every
                    // language, so a Chinese workout still counted "3 / 16" under 运动.
                    if (hasRound) "${Numbers.count(ui.round, lang)} / ${Numbers.count(ui.totalRounds, lang)}" else " ",
                    color = Color.White.copy(alpha = if (hasRound) 0.80f else 0f),
                    fontSize = counterSize,
                    // Monospace has no CJK/Indic/Thai glyphs, so glyph scripts take the default face
                    // — the same swap the big clock makes.
                    fontFamily = if (lang.digits == null) FontFamily.Monospace else FontFamily.Default,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(12.dp))
                OverallProgress(ui.round, ui.totalRounds)
            }
        }

        // Word mode used to print the digits again in small type down here as a glance-fallback,
        // behind a "Pure script" switch. Both are gone: showing a count twice on one screen is the
        // opposite of what word mode is for, and the setting existed only to turn off a mistake.
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
