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
import com.chrispoole.intervaltimer.model.expanded
import com.chrispoole.intervaltimer.model.flatten
import com.chrispoole.intervaltimer.model.groupIntervals
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.Preset
import com.chrispoole.intervaltimer.model.SeqInterval
import com.chrispoole.intervaltimer.model.secLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun clock(totalSec: Int): String = "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"

private fun Preset.summary(): String {
    val seq = expanded()
    return "${seq.size} intervals · ${clock(seq.sumOf { it.durationSec })}"
}

@Composable
fun PresetsScreen(
    onBack: () -> Unit,
    onStart: (Preset) -> Unit,
    onNew: () -> Unit,
    onEdit: (savedIndex: Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontSize = 18.sp) }
                Spacer(Modifier.width(8.dp))
                Text("Presets", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))

            // Anything in the list can be deleted. Built-ins are compiled in rather than stored, so
            // they're hidden by name instead of removed; saved ones are deleted outright.
            BUILTIN_PRESETS.filterNot { it.name in Settings.hiddenBuiltins }.forEach { p ->
                PresetRow(p, onStart = { onStart(p) }, onEdit = null, onDelete = { Settings.hideBuiltin(p.name); PresetStore.pushToWatch() })
            }
            PresetStore.saved.forEachIndexed { index, p ->
                PresetRow(p, onStart = { onStart(p) }, onEdit = { onEdit(index) }, onDelete = { PresetStore.deleteAt(index) })
            }

            Spacer(Modifier.height(16.dp))
            GlassPill("+  New sequence", onNew, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PresetRow(preset: Preset, onStart: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Delete takes two taps. Collapsing the row disarms it, so it can never sit armed unseen.
    var armed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(GlassFill, RoundedCornerShape(16.dp))
            .animateContentSize()
            .clickable { expanded = !expanded; armed = false }
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
            // The sequence as written, not as expanded — the ×N line below says how often it runs.
            preset.intervals.forEach { iv ->
                val isWork = iv.phase == Phase.WORK
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Rest rows are indented so work sits left, rest right — a quick visual rhythm.
                    if (!isWork) Spacer(Modifier.width(56.dp))
                    Text(
                        if (isWork) "Work" else "Rest",
                        color = (if (isWork) WorkGreen else RestBlue).copy(alpha = 0.95f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("  ${secLabel(iv.durationSec)}", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
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
                                .clickable { onDelete() }
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
    saveLabel: String = "Save",
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

    /** Every rule-checked edit to a group goes through here. Deletes deliberately don't. */
    fun change(i: Int, next: Block) {
        if (allow(replace(i, next))) blocks[i] = UiBlock(blocks[i].id, next)
    }

    fun addInterval(i: Int) {
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
        val b = blocks[i].block
        if (b.items.size == 1) blocks.removeAt(i)
        else blocks[i] = UiBlock(blocks[i].id, b.copy(items = b.items.toMutableList().also { it.removeAt(j) }))
    }

    fun addGroup() {
        val pair = Block(listOf(SeqInterval(Phase.WORK, 30), SeqInterval(Phase.REST, 15)), 1)
        val next = if (violates(current() + pair, repeatAll)) Block(listOf(SeqInterval(Phase.WORK, 30)), 1) else pair
        if (allow(current() + next)) blocks.add(UiBlock(ids.next(), next))
    }

    fun moveGroup(from: Int, to: Int): Boolean {
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(20.dp),
        ) {
            item(key = "header") {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onCancel) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassPill("Start", { onStart(build()) }, enabled = blocks.isNotEmpty())
                            Spacer(Modifier.width(10.dp))
                            GlassPill(saveLabel, { onSave(build()) }, enabled = blocks.isNotEmpty())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
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
                    onDeleteGroup = { blocks.removeAt(i) },
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
        listOf(WorkGreen to "Work", RestBlue to "Rest").forEach { (c, label) ->
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
    Column {
        Text(
            "$groups · ${once.size * repeatAll} intervals · ${clock(once.sumOf { it.durationSec } * repeatAll)}",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
        // Only reachable with the rule switched off, or on a sequence built before it was — worth
        // saying out loud rather than letting it play as one long silent gap.
        if (backToBackRests(blocks, repeatAll) > 0) {
            Spacer(Modifier.height(4.dp))
            Text("Two rests play back to back", color = RestBlue.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

/** The outer ×N: the whole sequence, top to bottom, that many times. */
@Composable
private fun RepeatAllCard(repeatAll: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(GlassFill, shape)
            .border(1.dp, glassBorder(), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Repeat everything", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                if (repeatAll == 1) "Plays once, top to bottom" else "Every group, $repeatAll times over",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
        GlassCircle("−", { onChange((repeatAll - 1).coerceAtLeast(1)) }, size = 44.dp)
        Text(
            "× $repeatAll",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp),
        )
        GlassCircle("+", { onChange(repeatAll + 1) }, size = 44.dp)
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
    onChange: (Block) -> Unit,
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
            GlassCircle("−", { onChange(block.copy(repeat = (block.repeat - 1).coerceAtLeast(1))) }, size = 44.dp)
            Text(
                "× ${block.repeat}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            GlassCircle("+", { onChange(block.copy(repeat = block.repeat + 1)) }, size = 44.dp)
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
    onMove: (Int, Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableIntStateOf(-1) }
    var dragged by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }

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

    fun commit() {
        val start = from
        val end = target()
        if (start < 0) return
        if (end < 0) {
            from = -1
            dragged = 0f
            return
        }
        scope.launch {
            // Ride the last few pixels into the slot instead of snapping, so the drop lands.
            Animatable(dragged).animateTo(
                (end - start) * pitch,
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
            ) { dragged = value }
            if (end != start) liveMove(start, end)
            from = -1
            dragged = 0f
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
            val accel = rememberStepAccel(5)
            val isWork = iv.phase == Phase.WORK
            val tint = (if (isWork) WorkGreen else RestBlue).copy(alpha = if (dragging) 0.34f else 0.20f)
            Row(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .onSizeChanged { if (it.height > 0) pitch = it.height.toFloat() }
                    .graphicsLayer {
                        // Once the drop has been committed the slide is done by the layout itself,
                        // so the offset has to be dropped in the same frame, not animated away.
                        translationY = if (dragging) dragged else if (from >= 0) animatedSlide else 0f
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
                                    onDragStart = {
                                        from = j
                                        dragged = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
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
                    { onSet(j, iv.copy(durationSec = (iv.durationSec - accel.step(-1)).coerceAtLeast(5))) },
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
                GlassCircle("+", { onSet(j, iv.copy(durationSec = iv.durationSec + accel.step(1))) }, size = 44.dp)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { onRemove(j) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
                }
            }
        }
    }
}

/** Work / rest switch for one interval. [dimmed] means the rule won't allow a rest here. */
@Composable
private fun PhaseChip(phase: Phase, dimmed: Boolean, onClick: () -> Unit) {
    val work = phase == Phase.WORK
    val accent = if (work) WorkGreen else RestBlue
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
