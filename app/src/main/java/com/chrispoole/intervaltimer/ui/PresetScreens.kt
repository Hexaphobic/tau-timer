package com.chrispoole.intervaltimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.chrispoole.intervaltimer.PresetStore
import com.chrispoole.intervaltimer.Settings
import com.chrispoole.intervaltimer.model.BUILTIN_PRESETS
import com.chrispoole.intervaltimer.model.Block
import com.chrispoole.intervaltimer.model.backToBackRests
import com.chrispoole.intervaltimer.model.flatten
import com.chrispoole.intervaltimer.model.groupIntervals
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.playbackIntervals
import com.chrispoole.intervaltimer.model.Preset
import com.chrispoole.intervaltimer.model.SeqInterval
import com.chrispoole.intervaltimer.model.secLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun clock(totalSec: Int): String = "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"

private fun Preset.summary(): String {
    val seq = playbackIntervals()
    return "${seq.size} intervals · ${clock(seq.sumOf { it.durationSec })}"
}

@Composable
fun PresetsScreen(
    onBack: () -> Unit,
    onStart: (Preset) -> Unit,
    onNew: () -> Unit,
    onEdit: (savedIndex: Int) -> Unit,
    onEditBuiltin: (Preset) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        // The pill floats over the scroll rather than sitting above it, so the list passes underneath
        // instead of being cut off at a hard edge. Declared after the scroll so it is hit-tested first.
        // No safeDrawingPadding on the container: the camera cutout's reserved band walled off the
        // top of the screen for a hole it only overlaps in the middle. The list uses the whole panel;
        // only the pill keeps an inset, so the one control up there never lands under the camera.
        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                // Clear of the pill at rest; scrolled, the list simply travels under it.
                .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 60.dp),
        ) {

            // Anything in the list can be deleted. Built-ins are compiled in rather than stored, so
            // they're hidden by name instead of removed; saved ones are deleted outright.
            BUILTIN_PRESETS.filterNot { it.name in Settings.hiddenBuiltins }.forEach { p ->
                // Editable like anything else — saving writes the copy and hides the original, so
                // "pre-made" is a starting point, not a locked case.
                PresetRow(p, onStart = { onStart(p) }, onEdit = { onEditBuiltin(p) }, onDelete = { Settings.hideBuiltin(p.name); PresetStore.pushToWatch() })
            }
            PresetStore.saved.forEachIndexed { index, p ->
                PresetRow(p, onStart = { onStart(p) }, onEdit = { onEdit(index) }, onDelete = { PresetStore.deleteAt(index) })
            }

            Spacer(Modifier.height(16.dp))
            GlassPill("+  New sequence", onNew, Modifier.fillMaxWidth())
        }
        BackPill(
            onBack,
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(start = 12.dp, top = 8.dp),
        )
        }
    }
}

@Composable
private fun PresetRow(preset: Preset, onStart: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    // Keyed on the preset, not the slot: these rows live in a plain Column, so remember state is
    // positional. Deleting one used to shift the row below into its slot and hand it the armed
    // "Delete?" state, where one more tap deleted the wrong preset.
    var expanded by remember(preset) { mutableStateOf(false) }
    // Delete takes two taps. Collapsing the row disarms it, so it can never sit armed unseen.
    var armed by remember(preset) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(GlassFill, RoundedCornerShape(16.dp))
            .animateContentSize()
            .noRippleClickable { expanded = !expanded; armed = false }
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(preset.summary(), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
            }
            Text(if (expanded) "▾" else "▸", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            // One tinted band per interval, full width — same treatment as the editor, so a preset
            // looks like the thing you'd edit. The old version indented rest and left work short,
            // which made the two read as different kinds of row rather than the same row in two
            // colours.
            //
            // The sequence as written, not as expanded — the ×N line below says how often it runs.
            preset.intervals.forEach { iv ->
                val isWork = iv.phase == Phase.WORK
                val c = if (isWork) WorkColor else RestColor
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(c.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (isWork) "Work" else "Rest",
                        color = c,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(secLabel(iv.durationSec), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                }
            }
            if (preset.repeatAll > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "All of it × ${preset.repeatAll}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEdit != null) {
                        TextButton(onClick = onEdit) { Text("Edit", color = Color.White.copy(alpha = 0.8f)) }
                    }
                    // Tap the bin to arm, tap the red "Delete?" pill to commit. Both are kept narrow:
                    // a wide confirm label squeezed the Start pill into wrapping onto a second line.
                    if (armed) {
                        val pill = RoundedCornerShape(50)
                        Box(
                            Modifier
                                .clip(pill)
                                .background(DangerRed.copy(alpha = 0.22f))
                                .border(1.dp, DangerRed.copy(alpha = 0.55f), pill)
                                // Disarm as it commits. `remember(preset)` keys on the preset's
                                // value, so two structurally equal saved presets share this state:
                                // deleting the first slid the second into its slot still armed,
                                // one tap from deleting it too.
                                .clickable { armed = false; onDelete() }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text("Delete?", color = DangerRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            Modifier.clip(CircleShape).clickable { armed = true }.padding(8.dp),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete preset", tint = DangerRed)
                        }
                    }
                }
                GlassPill("Start  ▶", onStart)
            }
        }
    }
}

