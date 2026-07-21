package com.chrispoole.intervaltimer.ui

import android.graphics.RuntimeShader
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val HASH = """
float hash(float2 p) {
    p = fract(p * float2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}
"""

// Timer: near-black canvas with soft, drifting, phase-colored glow blooms + animated film grain.
// Deep blacks (pow), wide/blurry blooms, movement. The colour IS light, not a fill.
private val AURA_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float iProgress;
layout(color) uniform half4 glow;
$HASH
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;
    float t = iTime * 0.6;
    float prog = 0.62 + 0.38 * iProgress;   // start brighter (was 0.4); glow still builds toward the boundary

    float aura = 0.0;
    aura += smoothstep(0.95, 0.0, length(p - float2(0.15 * sin(t*0.5), 0.05 + 0.06*cos(t*0.4)))) * 0.9;
    aura += smoothstep(0.85, 0.0, length(p - float2(0.30 * sin(t*0.33), -0.34))) * 0.5;
    aura += smoothstep(0.90, 0.0, length(p - float2(-0.30 + 0.10*cos(t*0.5), 0.33))) * 0.5;
    aura += smoothstep(0.85, 0.0, length(p - float2(0.33, 0.36 + 0.05*sin(t*0.6)))) * 0.4;
    aura = pow(aura, 1.4) * prog;   // pow deepens the black between blooms

    half3 col = glow.rgb * aura;
    col = col / (col + half3(0.65));            // tone-map: keep colour, don't blow to white

    float g = hash(fragCoord + fract(iTime) * 100.0) - 0.5;
    col += g * 0.05;                            // analog grain
    return half4(max(col, half3(0.0)), 1.0);
}
"""

// Home: AMOLED black with a distant, blurred aurora — soft colour curtains that weave and drift,
// mostly black. Not a full gradient fill.
private val HOME_AGSL = """
uniform float2 iResolution;
uniform float iTime;
$HASH
float curtain(float2 uv, float base, float amp, float freq, float speed) {
    float x = base + amp * sin(uv.y * freq + iTime * speed) + amp * 0.5 * cos(uv.y * freq * 2.1 - iTime * speed * 0.6);
    float body = smoothstep(0.34, 0.0, abs(uv.x - x));   // wide + very soft/blurred
    float vert = smoothstep(1.05, 0.05, uv.y);           // brighter toward the top (distant sky)
    return body * vert;
}
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    half3 col = half3(0.0);   // AMOLED black
    // three faint, blurred curtains — a subtle background feature, not the subject
    col += half3(0.14, 0.85, 0.55) * curtain(uv, 0.30, 0.16, 4.5, 0.40) * 0.32; // green
    col += half3(0.55, 0.28, 0.95) * curtain(uv, 0.60, 0.18, 4.0, 0.30) * 0.28; // purple
    col += half3(0.18, 0.50, 0.95) * curtain(uv, 0.82, 0.14, 5.0, 0.50) * 0.24; // blue
    col = col / (col + half3(1.6));   // much dimmer, deep black base
    float g = hash(fragCoord + fract(iTime) * 100.0) - 0.5;
    col += g * 0.012;
    return half4(max(col, half3(0.0)), 1.0);
}
"""

@Composable
private fun rememberShaderTime(): State<Float> = produceState(0f) {
    val start = withInfiniteAnimationFrameMillis { it }
    while (true) {
        withInfiniteAnimationFrameMillis { frame -> value = (frame - start) / 1000f }
    }
}

/** The real display corner radius (px→dp), so the perimeter stroke hugs the actual corners. */
@Composable
fun rememberDisplayCornerRadius(fallback: Dp = 34.dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view) {
        // getRoundedCorner is API 31+; below that we can't query it, so use the fallback radius.
        val px = if (Build.VERSION.SDK_INT >= 31) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        } else 0
        if (px > 0) with(density) { px.toDp() } else fallback
    }
}

/** Full-bleed animated glow over black. [glow] is the phase colour, [progress] 0..1 over the interval. */
@Composable
fun AuraBackground(glow: Color, progress: Float, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 33) {
        // AGSL RuntimeShader is API 33+. Fallback: a phase-coloured radial glow over near-black,
        // brightening with progress like the shader does.
        val prog = 0.55f + 0.45f * progress.coerceIn(0f, 1f)
        Box(modifier.background(Brush.radialGradient(listOf(glow.copy(alpha = 0.5f * prog), Color(0xFF070709)))))
        return
    }
    val time = rememberShaderTime()
    val shader = remember { RuntimeShader(AURA_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    Box(
        modifier.drawWithCache {
            shader.setFloatUniform("iResolution", size.width, size.height)
            onDrawBehind {
                shader.setFloatUniform("iTime", time.value)
                shader.setFloatUniform("iProgress", progress.coerceIn(0f, 1f))
                shader.setColorUniform("glow", glow.toArgb())
                drawRect(brush)
            }
        }
    )
}

/** Flowing purple/green/blue gradient background for the home + settings chrome. */
@Composable
fun HomeBackground(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 33) {
        // AGSL fallback: a subtle dark aurora-tinted gradient over AMOLED black.
        Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF0C0916), Color(0xFF070B0C), Color(0xFF060608)))))
        return
    }
    val time = rememberShaderTime()
    val shader = remember { RuntimeShader(HOME_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    Box(
        modifier.drawWithCache {
            shader.setFloatUniform("iResolution", size.width, size.height)
            onDrawBehind {
                shader.setFloatUniform("iTime", time.value)
                drawRect(brush)
            }
        }
    )
}

/**
 * A glowing progress stroke tracing the rounded-rectangle screen perimeter, hugging the edge.
 * [remaining] 0..1 depletes over the interval (full at start).
 */
@Composable
fun PerimeterProgress(
    remaining: Float,
    color: Color,
    cornerRadius: Dp = 34.dp,
    inset: Dp = 3.dp,
    strokeWidth: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.drawWithCache {
            val r = cornerRadius.toPx()
            val i = inset.toPx()
            val path = Path().apply {
                addRoundRect(RoundRect(i, i, size.width - i, size.height - i, CornerRadius(r, r)))
            }
            val pm = PathMeasure().apply { setPath(path, true) }
            val total = pm.length
            val dst = Path()
            val glowStroke = Stroke(width = (strokeWidth * 4f).toPx(), cap = StrokeCap.Round)
            val midStroke = Stroke(width = (strokeWidth * 2f).toPx(), cap = StrokeCap.Round)
            val crispStroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                val len = total * remaining.coerceIn(0f, 1f)
                dst.rewind()
                pm.getSegment(0f, len, dst, true)
                drawPath(dst, color.copy(alpha = 0.18f), style = glowStroke)
                drawPath(dst, color.copy(alpha = 0.35f), style = midStroke)
                drawPath(dst, color, style = crispStroke)
            }
        }
    )
}

/**
 * Perimeter progress that originates at the left-middle and right-middle of the vertical sides and
 * splits outward: four bright tips run up/down the sides, around the corners, and along the top and
 * bottom edges toward their centres. [remaining] (1..0) is how far each arm has grown — the full
 * perimeter at the start, retreating to the two side mid-points as time runs out.
 */
@Composable
fun SplitProgress(
    remaining: Float,
    color: Color,
    cornerRadius: Dp = 40.dp,
    inset: Dp = 6.dp,
    strokeWidth: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.drawWithCache {
            val r = cornerRadius.toPx()
            val i = inset.toPx()
            val left = i
            val right = size.width - i
            val midY = size.height / 2f
            // Half-perimeter path from left-middle, over the top (vert=-1) or under the bottom (vert=+1),
            // to right-middle. Its length-midpoint lands exactly on that edge's centre.
            fun halfPath(vert: Int): Path {
                val edgeY = if (vert > 0) size.height - i else i
                return Path().apply {
                    moveTo(left, midY)
                    lineTo(left, edgeY - vert * r)
                    quadraticBezierTo(left, edgeY, left + r, edgeY)
                    lineTo(right - r, edgeY)
                    quadraticBezierTo(right, edgeY, right, edgeY - vert * r)
                    lineTo(right, midY)
                }
            }
            val pmRight = PathMeasure().apply { setPath(halfPath(-1), false) } // top half
            val pmLeft = PathMeasure().apply { setPath(halfPath(1), false) }   // bottom half
            val glow = Stroke((strokeWidth * 3f).toPx(), cap = StrokeCap.Round)
            val crisp = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            val seg = Path()
            fun DrawScope.arms(pm: PathMeasure) {
                val len = (pm.length / 2f) * remaining.coerceIn(0f, 1f)
                if (len <= 0f) return
                seg.rewind(); pm.getSegment(0f, len, seg, true)          // arm from top-centre
                drawPath(seg, color.copy(alpha = 0.20f), style = glow); drawPath(seg, color, style = crisp)
                seg.rewind(); pm.getSegment(pm.length - len, pm.length, seg, true) // arm from bottom-centre
                drawPath(seg, color.copy(alpha = 0.20f), style = glow); drawPath(seg, color, style = crisp)
            }
            onDrawBehind {
                arms(pmRight)
                arms(pmLeft)
            }
        }
    )
}
