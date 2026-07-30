package com.chrispoole.intervaltimer.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrispoole.intervaltimer.Settings

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
layout(color) uniform half4 cWork;
layout(color) uniform half4 cPrep;
layout(color) uniform half4 cRest;
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
    // Three faint, blurred curtains — a subtle background feature, not the subject. They're the
    // palette's own three phase colours, so the home screen always previews the theme you picked.
    col += cWork.rgb * curtain(uv, 0.30, 0.16, 4.5, 0.40) * 0.32;
    col += cPrep.rgb * curtain(uv, 0.60, 0.18, 4.0, 0.30) * 0.28;
    col += cRest.rgb * curtain(uv, 0.82, 0.14, 5.0, 0.50) * 0.24;
    col *= 1.2;                       // user-set: 20% brighter than the original curtains
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

/** Full-bleed animated glow over black. [glow] is the phase colour, [progress] 0..1 over the interval. */
@Composable
fun AuraBackground(glow: Color, progress: Float, modifier: Modifier = Modifier) {
    // Minimal: no bloom, no grain, no shader running at 60fps. Just black, so the only things on
    // screen are the count and the perimeter line ticking down.
    if (Settings.minimalBg) {
        Box(modifier.background(Color.Black))
        return
    }
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

/**
 * Mid-interval: what a phase looks like for most of the time you're staring at it. The swatch is
 * frozen here rather than at either end, where the glow is still building or already peaked.
 */
private const val SWATCH_PROGRESS = 0.5f

/**
 * The timer's own aura, shrunk to a swatch. Deliberately [AURA_AGSL] itself — the same shader the
 * running workout draws — so a theme previews as what GO actually shows instead of as a hand-tuned
 * imitation of it that drifts the first time the real one is touched.
 *
 * Frozen, not animated: a grid of these would otherwise run a dozen shaders at 60fps to show drift
 * nobody is watching. Minimal mode is ignored on purpose — honouring it would render nine black
 * rectangles, which is accurate and useless for picking a theme.
 *
 * [seed] picks WHICH frame of the drift each swatch is frozen at, fed straight to iTime. The four
 * blooms orbit on periods of roughly 17.5s, 21s, 26s and 32s, and those don't divide into each
 * other, so separated seeds give genuinely different compositions rather than the same picture
 * twice. Left at 0 every swatch is the identical frame, which is what made a grid of them read as
 * one image stamped out repeatedly. Deliberately not routed through iProgress: that is only a
 * brightness multiplier, so varying it would make some themes look brighter than others for
 * reasons that have nothing to do with the theme.
 */
@Composable
fun AuraSwatch(glow: Color, modifier: Modifier = Modifier, seed: Float = 0f) {
    if (Build.VERSION.SDK_INT < 33) {
        // Same fallback as the timer, so the two still agree on pre-33 devices. The seed is
        // ignored here: there's no shader to re-time, and pre-33 devices get identical swatches
        // rather than a second bespoke gradient to keep in sync. ponytail: API 33 is the floor
        // this app is really built for.
        val prog = 0.55f + 0.45f * SWATCH_PROGRESS
        Box(modifier.background(Brush.radialGradient(listOf(glow.copy(alpha = 0.5f * prog), Color(0xFF070709)))))
        return
    }
    val shader = remember { RuntimeShader(AURA_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    Box(
        modifier.drawWithCache {
            shader.setFloatUniform("iResolution", size.width, size.height)
            onDrawBehind {
                shader.setFloatUniform("iTime", seed)
                shader.setFloatUniform("iProgress", SWATCH_PROGRESS)
                shader.setColorUniform("glow", glow.toArgb())
                drawRect(brush)
            }
        }
    )
}

/** Distant weaving aurora over AMOLED black, in the current palette's colours. */
@Composable
fun HomeBackground(modifier: Modifier = Modifier) {
    val work = WorkColor
    val prep = PrepColor
    val rest = RestColor
    // Deliberately NOT gated on Settings.minimalBg. Minimal is about the running timer — the screen
    // you actually stare at mid-set. Home, presets and settings keep their aurora; the only way to
    // lose colour here is to pick a palette that hasn't got any.
    if (Build.VERSION.SDK_INT < 33) {
        // AGSL fallback: the same three colours as a barely-there vertical wash over black.
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(prep.copy(alpha = 0.10f), work.copy(alpha = 0.06f), Color(0xFF060608)),
                ),
            ),
        )
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
                shader.setColorUniform("cWork", work.toArgb())
                shader.setColorUniform("cPrep", prep.toArgb())
                shader.setColorUniform("cRest", rest.toArgb())
                drawRect(brush)
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
    // Read inside onDrawBehind, never captured by the cache block. Captured, a new `remaining` every
    // 33ms made drawWithCache rebuild two Paths and two native PathMeasures per frame — the exact
    // trap RESEARCH.md §3.1 flags. Now the geometry is built once per size change.
    val rem = rememberUpdatedState(remaining)
    val col = rememberUpdatedState(color)
    Box(
        modifier.drawWithCache {
            val r = cornerRadius.toPx()
            val i = inset.toPx()
            val cx = size.width / 2f
            val top = i
            val bottom = size.height - i
            // One path per SIDE: top-edge centre, round the corner, down the whole side, round the
            // bottom corner, back to the bottom-edge centre. It's symmetric about the horizontal
            // centre line, so its length-midpoint lands exactly on that side's mid-point.
            //
            // Sliced by side rather than by half — which is the fix for the dot that used to sit at
            // each side's middle. Four arms sliced top/bottom put two segment ENDS on that point, and
            // a round cap on each drew two half-discs back to back: a permanent circle. One stroke
            // per side runs straight through the mid-point, so there is no cap there to draw.
            fun sidePath(horiz: Int): Path {
                val edgeX = if (horiz > 0) size.width - i else i
                return Path().apply {
                    moveTo(cx, top)
                    lineTo(edgeX - horiz * r, top)
                    quadraticTo(edgeX, top, edgeX, top + r)
                    lineTo(edgeX, bottom - r)
                    quadraticTo(edgeX, bottom, edgeX - horiz * r, bottom)
                    lineTo(cx, bottom)
                }
            }
            val pmLeft = PathMeasure().apply { setPath(sidePath(-1), false) }
            val pmRight = PathMeasure().apply { setPath(sidePath(1), false) }
            val glow = Stroke((strokeWidth * 3f).toPx(), cap = StrokeCap.Round)
            val crisp = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            val seg = Path()
            // Grows outward from the side's mid-point in both directions at once, so the two visible
            // tips travel toward the top and bottom edge centres and retreat back as time runs out.
            fun DrawScope.arm(pm: PathMeasure) {
                val c = col.value
                val mid = pm.length / 2f
                val half = mid * rem.value.coerceIn(0f, 1f)
                if (half <= 0f) return
                seg.rewind(); pm.getSegment(mid - half, mid + half, seg, true)
                drawPath(seg, c.copy(alpha = 0.20f), style = glow)
                drawPath(seg, c, style = crisp)
            }
            onDrawBehind {
                arm(pmLeft)
                arm(pmRight)
            }
        }
    )
}
