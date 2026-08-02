package com.chrispoole.intervaltimer.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
// How much of the frame the box shows. 1 is the whole composition, blooms and the black between
// them, which is what the timer wants. Below 1 it crops to the middle, where the light is.
uniform float iZoom;
layout(color) uniform half4 glow;
$HASH
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;
    p *= iZoom;
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

/**
 * [minFrameMs] throttles what is PUBLISHED, not the frame loop: the loop still rides the animation
 * clock (which is what stops it when the app isn't visible), it just leaves `value` alone until the
 * interval has passed, and an unchanged State invalidates no draw. Default 0 publishes every frame,
 * which is what the running timer wants — nothing here feeds a cue or the clock, but the timer's
 * glow is the countdown and gets the display's full rate.
 */
@Composable
private fun rememberShaderTime(minFrameMs: Long = 0L): State<Float> = produceState(0f) {
    // Reduced motion: never start the loop, so iTime stays 0 and the drift and the grain's
    // per-frame reshuffle stop. Not the same as freezing the picture — the running timer still
    // redraws whenever `progress` ticks, because that brightness IS the countdown rather than
    // decoration. On the home, where nothing else changes, it really is one frame. The glow stays
    // either way; it's the phase colour, and dropping it would leave the timer on a black screen.
    if (Settings.reducedMotion) return@produceState
    val start = withInfiniteAnimationFrameMillis { it }
    var published = start
    while (true) {
        withInfiniteAnimationFrameMillis { frame ->
            if (frame - published >= minFrameMs) {
                published = frame
                value = (frame - start) / 1000f
            }
        }
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
            // The whole composition: the blooms AND the black they sit in. That contrast is the
            // effect on a screen you're staring at for a minute.
            shader.setFloatUniform("iZoom", 1f)
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
private const val SWATCH_ZOOM = 0.35f

/**
 * @param zoom how much of the composition to show. The default crops to the lit middle, which is
 *   what a colour sample wants. Pass 1f where the swatch is big enough for the falloff to read as
 *   depth rather than as darkness — the language tiles are five times the area of a theme stripe,
 *   and they hold a numeral that the gradient sits behind.
 */
@Composable
fun AuraSwatch(
    glow: Color,
    modifier: Modifier = Modifier,
    seed: Float,
    zoom: Float = SWATCH_ZOOM,
) {
    if (Build.VERSION.SDK_INT < 33) {
        // The seed is ignored here: there's no shader to re-time, and pre-33 devices get identical
        // swatches rather than a second bespoke gradient to keep in sync. The outer stop is the
        // glow rather than the timer fallback's near-black, for the same reason SWATCH_ZOOM exists —
        // a swatch is a colour sample, not a picture of a dark screen. ponytail: API 33 is the floor
        // this app is really built for.
        val prog = 0.55f + 0.45f * SWATCH_PROGRESS
        Box(
            modifier.background(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.62f * prog), glow.copy(alpha = 0.38f * prog)),
                ),
            ),
        )
        return
    }
    val shader = remember { RuntimeShader(AURA_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    Box(
        modifier.drawWithCache {
            shader.setFloatUniform("iResolution", size.width, size.height)
            // Cropped to the middle of the composition. At full frame a 35×40 stripe puts its
            // corners out where the blooms have fallen away, and the darkest pixel measured 2–9% of
            // the brightest — a swatch that is mostly showing you black. On the timer that contrast
            // is the effect; here the job is to show a colour, so this takes the lit middle and
            // leaves the vignette to the screen that earns it.
            shader.setFloatUniform("iZoom", zoom)
            onDrawBehind {
                shader.setFloatUniform("iTime", seed)
                shader.setFloatUniform("iProgress", SWATCH_PROGRESS)
                shader.setColorUniform("glow", glow.toArgb())
                drawRect(brush)
            }
        }
    )
}

/**
 * Distant weaving aurora over AMOLED black, in the current palette's colours.
 *
 * A theme swap repaints it between one frame and the next, so the three colours cross-fade. That is
 * the whole of the transition: the shader is a sum of three coloured curtains, so interpolating what
 * it is handed interpolates what it draws — no second layer, no blend pass, nothing to keep in sync.
 *
 * 80ms, linear: five frames at 60Hz and ten at 120, which is enough to read as a fade rather than a
 * cut without anyone waiting for it. Linear because a colour ramp with an ease on it arrives late
 * and draws attention to itself, which is the opposite of the point.
 *
 * Held as State and read inside the draw lambda, never unwrapped with `by` — the same rule
 * `HomeSection`'s `box` follows. Read in composition, the whole background would rebuild on every
 * frame of the fade for three values only the shader ever sees.
 */
@Composable
fun HomeBackground(modifier: Modifier = Modifier) {
    val fade = tween<Color>(durationMillis = 80, easing = LinearEasing)
    val work = animateColorAsState(WorkColor, fade, label = "work")
    val prep = animateColorAsState(PrepColor, fade, label = "prep")
    val rest = animateColorAsState(RestColor, fade, label = "rest")
    // Deliberately NOT gated on Settings.minimalBg. Minimal is about the running timer — the screen
    // you actually stare at mid-set. Home, presets and settings keep their aurora; the only way to
    // lose colour here is to pick a palette that hasn't got any. What they don't need is the
    // display's full rate: these screens sit idle for minutes with nothing else invalidating, and a
    // 120Hz panel was rasterizing this full-screen shader 120 times a second for a drift the eye
    // can't resolve at a quarter of that.
    if (Build.VERSION.SDK_INT < 33) {
        // AGSL fallback: the same three colours as a barely-there vertical wash over black.
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(
                        prep.value.copy(alpha = 0.10f),
                        work.value.copy(alpha = 0.06f),
                        Color(0xFF060608),
                    ),
                ),
            ),
        )
        return
    }
    // ~30fps. The curtains sway at 0.4 rad/s across a smoothstep 0.34 uv wide — roughly 2px of
    // travel per 33ms step against a ~370px soft edge — and the grain is ±1.5/255 over black, so
    // the slower republish is invisible where a quarter of the shader draws is not.
    val time = rememberShaderTime(minFrameMs = 33)
    val shader = remember { RuntimeShader(HOME_AGSL) }
    val brush = remember { ShaderBrush(shader) }
    Box(
        modifier.drawWithCache {
            shader.setFloatUniform("iResolution", size.width, size.height)
            onDrawBehind {
                shader.setFloatUniform("iTime", time.value)
                shader.setColorUniform("cWork", work.value.toArgb())
                shader.setColorUniform("cPrep", prep.value.toArgb())
                shader.setColorUniform("cRest", rest.value.toArgb())
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
fun SplitProgress(remaining: Float, color: Color, modifier: Modifier = Modifier) {
    // Read inside onDrawBehind, never captured by the cache block. Captured, a new `remaining` every
    // 33ms made drawWithCache rebuild two Paths and two native PathMeasures per frame — the exact
    // trap RESEARCH.md §3.1 flags. Now the geometry is built once per size change.
    val rem = rememberUpdatedState(remaining)
    val col = rememberUpdatedState(color)
    Box(
        modifier.drawWithCache {
            // Fixed rather than parameters: the one call site never overrode them, and the 6dp
            // inset under an 18dp glow is quoted as fact by MainActivity's compact-layout maths.
            val r = 40.dp.toPx()
            val i = 6.dp.toPx()
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
            val glow = Stroke(18.dp.toPx(), cap = StrokeCap.Round)
            val crisp = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
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
