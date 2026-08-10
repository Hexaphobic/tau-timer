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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
import com.chrispoole.intervaltimer.model.Pips
import com.chrispoole.intervaltimer.model.TimerUiState
import com.chrispoole.intervaltimer.model.Workout
import com.chrispoole.intervaltimer.model.baseWorkout
import com.chrispoole.intervaltimer.model.Block
import com.chrispoole.intervaltimer.model.SeqInterval
import com.chrispoole.intervaltimer.model.basicBlock
import com.chrispoole.intervaltimer.model.isBasic
import com.chrispoole.intervaltimer.model.formatMs
import com.chrispoole.intervaltimer.model.homePreset
import com.chrispoole.intervaltimer.model.homeSeconds
import com.chrispoole.intervaltimer.model.homeSets
import com.chrispoole.intervaltimer.model.homeWorkout
import com.chrispoole.intervaltimer.model.secLabel
import com.chrispoole.intervaltimer.model.toWorkout
import com.chrispoole.intervaltimer.service.TimerService
import com.chrispoole.intervaltimer.ui.AuraBackground
import com.chrispoole.intervaltimer.ui.AuraSwatch
import com.chrispoole.intervaltimer.ui.CistercianNumeral
import com.chrispoole.intervaltimer.ui.cistercianSeconds
import com.chrispoole.intervaltimer.ui.BackPill
import com.chrispoole.intervaltimer.ui.CloseX
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
import com.chrispoole.intervaltimer.ui.stepperSemantics

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
                var editBuiltin by remember { mutableStateOf<Preset?>(null) }
                // The home screen's sections. Hoisted here so a trip to Settings or Presets
                // doesn't wipe an in-progress sequence; seeded from the home as it was left.
                val homeRows = remember {
                    mutableStateListOf(
                        *Settings.home.mapIndexed { i, b -> HomeRow(i.toLong(), b) }.toTypedArray(),
                    )
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
                        onNew = { editIndex = null; editBuiltin = null; screen = "editor" },
                        onEdit = { idx -> editIndex = idx; editBuiltin = null; screen = "editor" },
                        onEditBuiltin = { p -> editIndex = null; editBuiltin = p; screen = "editor" },
                    )
                    screen == "editor" -> EditorScreen(
                        initial = editIndex?.let { PresetStore.saved.getOrNull(it) } ?: editBuiltin,
                        // Leave the editor as the workout starts. Its draft lives in plain
                        // `remember`s inside this branch, so the running timer takes it out of
                        // composition and it is gone — and on End the branch recomposed from
                        // `initial`, silently showing the *original* preset (or a blank Work 30 /
                        // Rest 15) as though it were the user's unsaved work, ready for Save to
                        // write it over the top. Landing on Presets is honest about the loss.
                        onStart = {
                            launchWorkout(it.toWorkout(Settings.prepareSec * 1000L))
                            screen = "presets"
                        },
                        onSave = { p ->
                            val idx = editIndex
                            val original = editBuiltin
                            when {
                                idx != null -> PresetStore.update(idx, p)
                                // Editing a built-in: the copy becomes a saved preset and the
                                // original goes away by name — the same mechanism deleting one
                                // uses — so the list ends up holding the edited version once.
                                original != null -> {
                                    PresetStore.add(p)
                                    Settings.hideBuiltin(original.name)
                                    PresetStore.pushToWatch()
                                }
                                else -> PresetStore.add(p)
                            }
                            screen = "presets"
                        },
                        onCancel = { screen = "presets" },
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
        // A launch that never connected has no workout to run, and no service?.stop() can reach it
        // — the started service would sit in the foreground holding a wake lock forever.
        if (isFinishing && pending != null) stopService(Intent(this, TimerService::class.java))
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
data class HomeRow(val id: Long, val b: Block)

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
        CloseX { onClose() }
        Spacer(Modifier.width(6.dp))
        // Nothing typed, nothing to save — that's the whole gate, no second step.
        GlassPill("Save", onSave, enabled = value.isNotBlank())
    }
}

/** The group box's lazy key: it looks itself up in the layout to know where it was placed. */
private const val GROUP_BOX = "groupbox"