// ---- Editor ----

/** Identity for a card that outlives its position, so reordering animates instead of redrawing. */
private class UiBlock(val id: Long, val block: Block)

private class Ids {
    private var n = 0L
    fun next(): Long = n++
}

/** The header is a single lazy item, so the cards start at index 1. */
private const val FIRST_CARD = 1

@Composable
fun EditorScreen(
    initial: Preset?,
    onStart: (Preset) -> Unit,
    onSave: (Preset) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val ids = remember { Ids() }
    val blocks = remember {
        mutableStateListOf<UiBlock>().apply {
            addAll(
                groupIntervals(initial?.intervals ?: listOf(SeqInterval(Phase.WORK, 30), SeqInterval(Phase.REST, 15)))
                    .map { UiBlock(ids.next(), it) },
            )
        }
    }
    var repeatAll by remember { mutableIntStateOf(initial?.repeatAll ?: 1) }

    // Why an edit was refused. Sequenced so re-flashing the same message restarts its timer.
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeSeq by remember { mutableIntStateOf(0) }
    fun flash(message: String) {
        // A refused drag asks again on every frame it's held there. Re-arming the timer each time
        // would pin the pill open and recompose the whole editor at 60fps, so a message already on
        // screen is left to run out its own clock.
        if (notice == message) return
        notice = message
        noticeSeq++
    }
    LaunchedEffect(noticeSeq) {
        if (notice != null) {
            delay(2600)
            notice = null
        }
    }

    fun current(): List<Block> = blocks.map { it.block }

    /**
     * The no-back-to-back-rests rule, asked of the sequence as it will actually play.
     *
     * Compared against what's already there rather than against zero: a sequence that already has
     * a double rest (built with the rule off, or turned on afterwards) must still be editable, so
     * only an edit that *adds* one is refused.
     */
    fun violates(candidate: List<Block>, repeat: Int): Boolean =
        Settings.noDoubleRest &&
            backToBackRests(candidate, repeat) > backToBackRests(current(), repeatAll)

    fun allow(candidate: List<Block>, repeat: Int = repeatAll): Boolean {
        if (!violates(candidate, repeat)) return true
        flash("Two rests would land in a row — allow it in Settings")
        return false
    }

    fun replace(i: Int, b: Block): List<Block> = current().mapIndexed { k, x -> if (k == i) b else x }

    /**
     * Every rule-checked edit to a group goes through here; deletes deliberately don't. Returns
     * whether it landed, so a refused drag knows to put the row back where it came from.
     */
    fun change(i: Int, next: Block): Boolean {
        // Card callbacks capture their index; a second tap landing before recomposition indexes a
        // list that already shrank. Same guard moveGroup carries, for the same reason.
        if (i !in blocks.indices) return false
        if (!allow(replace(i, next))) return false
        blocks[i] = UiBlock(blocks[i].id, next)
        return true
    }

    fun addInterval(i: Int) {
        if (i !in blocks.indices) return
        val b = blocks[i].block
        val rest = b.copy(items = b.items + SeqInterval(Phase.REST, 15))
        // Alternate by default; where a rest would double up, work is the sane fallback.
        val next = if (b.items.lastOrNull()?.phase == Phase.WORK && !violates(replace(i, rest), repeatAll)) {
            rest
        } else {
            b.copy(items = b.items + SeqInterval(Phase.WORK, 30))
        }
        change(i, next)
    }

    fun removeInterval(i: Int, j: Int) {
        if (i !in blocks.indices) return
        val b = blocks[i].block
        if (j !in b.items.indices) return
        if (b.items.size == 1) blocks.removeAt(i)
        else blocks[i] = UiBlock(blocks[i].id, b.copy(items = b.items.toMutableList().also { it.removeAt(j) }))
    }

    fun addGroup() {
        val pair = Block(listOf(SeqInterval(Phase.WORK, 30), SeqInterval(Phase.REST, 15)), 1)
        val next = if (violates(current() + pair, repeatAll)) Block(listOf(SeqInterval(Phase.WORK, 30)), 1) else pair
        if (allow(current() + next)) blocks.add(UiBlock(ids.next(), next))
    }

    fun moveGroup(from: Int, to: Int): Boolean {
        // The drag tracks list indices of its own; a group deleted by a second finger mid-drag would
        // otherwise take them out of range.
        if (from !in blocks.indices || to !in blocks.indices) return false
        val candidate = current().toMutableList().apply { add(to, removeAt(from)) }
        if (!allow(candidate)) return false
        blocks.add(to, blocks.removeAt(from))
        return true
    }

    fun build() = Preset(name.ifBlank { "Sequence" }, flatten(current()), repeatAll)

    val listState = rememberLazyListState()
    val dragDrop = rememberDragDropState(
        listState = listState,
        draggable = FIRST_CARD until FIRST_CARD + blocks.size,
        onMove = { from, to -> moveGroup(from - FIRST_CARD, to - FIRST_CARD) },
    )

    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Outside the list, like the Back row on the other screens: a long sequence used to push
        // Cancel, Start and Save off the top, and they are the only ways out of this screen. The
        // header item below stays — FIRST_CARD still counts it.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPill("Start", { onStart(build()) }, enabled = blocks.isNotEmpty())
                Spacer(Modifier.width(10.dp))
                GlassPill("Save", { onSave(build()) }, enabled = blocks.isNotEmpty())
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            item(key = "header") {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    PhaseLegend()
                    if (blocks.size > 1 || blocks.any { it.block.items.size > 1 }) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GripDots(alpha = 0.45f, height = 13.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Drag to reorder — groups by the header, intervals by their own grip",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            itemsIndexed(blocks, key = { _, b -> b.id }) { i, ub ->
                val floating = dragDrop.isFloating(ub.id)
                val lifted = dragDrop.isLifted(ub.id)
                val scale by animateFloatAsState(if (lifted) 1.03f else 1f, label = "lift")
                BlockEditorCard(
                    block = ub.block,
                    index = i,
                    groupCount = blocks.size,
                    lifted = lifted,
                    // Switching a work interval to rest is the only edit the rule can refuse, so
                    // it's the only one the card needs to grey out.
                    canRest = { j ->
                        val b = ub.block
                        !violates(replace(i, b.copy(items = b.items.toMutableList().also { it[j] = it[j].copy(phase = Phase.REST) })), repeatAll)
                    },
                    handle = {
                        DragHandle(
                            key = ub.id,
                            label = "group ${i + 1}",
                            state = dragDrop,
                            onMoveUp = if (i > 0) ({ moveGroup(i, i - 1); Unit }) else null,
                            onMoveDown = if (i < blocks.lastIndex) ({ moveGroup(i, i + 1); Unit }) else null,
                        )
                    },
                    onChange = { change(i, it) },
                    onAddItem = { addInterval(i) },
                    onRemoveItem = { j -> removeInterval(i, j) },
                    onDeleteGroup = { if (i in blocks.indices) blocks.removeAt(i) },
                    modifier = Modifier
                        .zIndex(if (floating) 1f else 0f)
                        .graphicsLayer {
                            translationY = dragDrop.offsetFor(ub.id)
                            scaleX = scale
                            scaleY = scale
                        }
                        // The card under the finger is placed by hand; everything else lets the
                        // lazy list animate it aside.
                        .then(if (floating) Modifier else Modifier.animateItem()),
                )
            }

            item(key = "footer") {
                Column(Modifier.animateItem()) {
                    Spacer(Modifier.height(12.dp))
                    GlassPill("+  Add group", { addGroup() }, Modifier.fillMaxWidth())
                    if (blocks.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        RepeatAllCard(
                            repeatAll = repeatAll,
                            onChange = { next -> if (allow(current(), next)) repeatAll = next },
                        )
                        Spacer(Modifier.height(12.dp))
                        TotalsLine(current(), repeatAll)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        }

        notice?.let {
            NoticePill(
                it,
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 28.dp, start = 20.dp, end = 20.dp),
            )
        }
    }
}

/** Colour key. The rows carry no text, so this is what tells you which colour means what. */
@Composable
private fun PhaseLegend() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        listOf(WorkColor to "Work", RestColor to "Rest").forEach { (c, label) ->
            Box(Modifier.size(10.dp).background(c, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.width(16.dp))
        }
    }
}

