package com.chrispoole.intervaltimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A line in unit space: stave runs (0,-1)..(0,1), quadrants extend one unit sideways. */
data class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

// Digit shapes in the top-right quadrant, local coords x=0..1 (out from stave), y=0..1 (down from top).
private val DIGITS: Array<List<Stroke>> = arrayOf(
    emptyList(),                                                        // 0 — nothing drawn
    listOf(Stroke(0f, 0f, 1f, 0f)),                                     // 1 top bar
    listOf(Stroke(0f, 1f, 1f, 1f)),                                     // 2 lower bar
    listOf(Stroke(0f, 0f, 1f, 1f)),                                     // 3 down-diagonal
    listOf(Stroke(0f, 1f, 1f, 0f)),                                     // 4 up-diagonal
    listOf(Stroke(0f, 0f, 1f, 0f), Stroke(0f, 1f, 1f, 0f)),             // 5 = 1 + 4
    listOf(Stroke(1f, 0f, 1f, 1f)),                                     // 6 outer vertical
    listOf(Stroke(0f, 0f, 1f, 0f), Stroke(1f, 0f, 1f, 1f)),             // 7 = 1 + 6
    listOf(Stroke(0f, 1f, 1f, 1f), Stroke(1f, 0f, 1f, 1f)),             // 8 = 2 + 6
    listOf(Stroke(0f, 0f, 1f, 0f), Stroke(0f, 1f, 1f, 1f), Stroke(1f, 0f, 1f, 1f)), // 9
)

/**
 * Strokes for [n] in 0..9999, including the stave. Units go top-right, tens top-left,
 * hundreds bottom-right, thousands bottom-left — each quadrant the same nine shapes mirrored.
 */
fun cistercian(n: Int): List<Stroke> {
    require(n in 0..9999) { "Cistercian numerals cover 0..9999, got $n" }
    val out = mutableListOf(Stroke(0f, -1f, 0f, 1f))
    // (digit, x sign, y flip): flipped quadrants measure downward from the bottom of the stave.
    val quadrants = listOf(
        Triple(n % 10, 1f, false),
        Triple(n / 10 % 10, -1f, false),
        Triple(n / 100 % 10, 1f, true),
        Triple(n / 1000, -1f, true),
    )
    for ((digit, sx, flip) in quadrants) {
        for (s in DIGITS[digit]) {
            // + 0f folds IEEE -0.0 back to 0.0 so data-class equality behaves.
            out += if (flip) {
                Stroke(s.x1 * sx + 0f, 1f - s.y1, s.x2 * sx + 0f, 1f - s.y2)
            } else {
                Stroke(s.x1 * sx + 0f, s.y1 - 1f, s.x2 * sx + 0f, s.y2 - 1f)
            }
        }
    }
    return out
}

/** Draws [number] as a single Cistercian glyph, scaled to fill the composable's bounds. */
@Composable
fun CistercianNumeral(
    number: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 4.dp,
) {
    val strokes = cistercian(number)
    Canvas(modifier) {
        // Glyph is 2 wide x 2 tall in unit space; inset by half a stroke so ends aren't clipped.
        val px = strokeWidth.toPx()
        val scale = minOf(size.width, size.height) / 2f - px / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (s in strokes) {
            drawLine(
                color = color,
                start = Offset(cx + s.x1 * scale, cy + s.y1 * scale),
                end = Offset(cx + s.x2 * scale, cy + s.y2 * scale),
                strokeWidth = px,
                cap = StrokeCap.Round,
            )
        }
    }
}
