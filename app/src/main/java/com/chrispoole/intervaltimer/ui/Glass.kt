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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val GlassFill = Color.White.copy(alpha = 0.10f)

// Phase colours — one source for the timer glow, the editor, and the legend, so they can't drift.
val WorkGreen = Color(0xFF22E06A)
val RestBlue = Color(0xFF38BDF8)
val PrepPurple = Color(0xFF8B5CF6)
val DoneGray = Color(0xFF9CA3AF)

fun glassBorder(): Brush =
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f)))

@Composable
fun GlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val shape = RoundedCornerShape(50)
    // Tinted pills get a bit more body colour and a uniform border (even top-to-bottom, not a
    // top-weighted gradient) so the colour sits evenly across the whole pill.
    val fill = if (tint != null) tint.copy(alpha = 0.22f) else GlassFill
    val border = if (tint != null) SolidColor(tint.copy(alpha = 0.55f)) else glassBorder()
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 24.dp, vertical = if (big) 18.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = if (big) 22.sp else 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun GlassCircle(glyph: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}
