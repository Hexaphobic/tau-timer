package com.chrispoole.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val GlassFill = Color.White.copy(alpha = 0.10f)

// Phase colours — one source for the timer glow, the editor, and the legend, so they can't drift.
val WorkGreen = Color(0xFF22E06A)
val RestBlue = Color(0xFF38BDF8)
val PrepPurple = Color(0xFF8B5CF6)
val DoneGray = Color(0xFF9CA3AF)

/** Destructive actions: ending a workout, deleting a preset. */
val DangerRed = Color(0xFFFF4D4D)

fun glassBorder(): Brush =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f)))

@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 24.dp, vertical = if (big) 18.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = if (big) 22.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            // A pill's label is always one line — a crowded row must not fold "Start ▶" in half.
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * [size] shrinks the whole control, glyph included — the editor's interval rows carry a stepper,
 * a phase switch and a delete on one line, which the full-size circle can't fit on a narrow phone.
 */
@Composable
fun GlassCircle(glyph: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 54.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = Color.White,
            fontSize = (size.value * 0.44f).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A short, self-dismissing explanation. The editor refuses a few edits (a rest that would land on
 * another rest); refusing silently would just read as a broken button, so it says why.
 */
@Composable
fun NoticePill(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, glassBorder(), shape)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