/** What the whole thing adds up to, so the repeat counts have a number attached to them. */
@Composable
private fun TotalsLine(blocks: List<Block>, repeatAll: Int) {
    val once = flatten(blocks)
    val groups = if (blocks.size == 1) "1 group" else "${blocks.size} groups"
    // Counted the way it will be played, without building the expanded list: the whole thing × N,
    // less the trailing rest the timer drops.
    val count = once.size * repeatAll
    val trailingRest = if (count > 1 && once.lastOrNull()?.phase == Phase.REST) once.last().durationSec else 0
    val played = if (trailingRest > 0) count - 1 else count
    val seconds = once.sumOf { it.durationSec } * repeatAll - trailingRest
    Column {
        Text(
            "$groups · $played intervals · ${clock(seconds)}",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
        // Only reachable with the rule switched off, or on a sequence built before it was — worth
        // saying out loud rather than letting it play as one long silent gap.
        if (backToBackRests(blocks, repeatAll) > 0) {
            Spacer(Modifier.height(4.dp))
            Text("Two rests play back to back", color = RestColor.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

/**
 * The outer ×N: the whole sequence, top to bottom, that many times.
 *
 * Shared with the home screen rather than copied, so building a sequence there and building one in
 * the editor put the same control in the same words in front of you.
 */
@Composable
fun RepeatAllCard(repeatAll: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(GlassFill, shape)
            .border(1.dp, glassBorder(), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 40/44 rather than 44/52: on a 360dp-wide phone the wider stepper left the label too little
        // to sit on one line, and "Repeat everything" folded in half above a two-line subtitle.
        Column(Modifier.weight(1f)) {
            Text("Repeat everything", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                // Short enough to stay on one line beside the stepper at 360dp.
                if (repeatAll == 1) "Plays through once" else "$repeatAll times through",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
        GlassCircle("−", { onChange((repeatAll - 1).coerceAtLeast(1)) }, size = 40.dp)
        Text(
            "× $repeatAll",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        GlassCircle("+", { onChange(repeatAll + 1) }, size = 40.dp)
    }
}

/**
 * One repeat-group. The intervals sit inside a bracket so they visibly belong together, and the
 * repeat count is stated in words under it. The grip and delete live in the header, far from the
 * ×N stepper — sitting side by side, a reorder control reads as if it drove the repeat count.
 */
@Composable
private fun BlockEditorCard(
    block: Block,
    index: Int,
    groupCount: Int,
    lifted: Boolean,
    canRest: (Int) -> Boolean,
    handle: @Composable () -> Unit,
    /** Returns whether the edit was accepted — a reorder that isn't has to spring back. */
    onChange: (Block) -> Boolean,
    onAddItem: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onDeleteGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    // Lifting brightens the glass and casts a shadow: on a dark background that reads as height far
    // better than a drop shadow alone, which all but disappears.
    val fill by animateColorAsState(if (lifted) Color.White.copy(alpha = 0.20f) else GlassFill, label = "fill")
    val edge by animateFloatAsState(if (lifted) 0.5f else 0f, label = "edge")
    val elevation by animateDpAsState(if (lifted) 16.dp else 0.dp, label = "elevation")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .shadow(elevation, shape, clip = false)
            .background(fill, shape)
            .border(1.dp, Color.White.copy(alpha = edge), shape)
            .padding(start = 4.dp, top = 8.dp, end = 14.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                handle()
                Text(
                    if (groupCount > 1) "Group ${index + 1}" else "Group",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            TextButton(onClick = onDeleteGroup) { Text("Delete", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) }
        }

        // The bracket: a rail down the left edge tying every interval in the group together.
        Row(Modifier.fillMaxWidth().padding(start = 10.dp).height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                IntervalRows(
                    items = block.items,
                    canRest = canRest,
                    onSet = { j, v -> onChange(block.copy(items = block.items.toMutableList().also { it[j] = v })) },
                    onRemove = onRemoveItem,
                    onMove = { from, to ->
                        onChange(block.copy(items = block.items.toMutableList().also { it.add(to, it.removeAt(from)) }))
                    },
                )
                TextButton(onClick = onAddItem) {
                    Text("+ interval", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }

        // Stated in words so there's no guessing what the number applies to.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Repeat this group", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            GlassCircle("−", { m -> onChange(block.copy(repeat = (block.repeat - m).coerceAtLeast(1))) }, size = 44.dp)
            Text(
                "× ${block.repeat}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            GlassCircle("+", { m -> onChange(block.copy(repeat = block.repeat + m)) }, size = 44.dp)
        }
    }
}

/**
 * The intervals of one group, reorderable among themselves.
 *
 * Rows are a fixed height here, so — unlike the cards — nothing needs measuring against a layout:
 * the row under the finger is translated, the rows it has passed shift one slot to make the gap,
 * and the list itself is only rewritten once, on the drop. Nothing moves twice.
 */
@Composable
private fun IntervalRows(
    items: List<SeqInterval>,
    canRest: (Int) -> Boolean,
    onSet: (Int, SeqInterval) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableIntStateOf(-1) }
    var dragged by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    // The row easing home after the drop, tracked apart from the one under the finger so a second
    // grab during the spring is a clean new drag rather than a fight over the same offset.
    var settling by remember { mutableIntStateOf(-1) }
    var settleOffset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // The gesture detector is set up once and must outlive every recomposition — re-keying it would
    // tear down a drag in progress, since dragging recomposes these rows on every frame. So the
    // things it reaches for are held in updated state rather than captured: the closure it was built
    // with would otherwise still be writing back the group as it stood when the finger went down,
    // quietly undoing any edit made since.
    val liveItems by rememberUpdatedState(items)
    val liveMove by rememberUpdatedState(onMove)

    fun target(): Int =
        if (from < 0 || pitch <= 0f) -1
        else (from + (dragged / pitch).roundToInt()).coerceIn(0, liveItems.lastIndex)

    fun grab(j: Int) {
        settleJob?.cancel()
        settling = -1
        settleOffset = 0f
        from = j
        dragged = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun commit() {
        val start = from
        if (start < 0) return
        val end = target()
        // The move lands now, on release — never from indices captured before an animation, which by
        // the time it finished could point at a row that had since been deleted. What's left to
        // animate is the handful of pixels between the finger and the slot it dropped into, and a
        // move the rule turns down simply rides back to where it came from. The bounds check covers
        // the one index that can still go stale: a second finger deleting a row mid-drag.
        val moved = end >= 0 && end != start && start in liveItems.indices && liveMove(start, end)
        val landedAt = if (moved) end else start
        settleOffset = dragged - (landedAt - start) * pitch
        settling = landedAt
        from = -1
        dragged = 0f
        settleJob = scope.launch {
            Animatable(settleOffset).animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
            ) { settleOffset = value }
            settling = -1
            settleOffset = 0f
        }
    }

    val to = target()
    LaunchedEffect(to) {
        if (from >= 0 && to >= 0 && to != from) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { j, iv ->
            val dragging = j == from
            val slide = when {
                from < 0 || to < 0 || dragging -> 0f
                from < to && j > from && j <= to -> -pitch
                to < from && j >= to && j < from -> pitch
                else -> 0f
            }
            val animatedSlide by animateFloatAsState(slide, spring(stiffness = Spring.StiffnessMediumLow), label = "slide")
            val isWork = iv.phase == Phase.WORK
            val tint = (if (isWork) WorkColor else RestColor).copy(alpha = if (dragging) 0.34f else 0.20f)
            Row(
                Modifier
                    .zIndex(if (dragging || j == settling) 1f else 0f)
                    .onSizeChanged { if (it.height > 0) pitch = it.height.toFloat() }
                    .graphicsLayer {
                        // The rows that stood aside are back in their real slots the moment the drop
                        // lands, so their offset is dropped in that same frame rather than animated
                        // away on top of a layout that has already moved them.
                        translationY = when {
                            dragging -> dragged
                            j == settling -> settleOffset
                            from >= 0 -> animatedSlide
                            else -> 0f
                        }
                    }
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(tint, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (items.size > 1) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { grab(j) },
                                    onDragEnd = { commit() },
                                    onDragCancel = { commit() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragged += amount.y
                                    },
                                )
                            }
                            .semantics {
                                contentDescription = "Reorder interval ${j + 1}"
                                customActions = listOfNotNull(
                                    if (j > 0) CustomAccessibilityAction("Move up") { onMove(j, j - 1); true } else null,
                                    if (j < items.lastIndex) CustomAccessibilityAction("Move down") { onMove(j, j + 1); true } else null,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        GripDots(alpha = 0.5f, height = 16.dp)
                    }
                } else {
                    Spacer(Modifier.width(30.dp))
                }

                // Its own control with its own hit box. The row used to swap phase on any tap,
                // which meant a near-miss on a stepper flipped work to rest instead of nudging
                // the clock.
                PhaseChip(
                    phase = iv.phase,
                    // Greyed, but still tappable: the tap is what surfaces the reason.
                    dimmed = isWork && !canRest(j),
                    onClick = { onSet(j, iv.copy(phase = if (isWork) Phase.REST else Phase.WORK)) },
                )
                Spacer(Modifier.width(4.dp))
                GlassCircle(
                    "−",
                    { m -> onSet(j, iv.copy(durationSec = (iv.durationSec - 5 * m).coerceAtLeast(5))) },
                    size = 44.dp,
                )
                Text(
                    secLabel(iv.durationSec),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    // Weighted rather than fixed: still a constant width whatever the label says,
                    // but it gives way first when the row is squeezed on a narrow screen.
                    modifier = Modifier.weight(1f),
                )
                GlassCircle("+", { m -> onSet(j, iv.copy(durationSec = iv.durationSec + 5 * m)) }, size = 44.dp)
                CloseX { onRemove(j) }
            }
        }
    }
}

/** Work / rest switch for one interval. [dimmed] means the rule won't allow a rest here. */
@Composable
private fun PhaseChip(phase: Phase, dimmed: Boolean, onClick: () -> Unit) {
    val work = phase == Phase.WORK
    val accent = if (work) WorkColor else RestColor
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(48.dp)
            .clip(shape)
            .background(accent.copy(alpha = if (dimmed) 0.14f else 0.30f))
            .border(1.dp, accent.copy(alpha = if (dimmed) 0.22f else 0.55f), shape)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (work) "WORK" else "REST",
            color = Color.White.copy(alpha = if (dimmed) 0.45f else 0.95f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}
