package com.chrispoole.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.chrispoole.intervaltimer.model.BUILTIN_PRESETS
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.Preset
import com.chrispoole.intervaltimer.model.SeqInterval

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

            BUILTIN_PRESETS.forEach { p -> PresetRow(p, onStart = { onStart(p) }, onEdit = null) }
            PresetStore.saved.forEachIndexed { index, p ->
                PresetRow(p, onStart = { onStart(p) }, onEdit = { onEdit(index) })
            }

            Spacer(Modifier.height(16.dp))
            GlassPill("+  New sequence", onNew, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PresetRow(preset: Preset, onStart: () -> Unit, onEdit: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(GlassFill, RoundedCornerShape(16.dp))
            .clickable { onStart() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.width(220.dp)) {
            Text(preset.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(preset.summary(), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        }
        if (onEdit != null) {
            TextButton(onClick = onEdit) { Text("Edit", color = Color.White.copy(alpha = 0.8f)) }
        } else {
            Text("▶", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
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
    val items = remember {
        mutableStateListOf<SeqInterval>().apply {
            addAll(initial?.intervals ?: listOf(SeqInterval(Phase.WORK, 30), SeqInterval(Phase.REST, 15)))
        }
    }
    fun build() = Preset(name.ifBlank { "Sequence" }, items.toList())

    Box(Modifier.fillMaxSize()) {
        HomeBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassPill("Start", { onStart(build()) }, enabled = items.isNotEmpty())
                    Spacer(Modifier.width(10.dp))
                    GlassPill("Save", { onSave(build()) }, enabled = items.isNotEmpty())
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
            Spacer(Modifier.height(16.dp))

            items.forEachIndexed { i, iv ->
                IntervalEditorRow(
                    interval = iv,
                    onTogglePhase = { items[i] = iv.copy(phase = if (iv.phase == Phase.WORK) Phase.REST else Phase.WORK) },
                    onMinus = { items[i] = iv.copy(durationSec = (iv.durationSec - 5).coerceAtLeast(5)) },
                    onPlus = { items[i] = iv.copy(durationSec = iv.durationSec + 5) },
                    onUp = { if (i > 0) { val t = items[i - 1]; items[i - 1] = items[i]; items[i] = t } },
                    onDown = { if (i < items.lastIndex) { val t = items[i + 1]; items[i + 1] = items[i]; items[i] = t } },
                    onDuplicate = { items.add(i + 1, iv) },
                    onDelete = { items.removeAt(i) },
                )
            }

            Spacer(Modifier.height(12.dp))
            GlassPill("+  Add interval", { items.add(SeqInterval(Phase.WORK, 30)) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun IntervalEditorRow(
    interval: SeqInterval,
    onTogglePhase: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val isWork = interval.phase == Phase.WORK
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(GlassFill, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier
                    .background(
                        if (isWork) Color(0xFF22E06A).copy(alpha = 0.22f) else Color(0xFF3B82F6).copy(alpha = 0.22f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onTogglePhase() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(if (isWork) "Work" else "Rest", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassCircle("−", onMinus, Modifier)
                Text("${interval.durationSec}s", color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 10.dp))
                GlassCircle("+", onPlus, Modifier)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onUp) { Text("↑", color = Color.White.copy(alpha = 0.7f)) }
            TextButton(onClick = onDown) { Text("↓", color = Color.White.copy(alpha = 0.7f)) }
            TextButton(onClick = onDuplicate) { Text("Copy", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
            TextButton(onClick = onDelete) { Text("Delete", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
        }
    }
}
