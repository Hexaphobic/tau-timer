package com.chrispoole.intervaltimer.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrispoole.intervaltimer.PresetStore
import com.chrispoole.intervaltimer.Settings
import com.chrispoole.intervaltimer.model.BUILTIN_PRESETS
import com.chrispoole.intervaltimer.model.Block
import com.chrispoole.intervaltimer.model.flatten
import com.chrispoole.intervaltimer.model.groupIntervals
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.Preset
import com.chrispoole.intervaltimer.model.SeqInterval
import com.chrispoole.intervaltimer.model.secLabel

private fun Preset.summary(): String {
    val total = intervals.sumOf { it.durationSec }
    return "${intervals.size} intervals · ${total / 60}:${(total % 60).toString().padStart(2, '0')}"
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
                PresetRow(p, onStart = { onStart(p) }, onEdit = null, onDelete = { Settings.hideBuiltin(p.name) })
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

@Composable
fun EditorScreen(
    initial: Preset?,
    onStart: (Preset) -> Unit,
    onSave: (Preset) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val blocks = remember {
        mutableStateListOf<Block>().apply {
            addAll(groupIntervals(initial?.intervals ?: listOf(SeqInterval(Phase.WORK, 30), SeqInterval(Phase.REST, 15))))
        }
    }
    fun build() = Preset(name.ifBlank { "Sequence" }, flatten(blocks))

    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassPill("Start", { onStart(build()) }, enabled = blocks.isNotEmpty())
                    Spacer(Modifier.width(10.dp))
                    GlassPill("Save", { onSave(build()) }, enabled = blocks.isNotEmpty())
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
            Spacer(Modifier.height(6.dp))

            blocks.forEachIndexed { i, block ->
                BlockEditorCard(
                    block = block,
                    index = i,
                    groupCount = blocks.size,
                    onChange = { blocks[i] = it },
                    onUp = { if (i > 0) { val t = blocks[i - 1]; blocks[i - 1] = blocks[i]; blocks[i] = t } },
                    onDown = { if (i < blocks.lastIndex) { val t = blocks[i + 1]; blocks[i + 1] = blocks[i]; blocks[i] = t } },
                    onDelete = { blocks.removeAt(i) },
                )
            }

            Spacer(Modifier.height(12.dp))
            GlassPill(
                "+  Add group",
                { blocks.add(Block(listOf(SeqInterval(Phase.WORK, 30), SeqInterval(Phase.REST, 15)), 1)) },
                Modifier.fillMaxWidth(),
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

/**
 * One repeat-group. The intervals sit inside a bracket so they visibly belong together, and the
 * repeat count is stated in words under it. Reorder/delete live in the header, far from the ×N
 * stepper — sitting side by side, the arrows read as if they controlled the repeat count.
 */
@Composable
private fun BlockEditorCard(
    block: Block,
    index: Int,
    groupCount: Int,
    onChange: (Block) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(GlassFill, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (groupCount > 1) "Group ${index + 1}" else "Group",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (groupCount > 1) {
                    TextButton(onClick = onUp) { Text("Move ↑", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) }
                    TextButton(onClick = onDown) { Text("Move ↓", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) }
                }
                TextButton(onClick = onDelete) { Text("Delete", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) }
            }
        }

        // The bracket: a rail down the left edge tying every interval in the group together.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                block.items.forEachIndexed { j, iv ->
                    val isWork = iv.phase == Phase.WORK
                    val accel = rememberStepAccel(5)
                    fun setItem(v: SeqInterval) = onChange(block.copy(items = block.items.toMutableList().also { it[j] = v }))
                    // The row's own colour says work or rest — no label to make the widths uneven.
                    // Tapping the band swaps the phase.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                (if (isWork) WorkGreen else RestBlue).copy(alpha = 0.20f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { setItem(iv.copy(phase = if (isWork) Phase.REST else Phase.WORK)) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassCircle("−", { setItem(iv.copy(durationSec = (iv.durationSec - accel.step(-1)).coerceAtLeast(5))) })
                            // Fixed width so "5s" and "1:30" don't shove the +/- circles around.
                            Text(
                                secLabel(iv.durationSec),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(72.dp),
                            )
                            GlassCircle("+", { setItem(iv.copy(durationSec = iv.durationSec + accel.step(1))) })
                        }
                        TextButton(onClick = {
                            if (block.items.size == 1) onDelete()
                            else onChange(block.copy(items = block.items.toMutableList().also { it.removeAt(j) }))
                        }) { Text("✕", color = Color.White.copy(alpha = 0.5f)) }
                    }
                }
                TextButton(onClick = {
                    onChange(block.copy(items = block.items + SeqInterval(if (block.items.lastOrNull()?.phase == Phase.WORK) Phase.REST else Phase.WORK, 15)))
                }) { Text("+ interval", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
            }
        }

        // Stated in words so there's no guessing what the number applies to.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Repeat all of this", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            GlassCircle("−", { onChange(block.copy(repeat = (block.repeat - 1).coerceAtLeast(1))) })
            Text(
                "× ${block.repeat}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            GlassCircle("+", { onChange(block.copy(repeat = block.repeat + 1)) })
        }
    }
}