// stickyHeader — the one thing that keeps the group frame composed while the stack it frames
// scrolls past it, and inside the layer the stretch overscroll is applied to. Experimental in
// Compose 1.7; stable since.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SetupScreen(
    rows: SnapshotStateList<HomeRow>,
    onGo: (Workout) -> Unit,
    onSettings: () -> Unit,
    onPresets: () -> Unit,
) {
    // The classic home — three bare steppers, no box — is exactly one section holding work and
    // rest. Add a second section OR a third interval and every section folds into its own card,
    // because at that point there is a shape to show and the plain layout has nowhere to show it.
    // items.size == 2, not just isBasic: a section can be whittled down to a lone Work by deleting
    // its Rest row inside the card, and isBasic still says yes. Falling back to the plain home there
    // strands you — the plain home has no + interval, so there is no control left that puts the Rest
    // back. A one-interval section keeps its card and its way out.
    val solo = rows.size == 1 && rows[0].b.items.size == 2 && rows[0].b.isBasic
    // Two different lines, and they are not the same line. `solo` is about *chrome* — one plain
    // work/rest section wears no card. `grouped` is about *repeats*: an outer ×N only means anything
    // once there is more than one section to wrap. A single section holding work/work/rest gets a
    // card but no group, because its own repeat already is the total and a second number governing
    // one thing is a number you can only get wrong.
    val grouped = rows.size > 1
    var repeatAll by remember { mutableIntStateOf(Settings.homeRepeatAll) }
    val effectiveRepeatAll = if (grouped) repeatAll else 1
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    if (saved) LaunchedEffect(Unit) { delay(2200); saved = false }

    fun change(i: Int, b: Block) {
        // Guard the captured index: a second tap can land before recomposition.
        if (i !in rows.indices) return
        rows[i] = HomeRow(rows[i].id, b)
    }

    // The whole home is remembered, not just the first section's three numbers — a section can hold
    // a sequence of its own now, and dropping that on relaunch would be the same bug as never saving
    // it. Mirrored from the live list rather than written on the edit path: drag, the move-up/down
    // actions and delete all change the list without going through change().
    // A name only means anything when there is another section to tell it apart from, which is
    // exactly why the tag lives in the header and the header only arrives with `grouped`. Delete
    // down to one and the name would otherwise survive, ride onto the timer, and have nowhere on
    // screen left to edit it from. Stripped here rather than at each delete because this is the one
    // list both the save and the workout read, so neither can disagree with the other.
    val layout = if (grouped) rows.map { it.b } else rows.map { it.b.copy(name = "") }
    LaunchedEffect(layout) { if (layout.isNotEmpty()) Settings.updateHome(layout) }

    fun setRepeatAll(n: Int) {
        repeatAll = n.coerceAtLeast(1)
        Settings.updateHomeRepeatAll(repeatAll)
    }

    // Which section is being typed into, and the tick that re-arms its focus request so a second tag
    // press re-summons a keyboard that was dismissed without Done. One id, so opening a second
    // section's row ends the first's turn — the same single slot iOS keeps.
    val focus = LocalFocusManager.current
    var namingId by remember { mutableStateOf<Long?>(null) }
    var nameTick by remember { mutableIntStateOf(0) }
    // The name row's growth is already animated, from inside the card (chromeIn's expandVertically).
    // A lazy item's placement animation is for *insertions and reorders* — it moves an item from
    // where it was to where it now is. Pointed at a neighbour that is growing, it retargets every
    // frame and therefore trails the growth by its own duration: the card below stayed put while the
    // row opened above it, then slid down late, and for those 260ms it sat over the card that was
    // still expanding. Placement animation off while a row is opening, open, or closing; layout then
    // places the card below exactly where the growth puts it, every frame, and the two move as one.
    val nameGrow by animateFloatAsState(
        if (namingId != null) 1f else 0f,
        tween(260, easing = FastOutSlowInEasing),
        label = "nameGrow",
    )
    // ponytail: the same gate covers the collapse, so `nameGrow` is only read for its tail — it has
    // to finish falling before the tween comes back, or the shrink trails exactly as the growth did.
    // Known ceiling: a delete or a reorder *while* a row is open snaps instead of sliding, because the
    // gate is a blunt on/off for the whole list. Nobody deletes a section with the keyboard up; if
    // that ever stops being true, the gate needs to know which item is growing rather than that one is.
    val placeSpec: FiniteAnimationSpec<IntOffset>? =
        if (namingId != null || nameGrow > 0f) null
        else tween(260, easing = FastOutSlowInEasing)

    /**
     * The number on screen has to keep meaning the same thing across the one-to-two boundary.
     *
     * With one section, Rounds *is* that section's repeat. With two, it is the outer ×N. Adding a
     * section therefore lifts the number you were already looking at up to the group and leaves the
     * sections at 1 — otherwise "4" would quietly start meaning "each section, four times" and the
     * workout would come out sixteen rounds long instead of four. Removing folds it back down the
     * same way, so the round trip is lossless.
     */
    fun addBlock() {
        val nextId = (rows.maxOfOrNull { it.id } ?: 0L) + 1
        val last = rows.lastOrNull()?.b ?: return
        if (rows.size == 1) {
            setRepeatAll(last.repeat)
            rows[0] = HomeRow(rows[0].id, last.copy(repeat = 1))
            // The shape copies; the name doesn't — it names the section it's on, not the next one.
            rows += HomeRow(nextId, last.copy(repeat = 1, name = ""))
        } else {
            rows += HomeRow(nextId, last.copy(name = ""))
        }
    }

    fun removeBlock(i: Int) {
        // size > 1, not just a bounds check: the ✕ only exists once there are two sections, so
        // "never remove the last one" is the real invariant. Two ✕ taps landing in one frame both
        // passed a bare bounds check and took rows to empty, where the footer's + reads rows.last().
        if (rows.size <= 1 || i !in rows.indices) return
        rows.removeAt(i)
        if (rows.size == 1) {
            val one = rows[0]
            rows[0] = HomeRow(one.id, one.b.copy(repeat = (one.b.repeat * repeatAll).coerceAtLeast(1)))
            setRepeatAll(1)
        }
    }

    // What the one Rounds control reads and writes, whichever side of that boundary we are on.
    val homeRounds = if (grouped) repeatAll else rows.firstOrNull()?.b?.repeat ?: DEFAULT_ROUNDS
    fun setHomeRounds(n: Int) {
        val v = n.coerceAtLeast(1)
        if (grouped) setRepeatAll(v) else rows.firstOrNull()?.let { change(0, it.b.copy(repeat = v)) }
    }

    val listState = rememberLazyListState()
    // The drag works in *list* indices, so every item above the cards displaces them: the header row,
    // the summary and the group-box spacer always, plus the save control once the cards are showing.
    // Three of those four arrived after this count was first written and it was never updated — a
    // stale range here clamps a lifted card to the top of the page and lets nothing swap (PUNCHLIST
    // §42). The group's ×N sits below the cards and doesn't count. Everything that reads a card
    // index reads it from *here*, so there is one number to keep honest rather than three.
    val firstCard = if (solo) 3 else 4
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
        // No safeDrawingPadding on the container — same reason as Presets and Settings. Inset here,
        // the list's own top edge lands under the camera and scrolled cards are cut off against it,
        // so a section slides up and vanishes early at a line the screen gives no reason for. The
        // list uses the whole panel and simply travels past the cutout; the inset moves into its
        // content padding, so what's at rest — the header row included, since that is the list's
        // own first item — still starts clear of the camera.
        Box(Modifier.fillMaxSize()) {
            val groupFill = GlassFill.copy(alpha = GlassFill.alpha * 0.45f)
            // A page that fits has nothing to scroll, so it should not answer a drag at all — the
            // plain home bounced on the stretch overscroll and read as a page with more below it.
            // Asked of the list rather than of `solo`, so it is true whenever the content genuinely
            // fits, not just for the one shape we expect to. derivedStateOf so this recomposes on
            // the answer changing, not on every scroll that changes canScrollForward at an end.
            val scrollable by remember {
                derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = scrollable,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    // 4, not 56: the whole header row is the list's own first item now and carries
                    // the rest of that gap as its height, so everything below it starts exactly
                    // where it always did. 4 is what the row used to sit at as an overlay.
                    top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 4.dp,
                    bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 56.dp,
                ),
                verticalArrangement = CenterUnderHeader,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The whole header row is the list's first item: Presets and Settings scroll away
                // with the page exactly as Δτ does, because that is all "leaving the top of the
                // screen" ever needed to mean. Pinned over the scroll they overlapped the cards
                // passing under them, and everything that followed — a fraction off the scroll
                // offset, then accumulated deltas, a commit on release, an in-bias near the top —
                // was scaffolding to fake this for two buttons.
                //
                // The 52.dp is the space the row used to hold open as an overlay, so nothing below
                // it moved. The offsets undo the TextButton's own 14.dp so the labels land 18.dp
                // from the edge, where they have always been, rather than 34 in from the gutter.
                item(key = "mark") {
                    // No offset trickery here: CenterUnderHeader (the list's arrangement) pins this
                    // row in the same layout pass it centres everything else. The old version undid
                    // the centring with an offset { } that read listState.layoutInfo — which is
                    // published *after* measure, so while add/remove animations re-centred the page
                    // every frame, the header was always placed from the previous frame's layout
                    // and visibly wobbled. Arrangement math has no such lag.
                    Box(Modifier.fillMaxWidth().height(52.dp)) {
                        TextButton(
                            onPresets, "Presets",
                            Modifier.align(Alignment.TopStart).offset(x = (-16).dp),
                        )
                        Text(
                            "Δτ",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        )
                        TextButton(
                            onSettings, "Settings",
                            Modifier.align(Alignment.TopEnd).offset(x = 16.dp),
                        )
                    }
                }
                if (!solo) {
                    item(key = "save") {
                        // animateContentSize turns the swap into the button growing into the
                        // field, rather than one control vanishing and another appearing.
                        Column(Modifier.animateItem(placementSpec = placeSpec).animateContentSize(tween(260))) {
                            if (naming) {
                                NameField(
                                    value = name,
                                    onValueChange = { name = it },
                                    onClose = { naming = false; name = "" },
                                    onSave = {
                                        PresetStore.add(
                                            homePreset(layout, effectiveRepeatAll)
                                                .copy(name = name.trim()),
                                        )
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
                // What GO will run, at the top where you read it before you start rather than at the
                // bottom where it sat on the button. Sets, not "rounds": the control below already
                // owns that word and means the other thing by it — this is the count the timer will
                // walk you through, the one the pips draw.
                item(key = "summary") {
                    val sets = homeSets(layout, effectiveRepeatAll)
                    Text(
                        "$sets ${if (sets == 1) "set" else "sets"}  ·  ${clock(homeSeconds(layout, effectiveRepeatAll))}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.animateItem(placementSpec = placeSpec),
                    )
                }
                // The group box: one rounded frame drawn around the ×N header and every section
                // under it, so "repeat all of this" is a thing you can see rather than a sentence
                // you have to read. Painted from the list's own layout rather than composed around
                // the items, because the sections are separate lazy items — that's what makes
                // drag-reorder work, and a card carried out of the stack must not take a slice of
                // the frame with it.
                //
                // It is drawn by an item *in* the list, and specifically by a sticky one, for a
                // reason that is entirely about the Android 12 stretch. LazyColumn hands the
                // caller's modifier out at the very top and only then wraps its own
                // clipScrollableContainer → overscroll → scrollable around the content, so the
                // stretch RenderEffect lands on a layer this frame used to sit *outside* of: the
                // cards stretched at the end of a scroll and the box behind them stood still. Drawn
                // from inside the list it is under that layer with everything else, and stretches
                // for free — no second overscroll, no effect to keep in sync.
                //
                // Sticky, because a lazy item is only composed while its own slot is on screen and
                // this one has to keep drawing while the stack it frames scrolls past: a sticky
                // header stays composed and placed for as long as anything below it is showing.
                // Its 14.dp is the gap that used to be the summary's bottom padding — the same
                // empty space, now with a job — so nothing moved to make room for it.
                stickyHeader(key = GROUP_BOX) {
                    Spacer(
                        Modifier
                            // A sticky header is deliberately placed *last* of all the items,
                            // because a real one has to overlay the rows sliding under it. This one
                            // is the opposite of a real one: it is the surface everything else sits
                            // on, and placed last its fill tinted every card it was drawn over. The
                            // -1 puts it under the whole stack — same lever the lifted card uses to
                            // ride over its neighbours, pushed the other way.
                            .zIndex(-1f)
                            .fillMaxWidth()
                            .height(14.dp)
                            .drawBehind {
                                if (!grouped) return@drawBehind
                                val info = listState.layoutInfo
                                // Everything the frame is measured from is a list offset; this
                                // item's own offset is what turns those into the draw origin here.
                                // Read from layoutInfo like all the rest of it — sticky means
                                // "pinned to the top of the viewport", not "where the layout says",
                                // and only layoutInfo knows which of the two this frame is.
                                val me = info.visibleItemsInfo.firstOrNull { it.key == GROUP_BOX }
                                    ?: return@drawBehind
                                val range = firstCard..firstCard + rows.size
                                val members = info.visibleItemsInfo.filter { it.index in range }
                                if (members.isEmpty()) return@drawBehind
                                val pad = 8.dp.toPx()
                                // Scrolled past an end, that end simply runs off screen: clamping
                                // to the last visible item would draw a rounded corner in the
                                // middle of the stack.
                                val top = if (members.any { it.index == range.first }) {
                                    members.minOf { it.offset } - me.offset - pad
                                } else info.viewportStartOffset - me.offset - pad * 40
                                val bottom = if (members.any { it.index == range.last }) {
                                    members.maxOf { it.offset + it.size } - me.offset + pad
                                } else info.viewportEndOffset - me.offset + pad * 40
                                // No gutter arithmetic left: this item is laid out inside the
                                // list's content padding, so its own width already *is* the gutter.
                                // The cards are inset `pad` inside it by their own margin, which is
                                // what keeps the frame `pad` clear of them on all four sides and
                                // lined up with the full-width pills above and below.
                                val rect = Offset(0f, top)
                                val box = Size(size.width, bottom - top)
                                val radius = CornerRadius(36.dp.toPx(), 36.dp.toPx())
                                drawRoundRect(groupFill, rect, box, radius)
                                drawRoundRect(
                                    Color.White.copy(alpha = 0.14f), rect, box, radius,
                                    style = Stroke(1.dp.toPx()),
                                )
                            },
                    )
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
                            // On the item's own root, not on the card inside it: zIndex only orders
                            // siblings of the layout it is applied to, so down here it was ordering
                            // this Box's single child against nothing, and the lazy list went on
                            // drawing the items in index order. A card dragged downward was
                            // therefore painted *under* the one it was passing, which read as it
                            // ducking behind the background and coming back once the swap landed.
                            .zIndex(if (floating) 1f else 0f)
                            // No animateContentSize: it clips to the size it is animating, and the
                            // section's own height is already animated from the inside (see
                            // HomeSection), so all it did was slice the card off mid-row while the
                            // box formed. The lazy list re-lays out every frame as that height
                            // changes, which is what moves everything below along with it.
                            //
                            // The fade-in waits for the shuffle. A new item takes its full space the
                            // instant it is added, and everything around it — the first section
                            // closing into its card, Rounds and GO sliding down — spends the next
                            // ~quarter second animating through that space. Drawn immediately, the
                            // new card sat overlapped with the rows still moving out of its slot.
                            // So: everything gets out of the way first, then the card fades into the
                            // gap. The fade is long enough to read as an arrival, not a pop — which
                            // is what an earlier, shorter delayed fade got wrong.
                            .then(
                                if (floating) Modifier
                                else Modifier.animateItem(fadeInSpec = tween(260, delayMillis = 240), placementSpec = placeSpec),
                            ),
                    ) {
                    // ONE composable whether it's the plain home or a card — never two branches of
                    // an `if`. Two branches is what made "Add intervals" a dissolve: Compose threw
                    // away the work and rest rows you were looking at and built a fresh pair inside
                    // a card. Kept as one, they are the *same* rows the whole way through, and the
                    // only thing that animates is the box closing around them.
                    val lifted = !solo && dragDrop.isLifted(row.id)
                    val scale by animateFloatAsState(if (lifted) 1.03f else 1f, label = "lift")
                    HomeSection(
                        b = row.b,
                        boxed = !solo,
                        // The header carries the drag grip, the per-section ×N, the section's own
                        // running time and the ✕ — every one of which is meaningless when there is
                        // only one section. It arrives with the second one.
                        showHeader = grouped,
                        lifted = lifted,
                        naming = namingId == row.id,
                        focusTick = nameTick,
                        onTag = {
                            // Folding the typing now has to put the keyboard away by hand. The row
                            // no longer leaves composition when the typing stops — a name stays on
                            // the card — so the field is still there holding focus, and nothing
                            // would take it. Cleared HERE, at the one place that decides a fold, and
                            // never from inside the card: pressing another section's tag also ends
                            // this one's turn, and a card clearing focus on its own way out would
                            // yank it back off the card that had just asked for it.
                            if (namingId == row.id) { namingId = null; focus.clearFocus() }
                            else { namingId = row.id; nameTick++ }
                        },
                        onDone = { namingId = null },
                        onEdit = { namingId = row.id },
                        onChange = { change(i, it) },
                        // Not while a card is in the air. Deleting the section under the finger —
                        // or the last one keeping the group grouped — takes the drag handle with it
                        // mid-gesture, and nothing is worth carrying that case: no one removes a
                        // section with a second finger while moving another one.
                        onRemove = { if (!dragDrop.isDragging) removeBlock(i) },
                        handle = {
                            DragHandle(
                                key = row.id,
                                label = "section ${i + 1}",
                                state = dragDrop,
                                // Narrower than its stock 44dp: the dots are only 12dp of ink, and
                                // the dead width on their left pushed the whole header cluster
                                // right — this hands ~10dp back to the pencil's slot. 36×44 of
                                // touch target, the same width the glass steppers get.
                                modifier = Modifier.width(36.dp),
                                onMoveUp = if (i > 0) ({ rows.add(i - 1, rows.removeAt(i)); Unit }) else null,
                                onMoveDown = if (i < rows.lastIndex) ({ rows.add(i + 1, rows.removeAt(i)); Unit }) else null,
                            )
                        },
                        // The card under the finger is placed by hand; the wrapper above lets
                        // the lazy list animate everything else aside.
                        modifier = Modifier
                            .graphicsLayer {
                                translationY = dragDrop.offsetFor(row.id)
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                    }
                }
                // The home's Rounds, and the ONE place it lives on this screen. Plain, it sits
                // under the work and rest rows lined up with them; grouped, it walks to the centre
                // of the group box and grows. Same composable, same number, all the way through —
                // which is the whole point, because the rounds you were already setting are the
                // rounds the group runs.
                item(key = "rounds") {
                    HomeRounds(
                        rounds = homeRounds,
                        grouped = grouped,
                        onMinus = { m -> setHomeRounds(homeRounds - m) },
                        onPlus = { m -> setHomeRounds(homeRounds + m) },
                        onReset = { setHomeRounds(DEFAULT_ROUNDS) },
                        modifier = Modifier.animateItem(placementSpec = placeSpec),
                    )
                }
                item(key = "footer") {
                    Column(Modifier.animateItem(placementSpec = placeSpec)) {
                        if (!solo) {
                            // Clear of the group frame's bottom edge — the + adds a section *to* the
                            // group, so it sits just outside it rather than inside.
                            Spacer(Modifier.height(18.dp))
                            GlassPill("+", ::addBlock, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(20.dp))
                        }
                        GlassPill(
                            "GO",
                            {
                                // The same builder the total above it is measured from, so the
                                // number and the button can never describe different workouts.
                                onGo(
                                    homeWorkout(
                                        layout,
                                        effectiveRepeatAll,
                                        Settings.prepareSec * 1000L,
                                    ),
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
            // These two float over the scroll, so they carry the inset the container no longer
            // does: the list may pass under the camera, the controls never do.
            //
            if (saved) {
                NoticePill(
                    "Saved to presets",
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 28.dp),
                )
            }
        }
    }
}

/**
 * Arrangement.Center, except the first item — the Presets/Δτ/Settings row — sits at the very top.
 *
 * The content stays centred on a short home; the header alone is pinned to the top edge. Lazy lists
 * only consult the arrangement when the content fits the viewport, so the moment the page can
 * scroll, items stack normally and the header travels with the scroll like everything else — which
 * is exactly the rule the old offset hack was approximating, minus its one-frame lag.
 */
private val CenterUnderHeader = object : Arrangement.Vertical {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        var y = ((totalSize - sizes.sum()) / 2).coerceAtLeast(0)
        for (i in sizes.indices) {
            outPositions[i] = y
            y += sizes[i]
        }
        if (outPositions.isNotEmpty()) outPositions[0] = 0
    }
}

/**
 * One section, plain or boxed — deliberately ONE composable rather than two branches of an `if`.
 *
 * [boxed] false is the classic home: the same work and rest rows, with no chrome at all around them
 * and Rounds as a plain stepper underneath. True wraps them in the card, moves the count up into the
 * header, and tints the rows. Everything about that change is animated from the *same* rows, which
 * is the whole point — "Add intervals" should look like a box closing around what is already on
 * screen, and coming back should look like that box being let go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeSection(
    b: Block,
    boxed: Boolean,
    showHeader: Boolean,
    // Held by the list, not by the card. The row's growth changes this item's height, and the items
    // below it have to be told to stop animating their own placement while it happens — see
    // `placeSpec` at the call site. Hoisting it is also what makes "only one section is ever being
    // named" true rather than merely intended.
    naming: Boolean,
    focusTick: Int,
    onChange: (Block) -> Unit,
    onRemove: () -> Unit,
    onTag: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    handle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    lifted: Boolean,
) {
    val shape = RoundedCornerShape(28.dp)
    // 0 on the plain home, 1 as a card. Everything the box is made of rides on this one number, so
    // the chrome arrives and leaves as a single move rather than as four separate ones.
    //
    // A State, deliberately, and never unwrapped with `by`. Every read below happens inside a
    // layout or draw lambda, so the 260ms it spends animating re-lays-out and re-draws this card
    // without ever recomposing it. Unwrapped, the reads land in composition and the whole section
    // rebuilds on every frame of the crossing — see `paddingBy` for the numbers.
    val box = animateFloatAsState(
        if (boxed) 1f else 0f,
        tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "box",
    )
    // Same lift language as the presets editor: brighten the glass and light its edge. No elevation
    // shadow — a platform shadow assumes an opaque caster and skips the part of itself the card is
    // meant to hide, so through glass you see the ring it *did* draw and a hard-edged rectangle
    // where the skipped part ends (PUNCHLIST §44). On this near-black background the shadow was
    // worth almost nothing anyway; brightening the glass is what says "held above".
    val fill by animateColorAsState(if (lifted) Color.White.copy(alpha = 0.20f) else GlassFill, label = "fill")
    val edge by animateFloatAsState(if (lifted) 0.5f else 0f, label = "edge")
    // The phase colours fade in as the card lands, rather than popping alongside it.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(boxed) { appear.animateTo(if (boxed) 1f else 0f, tween(320)) }
    Column(
        modifier
            // Outside the card's own background: this is the gap between the card and the group
            // frame drawn around the whole stack, so it has to be margin, not padding.
            .paddingBy(8.dp, 0.dp, bottom = 10.dp) { box.value }
            .fillMaxWidth()
            // The fill and the edge, drawn rather than composed. `background()` and `border()` both
            // take their colour as an argument, so an alpha riding `box` had to be computed in
            // composition; drawn here the same alpha is read per frame with nothing rebuilt.
            .drawBehind {
                val k = box.value
                if (k <= 0.001f) return@drawBehind
                val r = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                drawRoundRect(fill.copy(alpha = fill.alpha * k), Offset.Zero, size, r)
                val edgeAlpha = if (lifted) edge * k else 0.10f * k
                drawRoundRect(
                    Color.White.copy(alpha = edgeAlpha), Offset.Zero, size, r,
                    style = Stroke(1.dp.toPx()),
                )
            }
            .paddingBy(12.dp, 10.dp) { box.value },
    ) {
        SectionNameRow(
            b = b,
            enabled = showHeader,
            naming = naming,
            focusTick = focusTick,
            onChange = onChange,
            onDone = onDone,
            onEdit = onEdit,
        )
        // The lid of the box: it arrives with the box and leaves with it. Its *height* is animated,
        // not just its alpha — an `if` around it made the card jump a whole row taller the instant
        // the box started forming, and the wrapper outside then had to chase that jump.
        AnimatedVisibility(showHeader, enter = chromeIn(Alignment.Top), exit = chromeOut(Alignment.Top)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                handle()
                // Hoisted only so the number can offer the same two edits as accessibility actions
                // without a second copy of them to keep in step.
                val less: (Int) -> Unit = { m -> onChange(b.copy(repeat = (b.repeat - m).coerceAtLeast(1))) }
                val more: (Int) -> Unit = { m -> onChange(b.copy(repeat = b.repeat + m)) }
                GlassCircle("−", less, size = 36.dp)
                Text(
                    "× ${b.repeat}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    // No section number in the label: the drag handle immediately before it in
                    // traversal order already says "Reorder section 2", and this header only exists
                    // when there is more than one section for it to number.
                    modifier = Modifier
                        .width(52.dp)
                        .stepperSemantics("Section repeats", "${b.repeat} times", less, more),
                )
                GlassCircle("+", more, size = 36.dp)
                // The tag, in the header's dead middle. Just the trigger — the name itself lives
                // in the row that expands out of the card's top.
                // A toggle: open-and-focus, or fold the keyboard away again. The list owns which.
                SectionTag(onTag)
                // How long this block is — the intervals under it, run the × N to its left. M:SS,
                // not formatMs: these are block lengths now rather than shares of the whole workout,
                // so most of them are under a minute, and a bare "50" in a column under "1:30" reads
                // as anything but fifty seconds.
                Text(
                    clock(sectionSeconds(b)),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(6.dp))
                CloseX { onRemove() }
            }
        }
        IntervalStack(b, { appear.value }, onChange, compact = boxed, t = { box.value })
    }
}

/**
 * The row a section's name is typed into — "Splits", "Pistol squats" — sitting above everything else
 * in the card.
 *
 * Shared by the home's sections and the sequence editor's group cards, which are the same object
 * seen on two screens: one field, so the 40-character cap, the caret behaviour and the Done key
 * cannot drift apart between them. The caller owns [naming] and [focusTick], because it is the one
 * that knows which of its cards the tag was pressed on.
 *
 * [enabled] is the home's "only once there is a header to hang it under"; the editor passes true.
 *
 * A NAME STAYS ON SCREEN. Visibility is [naming] *or* a name to show: the tag only ever opens this
 * row, and closing it stops the typing rather than hiding what you typed — a name you can't see is
 * one you have to open a keyboard to check. The row goes away when the name does, so clearing the
 * text and folding is what takes it off the card, and deleting the section takes it off for good.
 * Tapping the name itself is the other way back in, which is why focus reports up through [onEdit].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionNameRow(
    b: Block,
    naming: Boolean,
    focusTick: Int,
    onChange: (Block) -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    enabled: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    // The caret is a function of FOCUS, and hiding the keyboard does not take focus away — so
    // dismissing the IME any way other than our own Done left the field focused and the cursor
    // blinking inside a box that could no longer receive a keystroke. The keyboard is the honest
    // signal here ("only there when the keyboard's up"), so watch it and drop focus when it goes.
    //
    // The `seen` latch is load-bearing: requesting focus is what *summons* the keyboard, so for a
    // frame or two after the tag is pressed the IME is still down. Reacting to that would clear
    // the focus that was about to open it — the row would flicker and close on its own. Only a
    // keyboard that has actually been up can count as one that has gone away.
    //
    // And it must be a keyboard THIS field was holding. isImeVisible is a window-wide fact: every
    // card on the screen sees every keyboard, so a latch armed by the neighbour's typing fired the
    // moment the neighbour's keyboard went down. That is precisely the gap between closing one tag
    // and opening the next — press the top card's tag, then the bottom one's, and the row you just
    // opened closed itself and took the keyboard with it. Arming on `focused` scopes the latch to
    // the field that actually summoned the IME; a card that merely witnessed one never arms at all.
    val imeUp = WindowInsets.isImeVisible
    var focused by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(naming, imeUp, focused) {
        // Folded away: nothing to close, and the latch must not survive into the next opening.
        if (!naming) {
            seen = false
            return@LaunchedEffect
        }
        if (imeUp && focused) {
            seen = true
            return@LaunchedEffect
        }
        // The keyboard this field was holding has gone — swiped away, or handed to another card.
        if (seen) {
            seen = false
            onDone()
            focusManager.clearFocus()
        }
    }
        // The name, above everything else in the card — tag pressed, this expands out of the
        // top on the same chrome animation the header lid rides, and the card grows to make room
        // exactly the way it does for a new interval. A name then simply stays put, in plain sight
        // on the card, until it is cleared: `b.name.isNotEmpty()` is what keeps this row up after
        // the typing stops.
        AnimatedVisibility(
            enabled && (naming || b.name.isNotEmpty()),
            enter = chromeIn(Alignment.Top),
            exit = chromeOut(Alignment.Top),
        ) {
            val focus = remember { FocusRequester() }
            // TextFieldValue rather than the plain String overload, for one reason: the caret.
            // With a String the field owns the selection, and a programmatic focus lands it at
            // position 0 — tapping the tag on an already-named section put the caret BEFORE
            // "Splits", so typing prepended. Owning the value lets each tag press park it at
            // the end, which is where an edit to a name starts.
            var tf by remember { mutableStateOf(TextFieldValue(b.name, TextRange(b.name.length))) }
            // The block is the source of truth; the field follows it. Covers this remember's
            // slot being reused for a different section after a delete above shifts the list.
            if (tf.text != b.name) tf = TextFieldValue(b.name, TextRange(b.name.length))
            // Keyed on the tick, not on `naming`: each pencil press re-arms the request, so the
            // keyboard comes back even when a swipe already dismissed it with naming still true.
            // On first composition of an already-named card naming is false, so nothing grabs.
            LaunchedEffect(focusTick) {
                if (naming) {
                    tf = tf.copy(selection = TextRange(tf.text.length))
                    focus.requestFocus()
                }
            }
            BasicTextField(
                value = tf,
                // 40. The old 16 was set when this was a strip wedged between the steppers with one
                // line to spend; on its own full-width row that was needlessly mean. 40 is picked to
                // be the largest cap that CANNOT clip in any script, which retires the
                // character-vs-row mismatch outright rather than just making it rarer. Measured on
                // device at 15sp in this field, not estimated:
                //
                //   Latin  ~26 per row → 40 fills 2 rows
                //   CJK     15 per row → 40 fills 3 rows exactly (15 + 15 + 10)
                //
                // So CJK is the binding constraint and the ceiling is 45; 40 keeps five characters
                // of headroom. Raising this much past 40 puts Chinese text into a fourth row the
                // field refuses to draw — visibly swallowing input it has actually stored. A bigger
                // cap buys only names nobody writes: a section is one exercise, not a sentence.
                //
                // Newlines are flattened to spaces: wrapping is the layout's call, so a name can't
                // reach the timer carrying hard breaks.
                onValueChange = { new ->
                    val text = new.text.replace("\n", " ").take(40)
                    // Untouched input passes through whole, composition ranges and all; only a
                    // clamped one is rebuilt, with the caret held in place rather than thrown
                    // to the end of a name being edited in the middle.
                    tf = if (text == new.text) new
                    else TextFieldValue(text, TextRange(minOf(new.selection.end, text.length)))
                    onChange(b.copy(name = text))
                },
                // Wraps rather than scrolling sideways. 3 rather than 2 because only CJK ever needs
                // the third row, and clipping it there is exactly the bug this cap exists to avoid.
                singleLine = false,
                maxLines = 3,
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone(); focusManager.clearFocus() }),
                // animateContentSize so the second and third rows arrive the way everything else in
                // this card does — grown into, not snapped. Same 260ms the box itself rides.
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    // What scopes the IME latch above to this field rather than to the window. And
                    // now that a named row sits on the card with the typing over, focus arriving is
                    // its own way in: tap the name and the list is told to treat it as being edited,
                    // which is what re-arms the latch that puts the caret away afterwards.
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused && !naming) onEdit()
                    }
                    .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp)
                    .animateContentSize(tween(260, easing = FastOutSlowInEasing)),
                decorationBox = { inner ->
                    // Top-aligned, not centred: on a two- or three-row name the placeholder is
                    // long gone, but centring would also drift the first row down as the field
                    // grows, which reads as the text sliding rather than a row being added.
                    Box(contentAlignment = Alignment.TopStart) {
                        if (b.name.isEmpty()) {
                            Text("Name", color = Color.White.copy(alpha = 0.35f), fontSize = 15.sp, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
        }
}

/**
 * The button that opens a section's name row: a luggage tag, in the header's dead middle.
 *
 * A RowScope extension so it carries the flexible slot that used to be a bare Spacer in the middle
 * of the header. Shared by the home and the sequence editor, for the reason on [SectionNameRow].
 */
@Composable
internal fun RowScope.SectionTag(onClick: () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    // requiredSize, NOT size: the leftover slot here can be narrower
                    // than the circle, and Modifier.size coerces into what the parent
                    // leaves — measured on device, this drew 24×30, a visible ellipse.
                    // requiredSize keeps it square and centers over the slot instead.
                    // 36dp — the same circle as the steppers beside it.
                    .requiredSize(36.dp)
                    .clip(CircleShape)
                    .background(GlassFill)
                    .border(1.dp, glassBorder(), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                // A luggage tag, drawn rather than typed — same as CloseX and GripDots,
                // and for the same reason: no font is guaranteed to carry a glyph, and
                // Samsung's ✎ is a 3D-shaded pencil that turns to mush at this size.
                //
                // The action is "name this section", not "edit" — a tag says that, and
                // unlike every pencil in every icon set it points right on its own,
                // with no mirroring against convention.
                //
                // Five points on Lucide's 24-unit grid: flat top and left edges meeting
                // the squared-off corner that holds the punch hole, then the long
                // diagonals out to the point. cornerPathEffect rounds all five at once
                // — a round join alone only softens by half the stroke width, which
                // came out sharper than the reference.
                Canvas(Modifier.size(18.dp)) {
                    val u = size.minDimension / 24f
                    val c = Color.White.copy(alpha = 0.85f)
                    val tag = Path().apply {
                        moveTo(12f * u, 2f * u)
                        lineTo(2f * u, 2f * u)
                        lineTo(2f * u, 12f * u)
                        lineTo(13f * u, 23f * u)
                        lineTo(23f * u, 13f * u)
                        close()
                    }
                    drawPath(
                        tag,
                        c,
                        style = Stroke(
                            width = 2f * u,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.cornerPathEffect(2.4f * u),
                        ),
                    )
                    // The punch hole, filled: an outlined ring this small is two
                    // concentric circles a pixel apart, which just reads as grey.
                    drawCircle(c, radius = 1.4f * u, center = Offset(7.6f * u, 7.6f * u))
                }
        }
    }
}

/**
 * The home's Rounds: how many times the whole thing runs, top to bottom.
 *
 * ONE control across both shapes, deliberately — never a plain one that leaves and a group one that
 * arrives. With a single section this is that section's own repeat; with more than one it is the
 * outer ×N governing all of them, and the screen moves the number across that boundary so it never
 * changes under you. Keeping it as one composable is what makes "Add intervals" read as *the rounds
 * you were already setting becoming the rounds for the group*, rather than as one control being
 * thrown away and a different one appearing somewhere else.
 */
@Composable
private fun HomeRounds(
    rounds: Int,
    grouped: Boolean,
    onMinus: (Int) -> Unit,
    onPlus: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deferred exactly like HomeSection's `box`, and for the same measured reason: as a plain
    // composition read this one row cost 9ms on the 90th-percentile frame all by itself (23ms with
    // it, 14ms with it made instant).
    val g = animateFloatAsState(
        if (grouped) 1f else 0f,
        tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "grouped",
    )
    // pointerInput(Unit) launches its gesture loop once, on the first touch, and reads the handler
    // it was given at that moment — a constant key means it is never restarted. This item is keyed
    // in the lazy list, so it survives the solo↔grouped crossing, and a lambda captured on the
    // plain home went on writing to the *section's* repeat long after Rounds had become the group's.
    // Same hazard GlassCircle already guards against, same fix.
    val reset by rememberUpdatedState(onReset)
    // A hand-written Layout rather than a Row with animated weights.
    //
    // Weights were the honest way to say "walk this control from left-aligned to centred", but a
    // weight is a composition-time argument, so every frame of that walk rebuilt the row and both
    // glass circles inside it. Placing the two pieces by hand moves the same walk into the layout
    // pass, where `g` can be read per frame for free.
    //
    // Every size below rides `g` too, through a scale rather than through the font size. Snapped on
    // `grouped` the number went big in one frame and only then walked to the middle, which is
    // backwards — it should grow ON THE WAY. Composed at its grouped size and scaled, it does, and
    // still costs no recomposition. The number is the one place a scale can't say it alone: the
    // digits grow 24→30 while the box around them SHRINKS 96→78, so the box gets its own
    // interpolated width and the text scales inside it.
    Layout(
        content = {
            Text(
                "Rounds",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .scaledBy { 20f / 18f + (1f - 20f / 18f) * g.value }
                    .padding(vertical = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val circle: () -> Float = { 54f / 50f + (1f - 54f / 50f) * g.value }
                Box(Modifier.scaledBy(circle)) {
                    GlassCircle("−", onMinus, size = 50.dp)
                }
                Box(
                    Modifier.widthBy(96.dp, 78.dp) { g.value },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$rounds",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .scaledBy { 24f / 30f + (1f - 24f / 30f) * g.value }
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) }
                            .stepperSemantics("Rounds", "$rounds", onMinus, onPlus),
                    )
                }
                Box(Modifier.scaledBy(circle)) {
                    GlassCircle("+", onPlus, size = 50.dp)
                }
            }
        },
        modifier = modifier.fillMaxWidth().paddingBy(8.dp, 0.dp) { g.value },
    ) { measurables, constraints ->
        // minWidth = 0, NOT the incoming constraints. fillMaxWidth() makes the width TIGHT, and
        // handing that straight to a child forces it to fill the row — which stretched both pieces
        // to the full width, drove the centred origin negative, and put the label off the left edge
        // with the + hanging off the right.
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val label = measurables[0].measure(loose)
        val cluster = measurables[1].measure(loose)
        val k = g.value
        val w = constraints.maxWidth
        // Plain sits well clear of GO below it; grouped tucks up inside the frame.
        val top = ((16.dp.toPx() * (1 - k)) + (4.dp.toPx() * k)).roundToInt()
        val bot = ((32.dp.toPx() * (1 - k)) + (2.dp.toPx() * k)).roundToInt()
        val h = maxOf(label.height, cluster.height)
        layout(w, h + top + bot) {
            // Plain: label hard left, cluster hard right — lined up with the Work and Rest rows
            // above it. Grouped: the pair travels to the middle as one unit with a 12dp gap, which
            // is what stops the label butting against the − once the slack runs out.
            val gap = (12.dp.toPx() * k).roundToInt()
            val centred = (w - label.width - gap - cluster.width) / 2
            val labelX = (centred * k).roundToInt()
            val clusterX = ((w - cluster.width) * (1 - k) + (centred + label.width + gap) * k).roundToInt()
            label.place(labelX, top + (h - label.height) / 2)
            cluster.place(clusterX, top + (h - cluster.height) / 2)
        }
    }
}

/**
 * How the card's chrome comes and goes: height first, alpha slightly behind it.
 *
 * Height, not just alpha, is the whole point. Both the header and the Rounds stepper used to be
 * behind an `if`, so each one appeared or vanished at full size in a single frame and left the card
 * to jump. Animating the size here means the section's own height is continuous, and the lazy list
 * carries everything below it along for free.
 */
private fun chromeIn(from: Alignment.Vertical) =
    fadeIn(tween(200, delayMillis = 60)) +
        expandVertically(tween(260, easing = FastOutSlowInEasing), expandFrom = from)

private fun chromeOut(towards: Alignment.Vertical) =
    fadeOut(tween(140)) +
        shrinkVertically(tween(260, easing = FastOutSlowInEasing), shrinkTowards = towards)

/**
 * A section's intervals, one row each, and — on a card — the + that adds another.
 *
 * Shared by the card and by the plain single-section home, so the two can't drift. [compact] is doing
 * double duty as "this is a card": the plain home is deliberately just work, rest and rounds, so it
 * gets the rows and nothing else. No +, no ✕, no tap-to-flip. Building a section with a shape of its
 * own is what "Add intervals" is for, and that is where all of it lives.
 */
@Composable
private fun IntervalStack(
    b: Block,
    /** The tint's alpha, as a lambda so the rows fade without recomposing. */
    appear: () -> Float,
    onChange: (Block) -> Unit,
    compact: Boolean = true,
    /** The box-forming fraction — the steppers' sizes ride it so they shrink WITH the card.
     *  A lambda for the same reason `appear` is one: read at layout, never in composition. */
    t: () -> Float = { if (compact) 1f else 0f },
) {
    fun set(j: Int, iv: SeqInterval) =
        onChange(b.copy(items = b.items.toMutableList().also { it[j] = iv }))

    b.items.forEachIndexed { j, iv ->
        val isWork = iv.phase == Phase.WORK
        Spacer(Modifier.height(6.dp))
        Stepper(
            if (isWork) "Work" else "Rest",
            secLabel(iv.durationSec),
            // Work has a 5s floor; rest keeps its 0, because dialling rest to nothing has always
            // been how you say "no rest here" and the row stays put so you can dial it back.
            { m -> set(j, iv.copy(durationSec = (iv.durationSec - 5 * m).coerceAtLeast(if (isWork) 5 else 0))) },
            { m -> set(j, iv.copy(durationSec = iv.durationSec + 5 * m)) },
            onReset = { set(j, iv.copy(durationSec = if (isWork) DEFAULT_WORK_SEC else DEFAULT_REST_SEC)) },
            number = j + 1,
            compact = compact,
            t = t,
            tint = if (isWork) WorkColor else RestColor,
            tintAlpha = appear,
            onLabelClick = if (compact) {
                // Flipping to Work re-applies the 5s floor the minus stepper enforces: a rest
                // dialled to 0 and then flipped was the one way to build a 0-second work interval,
                // which the timer never plays but every set count still counts.
                {
                    set(
                        j,
                        if (isWork) iv.copy(phase = Phase.REST)
                        else iv.copy(phase = Phase.WORK, durationSec = iv.durationSec.coerceAtLeast(5)),
                    )
                }
            } else null,
            // Only once there is something to remove — a section with one interval is the floor. On
            // the plain home, dialling a rest to 0 is how you drop it, same as it has always been.
            // `b.items.size > 1` alone — NOT `compact &&`. Gated on compact it popped out of
            // existence the moment a remove started, narrowing the cluster in one frame at the far
            // end of the same crossing. Composed throughout and scaled by `t`, it grows in and
            // shrinks away with the card, and on the plain home `t` is 0 so it costs no width.
            trailing = if (b.items.size > 1) {
                { CloseX { onChange(b.copy(items = b.items.filterIndexed { k, _ -> k != j })) } }
            } else null,
        )
    }
    if (compact) {
        Spacer(Modifier.height(4.dp))
        TextButton(
            { onChange(b.copy(items = b.items + nextInterval(b.items))) },
            "+ interval",
            Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * What the + adds: alternate by default, because work/rest/work/rest is the shape nearly every
 * section wants. Tap the row's label afterwards to make it the other one — which is how you get
 * work, work, rest.
 */
private fun nextInterval(items: List<SeqInterval>): SeqInterval =
    if (items.lastOrNull()?.phase == Phase.WORK) SeqInterval(Phase.REST, DEFAULT_REST_SEC)
    else SeqInterval(Phase.WORK, DEFAULT_WORK_SEC)

/**
 * How long this block is: its intervals, run its own ×N. Nothing else.
 *
 * It used to be the block's *share of the workout* — multiplied by the home's rounds as well, and
 * with the last one docking the closing rest the timer won't play. Both were defensible and both
 * were wrong to put here: the number sits in the same row as the block's own "× 2", so it has to
 * describe the same thing that × 2 does. Multiplying by the outer rounds made a block's length
 * change when you touched a control somewhere else entirely, and docking the rest made two identical
 * blocks read as different lengths depending on which one was last. What the whole thing costs, with
 * the rounds and the dropped rest, is the line at the top of the screen — one place, not eight.
 */
private fun sectionSeconds(b: Block): Long =
    b.items.sumOf { it.durationSec }.toLong() * b.repeat

/** A total, so always M:SS — a bare "45" under a minute reads as a count, not a duration. */
private fun clock(sec: Long): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

@Composable
private fun TextButton(onClick: () -> Unit, text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
    }
}

/**
 * Every size a stepper row changes across the plain↔card crossing, as one ratio.
 *
 * label 15/20 = .750, value 17/24 = .708, circles 40/54 = .741, label box 66/90 = .733,
 * value box 68/96 = .708 — every one of them within 3% of the same number. Five independent
 * dimensions that are really one, which is what makes a single scale able to stand in for all of
 * them and lets the whole row cross on the GPU instead of through the measure pass.
 */
private const val CardScale = 0.728f

/**
 * Draw a subtree at [s] and tell the parent the room it now takes — WITHOUT re-measuring it.
 *
 * This is the whole performance fix. Animating fontSize, widths and circle diameters directly
 * re-ran text measurement and re-rasterised every glyph on every frame, for two Text nodes per
 * row, two rows per section — which measured out at 27–48ms a frame against the 8.33ms this
 * 120Hz panel actually allows, so the app missed two vsyncs out of every three and the crossing
 * arrived in visible steps. A scale is a matrix the GPU applies to a raster that already exists:
 * the glyphs are measured once, at the size they will come to rest at.
 *
 * Order matters. `graphicsLayer` is outer and `layout` inner, so `layout` measures the content at
 * its natural size and reports `s ×` that upward, while the layer scales the drawing by the same
 * `s` about the top-left. Node size and drawn size therefore agree, and the row occupies exactly
 * the space it looks like it occupies. Origin is top-left rather than centred because the Row
 * places these with SpaceBetween — the label is flush left and the cluster flush right whatever
 * width they report, so anchoring anywhere else would fight the arrangement.
 *
 * Top-left is also the ONLY origin that registers correctly here, which cost a round trip to learn.
 * The pivot is a fraction of the node's size, and this node reports `s ×` its content — so a centred
 * pivot sits at `s·W/2` while the content's own centre is at `W/2`, and the drawing comes out offset
 * by `(s-1)·W/2`. At (0,0) the content spans 0..W, scales to 0..sW, and matches the reported size
 * exactly. Centring is the parent's job, done with the size this reports.
 */
private fun Modifier.scaledBy(s: () -> Float) =
    graphicsLayer {
        val v = s()
        scaleX = v
        scaleY = v
        transformOrigin = TransformOrigin(0f, 0f)
    }.layout { measurable, constraints ->
        val v = s()
        val p = measurable.measure(constraints)
        layout((p.width * v).roundToInt(), (p.height * v).roundToInt()) { p.place(0, 0) }
    }

/** A width that interpolates at layout time, for the one case a scale can't express: a box whose
 *  content grows while the box itself shrinks. */
private fun Modifier.widthBy(from: Dp, to: Dp, f: () -> Float) =
    layout { measurable, constraints ->
        val w = (from.toPx() + (to.toPx() - from.toPx()) * f()).roundToInt()
        val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(w, p.height) { p.place(0, 0) }
    }

/**
 * Padding whose amount is read at LAYOUT time rather than at composition time.
 *
 * This is the other half of the same fix, and the more important half. `Modifier.padding(lerp(a, b,
 * box))` reads `box` while the composable is running, so every frame of the 260ms crossing marked
 * the whole section — its header, both stepper rows, every glass circle — as needing to recompose.
 * Profiling the transition put the Choreographer's `animation` phase, which is where Compose ticks
 * animations and then recomposes, at 25ms on its worst frame; measure, layout and draw together
 * never exceeded 16ms, and the GPU sat flat at 3ms the whole time. Making `box` and `g` instant
 * dropped the 90th percentile frame from 32ms to 8ms and missed vsyncs from 13 to 2, which is what
 * identified recomposition rather than drawing as the cost.
 *
 * Read inside the `layout` lambda, the same value re-runs layout alone and skips composition
 * entirely. The animation is unchanged; only what it invalidates is.
 */
private fun Modifier.paddingBy(h: Dp, v: Dp, bottom: Dp = 0.dp, f: () -> Float) =
    layout { measurable, constraints ->
        val k = f()
        val px = (h.toPx() * k).roundToInt()
        val py = (v.toPx() * k).roundToInt()
        val pb = (bottom.toPx() * k).roundToInt()
        val p = measurable.measure(constraints.offset(-2 * px, -(2 * py + pb)))
        layout(p.width + 2 * px, p.height + 2 * py + pb) { p.place(px, py) }
    }

/** Double-tapping the number puts it back to the stock value — the way out of a hold that overshot. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: (Int) -> Unit,
    onPlus: (Int) -> Unit,
    onReset: () -> Unit,
    /** Which row this is, for the spoken label — "Work" alone repeats inside a section. */
    number: Int,
    compact: Boolean = false,
    /** The plain↔card size fraction. [compact] flips at the *start* of that transition, so sizing
     *  off the boolean snapped the circles and digits a whole animation ahead of the card they sit
     *  in. The fraction is the same `box` value the card's own chrome rides. */
    t: () -> Float = { if (compact) 1f else 0f },
    /** The phase colour at FULL alpha — a stable value, so the pill's gradient shader is built once
     *  and not once per frame. How much of it shows is [tintAlpha]. */
    tint: Color? = null,
    /** The tint's alpha, read per frame inside the draw lambda so fading it recomposes nothing. */
    tintAlpha: () -> Float = { 1f },
    /** Tap the label to flip work↔rest. Its own hit box, well clear of the steppers: the editor
     *  learned the hard way that a whole-row tap turns a near-miss on − into a phase change. */
    onLabelClick: (() -> Unit)? = null,
    /** Sits at the right-hand end, inside the pill — the ✕ that removes this interval. */
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    // pointerInput(Unit) reads the handler it was given on the node's first composition and is never
    // restarted, so the double-tap kept resetting through a stale onReset — one closed over an older
    // Block, an older row index and an older onChange, which put the whole section back as it was and
    // silently reverted every other edit. Same hazard GlassCircle and HomeRounds already guard, same fix.
    val reset by rememberUpdatedState(onReset)
    // The row is always composed at the size it is going to REST at — `compact` picks that, and it
    // flips the instant the crossing starts. What animates is how far from that size the row is
    // currently drawn: coming in as a card it starts a touch over 1 and settles to 1; going back to
    // the plain home it starts at CardScale and opens out to 1. Either way it ends at exactly 1.0,
    // so the text at rest is rasterised at its true size and never resampled — the scale only exists
    // during the 260ms nobody is reading it.
    val scale: () -> Float = {
        val k = t()
        if (compact) 1f + (1f / CardScale - 1f) * (1f - k) else 1f + (CardScale - 1f) * k
    }
    val minimal = Settings.minimalBg
    // An invisible tint is not a pill. The plain home hands these rows the phase colour at alpha 0 so
    // it can fade up when the box arrives — but they were still paying the pill's 12dp inset either
    // side for a pill nobody can see. That cost the row 24dp it needed: Modifier.size coerces into
    // whatever the parent leaves, so the last child, the + circle, was quietly clamped to 48dp inside
    // a 54dp box and drew as an ellipse. It also sat Work and Rest 12dp in from Rounds.
    // Whether the pill exists at all is still a composition-level question — a plain row genuinely
    // has no pill and must not pay for its 12dp inset — but it is asked of `compact`, which flips
    // once per crossing, not of an alpha that changes every frame.
    val visibleTint = tint?.takeIf { compact }
    // Only glow rows pay for an animation clock; plain and minimal rows never start one.
    val drift = if (visibleTint != null && !minimal) {
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
                    visibleTint == null -> Modifier
                    // Minimal mode's timer is black with only the perimeter stroke, so the preview
                    // is the same idea: a bubble around the edge, nothing filled in.
                    minimal -> Modifier
                        .drawBehind {
                            drawRoundRect(
                                visibleTint.copy(alpha = tintAlpha() * 0.55f),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                style = Stroke(1.5.dp.toPx()),
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp)
                    // Otherwise: what GO actually shows is a full wash of the phase colour. So the
                    // row is filled edge to edge, and the only movement is dips in brightness
                    // drifting across it. No shape inside the pill — a shape is what read as a
                    // sticker sitting on the row instead of the row being lit.
                    else -> Modifier
                        .clip(shape)
                        // Built in the cache block, not the draw body: Compose caches the native
                        // LinearGradient inside the Brush instance, so a Brush built per frame is a
                        // shader allocated per frame — and the drift transition invalidates this at
                        // display refresh rate, for every tinted row, the whole time the home shows
                        // a boxed section. Same trap SplitProgress documents.
                        .drawWithCache {
                            // Full alpha in the stops, and the fade applied at draw. The stops used
                            // to carry the animating alpha, so the cache block — whose whole job is
                            // to build the native shader ONCE — was invalidated on every frame of
                            // the fade, allocating a gradient per row per frame. Now the only thing
                            // that changes per frame is a float multiplier.
                            val a = 1f
                            // Repeated so it never seams; first and last stop match, and one cycle
                            // of `drift` slides it exactly one span, so the loop is invisible. The
                            // span sits at -span..0 rather than following `shift`, so translating
                            // the canvas by `shift` puts it exactly where startX = shift - span used
                            // to, with no wrapping — and drawRect gets a constant size, which is
                            // what keeps the cached shader valid frame to frame.
                            val span = size.width * 1.5f
                            val brush = Brush.horizontalGradient(
                                0.00f to visibleTint.copy(alpha = a * 0.58f),
                                0.32f to visibleTint.copy(alpha = a * 0.43f),
                                0.66f to visibleTint.copy(alpha = a * 0.60f),
                                1.00f to visibleTint.copy(alpha = a * 0.58f),
                                startX = -span,
                                endX = 0f,
                                tileMode = TileMode.Repeated,
                            )
                            onDrawBehind {
                                // drift is read here and never in the cache block — captured up
                                // there it would rebuild the brush every frame, which is the cost
                                // this is removing.
                                val alpha = tintAlpha()
                                val shift = drift!!.value * span
                                translate(left = shift) {
                                    drawRect(brush, topLeft = Offset(-shift, 0f), size = size, alpha = alpha)
                                }
                                // The edge, drawn with the wash rather than composed as a border,
                                // so the two fade together off one per-frame read.
                                drawRoundRect(
                                    visibleTint.copy(alpha = alpha * 0.28f),
                                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                    style = Stroke(1.dp.toPx()),
                                )
                            }
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
            modifier = Modifier
                .scaledBy(s = scale)
                // The fixed width is the label COLUMN — it keeps every row's steppers aligned — but
                // the tap pill must not be that box: clipped to it, the ripple ran the full 90dp
                // while the word sat flush left, so "Work" looked shoved into the pill's corner.
                // wrapContent shrinks the pill to the word; the offset pays for the pill's own
                // horizontal padding, so the glyph itself never moves and Rounds below still lines
                // up. The ripple now overhangs the column edge by that same 10dp — press-only, and
                // symmetric around the word, which is the whole point.
                .width(if (compact) 66.dp else 90.dp)
                .wrapContentWidth(Alignment.Start)
                .then(
                    if (onLabelClick == null) Modifier
                    else Modifier
                        .offset(x = (-10).dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onLabelClick() }
                        .padding(horizontal = 10.dp),
                )
                .padding(vertical = 6.dp),
        )
        Row(Modifier.scaledBy(s = scale), verticalAlignment = Alignment.CenterVertically) {
            GlassCircle("−", onMinus, size = if (compact) 40.dp else 54.dp)
            Text(
                value,
                color = Color.White,
                fontSize = if (compact) 17.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(if (compact) 68.dp else 96.dp)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) }
                    .stepperSemantics("Interval $number $label duration", value, onMinus, onPlus),
                textAlign = TextAlign.Center,
            )
            GlassCircle("+", onPlus, size = if (compact) 40.dp else 54.dp)
            // Scaled by the crossing fraction, not switched on by `compact`.
            //
            // This is what made the row "teleport to the left and then shrink". The cluster is
            // placed flush right, so its width sets where the − starts. Appearing at full width the
            // instant `compact` flipped, the ✕ made the cluster ~30dp wider in a single frame and
            // shoved everything left before any of it had begun to move. Grown from nothing on the
            // same clock as the card, the arithmetic comes out exact: (40+68+40+2) × 1/0.728 = 206dp,
            // against the plain row's 54+96+54+2 = 206dp. The cluster now starts precisely where it
            // already was.
            trailing?.let {
                // The gap goes INSIDE the scaled box with the ✕, not beside it. Left outside, its
                // 2dp survived at t = 0 and sat the plain home's whole −/value/+ cluster 2dp left
                // of where it had always been.
                Box(Modifier.scaledBy { t() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(2.dp))
                        it()
                    }
                }
            }
        }
    }
}

// ---- Settings ----

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val current = Language.of(Settings.languageCode)
    Box(Modifier.fillMaxSize()) {
      HomeBackground(Modifier.fillMaxSize())
      // The pill floats over the scroll rather than sitting above it, so the content passes
      // underneath instead of being cut off at a hard edge. Declared after the scroll so it is
      // hit-tested first.
      //
      // No safeDrawingPadding on the container, deliberately: reserving the camera cutout's band
      // walled off the top of the screen and cost the menu that much height for a hole it only
      // overlaps in the middle. The content uses the whole panel and runs past the cutout; only
      // the pill — the one control up there — keeps its own inset, so it never lands under the
      // camera.
      Box(Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            // Clear of the pill at rest; scrolled, the content simply travels under it.
            .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 60.dp),
    ) {
        SettingsCard("Settings") {
            ToggleRow("Mute", Settings.muted) { Settings.updateMuted(it) }
            Spacer(Modifier.height(16.dp))
            Text("Volume", color = Color.White, fontSize = 18.sp)
            VolumeSlider()
            Spacer(Modifier.height(16.dp))
            ToggleRow("Run in background", Settings.runInBackground) { Settings.updateRunInBackground(it) }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Get ready", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val less: (Int) -> Unit = { m -> Settings.updatePrepareSec(Settings.prepareSec - 5 * m) }
                    val more: (Int) -> Unit = { m -> Settings.updatePrepareSec(Settings.prepareSec + 5 * m) }
                    // One reading for both the eye and TalkBack: "Off" is what zero shows, so the
                    // spoken value must not be a bare "0s" the screen never displays.
                    val ready = if (Settings.prepareSec == 0) "Off" else secLabel(Settings.prepareSec)
                    GlassCircle("−", less)
                    Text(
                        ready,
                        color = Color.White,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(64.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { Settings.updatePrepareSec(DEFAULT_PREPARE_SEC) })
                            }
                            .stepperSemantics("Get ready", ready, less, more),
                    )
                    GlassCircle("+", more)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Minimal rides the title line rather than sitting under the grid. Orthogonal to the palette
        // on purpose — Minimal + Vesper is a black timer with Vesper on the edge, and Mono as well
        // gives the plain black-and-white one — so it belongs to the whole card, not to the row of
        // swatches it used to hang below.
        SettingsCard(
            "Theme",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Minimal", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Switch(Settings.minimalBg, { Settings.updateMinimalBg(it) })
                }
            },
        ) {
            PalettePicker()
        }

        Spacer(Modifier.height(16.dp))

        // Its own panel rather than a dropdown inside Theme: the grid is the biggest thing on this
        // screen, and burying it behind a disclosure row made it feel like a footnote.
        SettingsCard("Language") {
            // Fixed-height box, not a bare Text. This string changes script with the selection, and
            // every script's font reports its own ascent/descent — Latin, CJK and Arabic all measure
            // differently at 15sp — so an unconstrained Text made this one line resize the card and
            // shove the grid below it. lineHeight does not fix that: on a single line the resolved
            // font's metrics still decide the height. A fixed box takes the measurement out of the
            // font's hands entirely; wrapContentHeight(unbounded) then lets tall glyphs draw past it
            // instead of being clipped, so the layout is frozen but nothing is cut off.
            Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    current.english,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.wrapContentHeight(unbounded = true),
                )
            }
            Spacer(Modifier.height(16.dp))
            // One line, and short enough to stay one. The second sentence explained a rule you can
            // see for yourself the moment you flip it — the Chinese tile keeps 九 either way — and
            // it was the longest string on the screen to say it. "≤60s" rather than "under 60s
            // only": the symbol says it in two characters, and what it buys is vertical space
            // between this row and the grid, not brevity for its own sake.
            ToggleRow("Word mode", Settings.wordMode, sub = "Thirty-two, not 32  ( ≤60s )") { Settings.updateWordMode(it) }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(16.dp))
            LanguageGrid()
        }
      }
      BackPill(
          onBack,
          Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = 16.dp, top = 8.dp),
      )
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
            // A short last row is centred, half a hole either side, rather than left-aligned against
            // one big gap. The spacers keep the tiles at the same width as every other row — laying
            // the row out without them would stretch two tiles across the full width instead.
            val holes = 3 - row.size
            if (holes > 0) Spacer(Modifier.weight(holes / 2f))
            row.forEach { lang -> LanguageTile(lang, second, Modifier.weight(1f)) }
            if (holes > 0) Spacer(Modifier.weight(holes / 2f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LanguageTile(lang: Language, second: Int, modifier: Modifier = Modifier) {
    val selected = lang.code == Settings.languageCode
    // A bubble, not a box: a percentage radius stays organic at any tile size, matching the
    // gradients drifting inside them rather than framing them in hard corners. Halved from 38 —
    // that much rounding was eating the corners of a tile whose whole job is to hold a numeral.
    val shape = RoundedCornerShape(percent = 19)
    // Follows the Word mode switch, which sits directly above this grid. It used to spell whatever
    // the setting said, on the grounds that English, Russian, Spanish and French otherwise print the
    // same Western 9 and the tiles would be indistinguishable. They aren't: the phase word up top is
    // already Work / Работа / Trabajo / Effort. Ignoring the switch made it look broken — you flip
    // it and the nine words under your thumb don't move.
    val text = if (Settings.wordMode && lang.digits == null && !lang.cistercian && !lang.stacks) {
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
            // instant and the grid read as one image stamped out once per tile.
            AuraSwatch(
                phaseColor,
                Modifier.fillMaxSize().clip(shape),
                // Both halves of the shared tile take Chinese's seed, so cycling relabels the tile
                // instead of restamping its aura — otherwise a tap reads as a different tile.
                seed = (if (lang.han) Language.ZH.ordinal else lang.ordinal) * 3.7f,
                // Full frame, unlike the theme stripes. A tile this size has room for the falloff to
                // read as depth behind the numeral rather than as the darkness it became at 35×40.
                zoom = 1f,
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
                // 3, down from 9. Measured on device: the glyphs are 6.7dp tall and sat 8dp below the
                // tile's coloured edge, so this lifts them by about their own height and leaves 2dp
                // of air. Any less and the ascender line would touch the tint.
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp),
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
        // Fixed height for the same reason as the card's own label above: the endonym is a different
        // script per tile, and selecting one also swaps it to bold — which for Arabic resolves a
        // different fallback face entirely. Either change moves the font's reported line height, and
        // an unconstrained Text would pass that straight into the Column, so one row of tiles would
        // sit lower than the others and the grid would jump on selection.
        Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
            Text(
                // Just the endonym: "中文 · Chinese" clips mid-string in a third-width tile, and the
                // native name is the more useful half here anyway.
                lang.english.substringBefore(" ·"),
                color = Color.White.copy(alpha = if (selected) 1f else 0.5f),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.wrapContentHeight(unbounded = true),
            )
        }
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
    // No heading of its own: the card it sits in is called Theme, and two of those in a row read as
    // a section inside a section.
    // Two across, not three. Width is the only dimension a swatch can grow in without looking
    // stretched — the stripes are the aura seen through a slot, and a tall thin slot shows less of
    // it than a wide one, not more. Two cells across a 280dp card put each stripe at 39dp instead of
    // 23, which is where a theme stops being a colour chip and starts being a picture of the screen.
    // The cost is five rows instead of three, on a page that already scrolls.
    Palette.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { p -> PaletteSwatch(p, Modifier.weight(1f)) }
            // Keeps a short final row left-aligned at the same cell width instead of stretching it.
            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PaletteSwatch(p: Palette, modifier: Modifier = Modifier) {
    val selected = p == Settings.palette
    val shape = RoundedCornerShape(12.dp)
    // 6dp off each side, which takes ~10% off every stripe. The shader corrects for aspect
    // (`p.x *= iResolution.x / iResolution.y`), so a box wider than it is tall reaches further out
    // into the falloff horizontally and the corners go black — at 39×36 that vignette was the first
    // thing you saw. Back at ~35×36 the stripe is square enough to sit inside the bloom.
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                // 48: the 44 it always was, plus 10%. Height alone was the wrong lever — at three
                // across it gave 23×54 slivers, and stretching a swatch the long way reads as a
                // distortion rather than as more of anything. With the width fixed first (two across,
                // then 10% back off it) the stripe is 35×40, and the extra height costs no vignette:
                // the shader's `p.x *= w/h` means a taller box reaches *less* far into the falloff.
                .height(48.dp)
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

/** @param trailing a control that belongs to the whole card, parked on its title line. */
@Composable
private fun SettingsCard(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), shape)
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
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
    // but not the OS's slow auto-dim, which was darkening the screen a few minutes in. Held
    // through the Done state too, because releasing on `done` visibly dimmed the finish; both
    // release together, either when the screen leaves or when the Done hold below runs out.
    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    // Done is not a workout. A phone put down on the Done screen instead of tapped was never
    // sleeping again — forced max brightness and a per-frame shader, for hours. So hold the finish
    // long enough to walk back and read it, then hand the display back to the OS. Not a fresh
    // timeout at that point: the screen-off clock runs from the last touch, so a phone genuinely
    // left alone sleeps as soon as the hold drops, which is the whole point of dropping it.
    var holdAwake by remember { mutableStateOf(true) }
    LaunchedEffect(ui.done) { if (ui.done) { delay(60_000); holdAwake = false } }
    DisposableEffect(view, holdAwake) {
        if (holdAwake) {
            view.keepScreenOn = true
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
        }
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

    // Hoisted out of the `if` below and left as a State rather than a value: animateFloatAsState
    // remembers an Animatable already sitting AT its target, so creating it inside a block that
    // only composes once a press begins started it at 1f with nothing to animate — the bar was
    // full on its first frame and never swept. Composed unconditionally it starts at 0f and the
    // press is a real target change. Reading `.value` in the draw phase also keeps the per-frame
    // sweep out of composition.
    val holdFill = animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(if (holding) HOLD_TO_PAUSE_MS.toInt() else 180, easing = LinearEasing),
        label = "hold",
    )

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
        // The Flip's cover screen is nearly square where the main screen is long: 474 x 523dp
        // against 360 x 840 (Samsung hands the app a 1422 x 1572 sandbox and scales it 0.667 onto
        // the 948 x 1048 panel, so neither dimension alone is a tell — the ratio is). On it the
        // phase label at w/7 ate the top of the screen the clock needed.
        val compact = h < w * COMPACT_ASPECT
        val labelSize = (w / (if (compact) 13f else 7.0f) / fontScale).coerceIn(16f, 72f).sp
        val counterSize = (w / (if (compact) 22f else 14f) / fontScale).coerceIn(11f, 34f).sp

        // Pause and finish never blur or dim this — they only swap out the centre text, so the
        // glow, the progress arms and the round counter stay exactly as they were.
        Box(Modifier.fillMaxSize()) {
            // Done keeps the glow at full bloom — letting the progress dim it made the whole
            // finish read as the screen going dark.
            AuraBackground(glow = glow, progress = if (ui.done) 1f else ui.fraction, modifier = Modifier.fillMaxSize())
            if (!ui.done) SplitProgress(remaining = 1f - ui.fraction, color = glow, modifier = Modifier.fillMaxSize())
            // Compact keeps the top and side insets but not the bottom one: the bottom inset is
            // the full width of the camera housing, and the strip to its left is the only place
            // left to put the round counter.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (compact) Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        ) else Modifier.safeDrawingPadding()
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
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

        // Bottom hint: fills while you hold so the 400ms press reads as progress, not a dead screen.
        if (!ui.done && !ui.paused && (holding || showHint)) {
            // Sits below the round counter and its pips, not on them: the hint is ~44dp tall, so at
            // the old 44dp offset its top edge landed 4dp under the progress grid and the
            // translucent pill read as covering it.
            // Compact keeps it left and lifts it clear of the counter: bottom-centre on the cover
            // screen is half under the camera housing.
            // Compact's 152dp clears the counter column above it: 30dp inset + ~25dp count line +
            // 6dp gap + Pips.MAX_ROWS × 17.75dp of pips + 3 × 4dp of gaps ≈ 145dp, and the clock's
            // own band stops 130dp up, so the hint sits in between rather than under the grid.
            HoldHint(
                holdFill,
                if (compact) Modifier.align(Alignment.BottomStart)
                    .padding(start = COMPACT_EDGE_DP.dp, bottom = 152.dp)
                else Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

/** "Hold to pause" pill; [fill] 0..1 sweeps a brighter bar across it as the hold progresses. */
@Composable
private fun HoldHint(fill: androidx.compose.runtime.State<Float>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, glassBorder(), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().drawBehind {
            drawRect(Color.White.copy(alpha = 0.22f), size = size.copy(width = size.width * fill.value))
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
    // Guarded here rather than at the two call sites: it's pure decoration, so reduced motion drops
    // the whole thing instead of freezing a frame of blurred blobs over the finish.
    if (Settings.reducedMotion) return
    // Dropped for the same reason and in the same place: Modifier.blur is RenderEffect-backed and a
    // silent no-op below API 31, so on Android 8–11 the "soft blooms" above are 38 hard-edged circles
    // per shell — a glitchy dot spray over the finish. ponytail: skip the decoration rather than keep
    // a second unblurred look in sync. The Done screen still has its full-bloom aura and its "Done".
    if (Build.VERSION.SDK_INT < 31) return
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
    var shell by remember { mutableIntStateOf(0) }
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
 * How far through the workout you are — one pip per work set, lit as each one lands. It steps with
 * the "1 / 7" counter above it and holds still in between; a bar that crept every second just
 * duplicated the countdown already filling the screen.
 *
 * Rows are the workout's own shape where it has one: four sets run twice is two rows of four, so the
 * grid says "and again" without a word on it. [roundsPerPass] carries that shape; 0 wraps by count.
 *
 * The set you are in the middle of breathes rather than sitting flat, so the grid says *where* you
 * are and not just how far. It stops the moment the clock does — a pip still pulsing on a paused
 * timer would be the screen telling you something is running when nothing is.
 *
 * White rather than the phase colour: it sits inside the phase-coloured aura, and a coloured bar on
 * a coloured wash reads as a smudge.
 */
@Composable
private fun OverallProgress(
    round: Int,
    totalRounds: Int,
    roundsPerPass: Int,
    /** Running, not paused and not finished — the only state in which anything should be moving. */
    live: Boolean,
    compact: Boolean,
) {
    // Compact already sits in a width-capped column beside the cameras, so the fractions that
    // keep this off a wide screen's edges would only shrink it twice.
    val span = if (compact) 1f else 0.52f
    val gridSpan = if (compact) 1f else 0.46f
    val done = round.coerceIn(0, totalRounds)
    // Only a live workout pays for an animation clock, the same bargain the Stepper glow makes. The
    // trough stays above the unlit 0.22 so the current pip never reads as one you haven't done yet.
    val breath = if (live && done > 0) {
        rememberInfiniteTransition(label = "pip").animateFloat(
            0.45f, 1f,
            infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else null
    // Read inside drawBehind, not passed to background(): a state read at draw time invalidates the
    // drawing and nothing else, where reading it in composition would rebuild all thirty-two boxes
    // sixty times a second to change one of them.
    fun alpha(i: Int) = when {
        i == done - 1 && breath != null -> breath.value
        i < done -> 0.85f
        else -> 0.22f
    }
    val layout = Pips.rows(totalRounds, roundsPerPass)
    if (layout.isEmpty()) {
        // Past the grid's ceiling even squares are a wall of dots, so it degrades to one bar —
        // still stepping per round, just no longer drawn one-per-round.
        Box(
            Modifier
                .fillMaxWidth(span)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(done.toFloat() / totalRounds.coerceAtLeast(1))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }
        return
    }
    BoxWithConstraints {
        val gap = 4.dp
        val available = maxWidth * gridSpan
        // One cell size for the whole grid, from the width a full row of eight needs — so three
        // rounds and a row of a twenty-four-round workout draw the same square, and the rows can
        // simply be centred. The second term only bites in the single-line case, where the count
        // is allowed to run past eight and the cells have to shrink to fit it.
        val widest = layout.max()
        val cell = minOf(
            (available - gap * (Pips.PER_ROW - 1)) / Pips.PER_ROW,
            (available - gap * (widest - 1)) / widest,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var base = 0
            layout.forEach { n ->
                val start = base
                base += n
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(n) { c ->
                        val i = start + c
                        Box(
                            Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(percent = 34))
                                .drawBehind { drawRect(Color.White.copy(alpha = alpha(i))) },
                        )
                    }
                }
            }
        }
    }
}

// Anything squatter than this is the cover screen: 523/474 = 1.10 against the main screen's 2.33.
private const val COMPACT_ASPECT = 1.4f
// The free strip left of the camera housing, which starts 214dp in from the left edge of a 474dp
// screen. Narrower than the gap so the last column keeps clear of the housing.
private const val COMPACT_COUNTER_WIDTH_DP = 170f
// Clearance from the screen edge, on top of the 14dp the content is already inset by. SplitProgress
// runs its arms 6dp in under an 18dp-wide glow stroke, so it owns the outer 15dp of every edge —
// the counter's bottom row was being drawn straight onto the arm along the bottom.
private const val COMPACT_EDGE_DP = 16f

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
    // (Hindi, Arabic, Chinese…) already look distinct, so they just show their glyphs. The ceiling
    // is per-language: a minute for the Latin ones, far more for Korean, which composes its own.
    val showWords = Settings.wordMode && !ui.done && lang.digits == null &&
        !lang.cistercian && !lang.stacks && ui.remainingMs < 60_000

    // Space the big number may occupy, in px. 26dp of side margin: the fit fills its width budget
    // exactly, and a three-glyph Han line is width-bound, so at 14dp 五十八 came to rest a pixel
    // from the perimeter stroke. Latin is height-bound and doesn't notice the difference.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    val availWPx = with(density) { (wDp - 52f).coerceAtLeast(1f).dp.toPx() }

    // Cover-screen layout. Its camera housing claims the bottom-right 198 x 84dp, which the
    // window's safe insets do NOT reserve — the clock was drawing straight over it, and the round
    // counter with it. Two bands keep the number clear: the label's at the top, the counter's at
    // the bottom, and the counter moves into the free strip to the left of the cameras.
    val compact = hDp < wDp * COMPACT_ASPECT
    val labelBand = if (compact) 46f else 0f
    // Deep enough to clear the camera housing (110dp), since compact draws into it, plus room
    // for the counter to sit clear of the progress arm along the bottom edge.
    val progressBand = if (compact) 130f else 0f
    // Tall screens keep the old fixed share; short ones take what the two bands leave, because
    // 42% of 399dp is a number nobody can read.
    val availHPx = with(density) {
        (if (compact) (hDp - labelBand - progressBand) * 0.95f else hDp * 0.42f)
            .coerceAtLeast(1f).dp.toPx()
    }

    Box(Modifier.fillMaxSize()) {
        // The number owns the true middle of the screen, regardless of the labels riding up top.
        // Finished or paused it steps aside entirely — the centred "Done"/"Paused" takes that spot.
        Box(
            Modifier.fillMaxSize().padding(top = labelBand.dp, bottom = progressBand.dp),
            contentAlignment = Alignment.Center,
        ) {
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
                    // fittedSp budgets INK, but a stacked column is laid out in LINE BOXES, which
                    // carry the font's full ascent and descent on top. A tall screen absorbs the
                    // difference; 359dp of cover screen does not, and the seconds line came out
                    // sliced in half. Compact reserves for the overhead instead of ignoring it.
                    val perLineH =
                        if (widest.size > 1) availHPx * (if (compact) 0.62f else 1.3f) / widest.size * 0.87f
                        else availHPx
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
        Column(
            Modifier.align(Alignment.TopCenter)
                .padding(top = if (compact) 8.dp else 24.dp)
                // Side inset so a long name wraps well before the perimeter progress stroke rather
                // than running under it. The phase label is far shorter and never notices.
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The phase word always leads. It is the thing you read at a glance from across the
            // room and it never moves or changes size, so a glance costs nothing — the name goes
            // underneath it as subtext, which is the one place it can be as long as you like
            // without pushing anything else around. Work wears its own section's name; rest wears
            // the one it is handing over to, which is what you need while you catch your breath.
            // Not on the cover screen: its label band is 46dp, so it keeps the plain phase word.
            val named = !compact && ui.label.isNotEmpty()
            Text(
                label,
                color = Color.White,
                fontSize = labelSize,
                fontWeight = glyphWeight(lang),
                textAlign = TextAlign.Center,
            )
            if (named && ui.phase != Phase.PREPARE) {
                Text(
                    if (ui.phase == Phase.REST) "Next · ${ui.label}" else ui.label,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // How far through the workout, down at the bottom: the count, then a pip per repetition.
        // Sits above the hold-to-pause hint, which keeps its own 44dp.
        if (ui.totalRounds > 0) {
            Column(
                modifier =
                    if (compact) Modifier.align(Alignment.BottomStart)
                        .padding(start = COMPACT_EDGE_DP.dp, bottom = COMPACT_EDGE_DP.dp + 14.dp)
                        .width(COMPACT_COUNTER_WIDTH_DP.dp)
                    else Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
                horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                val hasRound = ui.round > 0
                Text(
                    // In the chosen language's own numerals — this printed Western digits in every
                    // language, so a Chinese workout still counted "3 / 16" under 运动.
                    if (hasRound) "${Numbers.count(ui.round, lang)} / ${Numbers.count(ui.totalRounds, lang)}" else " ",
                    color = Color.White.copy(alpha = if (hasRound) 0.80f else 0f),
                    fontSize = counterSize,
                    // Monospace has no CJK/Indic/Arabic glyphs, so glyph scripts take the default face
                    // — the same swap the big clock makes.
                    fontFamily = if (lang.digits == null) FontFamily.Monospace else FontFamily.Default,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                OverallProgress(
                    ui.round, ui.totalRounds, ui.roundsPerPass,
                    live = ui.running && !ui.paused && !ui.done,
                    compact = compact,
                )
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
