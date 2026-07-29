package com.chrispoole.intervaltimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
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
import com.chrispoole.intervaltimer.Settings

val GlassFill = Color.White.copy(alpha = 0.10f)

/**
 * Selectable colour palettes, named after the monkeytype themes they're taken from.
 *
 * Every palette needs three plainly different hues. Work, rest and prepare have to be tellable
 * apart at a glance, mid-set, sweating, from arm's length — so a gorgeous monochrome theme is not
 * an option here however well it reads on a typing test. That rules out most of monkeytype's
 * catalogue, which only commits to one accent.
 *
 * They also want one channel near zero. These colours are painted as a full-screen bloom, and a
 * pale one has no dark channel to keep the background black with — monkeytype's miami yellow
 * (#FFF591) turned the whole timer into a flat olive field on device. So hexes are the originals
 * where they hold up, and pushed toward saturation where they don't.
 */
enum class Palette(val label: String, val work: Color, val rest: Color, val prep: Color) {
    DEFAULT("Default", Color(0xFF22E06A), Color(0xFF38BDF8), Color(0xFF8B5CF6)),
    DRACULA("Dracula", Color(0xFF50FA7B), Color(0xFF8BE9FD), Color(0xFFBD93F9)),
    GRUVBOX("Gruvbox", Color(0xFFB8BB26), Color(0xFF83A598), Color(0xFFD3869B)),
    ROSE_PINE("Rosé Pine", Color(0xFF9CCFD8), Color(0xFFC4A7E7), Color(0xFFF6C177)),
    SOLARIZED("Solarized", Color(0xFF859900), Color(0xFF2AA198), Color(0xFFD33682)),
    AURORA("Aurora", Color(0xFF00E980), Color(0xFF2EC5D6), Color(0xFFB94DA1)),
    MIAMI("Miami", Color(0xFFFF2D8F), Color(0xFF05DFD7), Color(0xFFFFC400)),
    LASER("Laser", Color(0xFFA8D400), Color(0xFF22C9DC), Color(0xFFFF3D7F)),
    TRANCE("Trance", Color(0xFF02D3B0), Color(0xFF6C8BE8), Color(0xFFE51376)),
    GRAPE("Grape", Color(0xFFFF8F00), Color(0xFFB14EFF), Color(0xFFFF4081)),
    JOKER("Joker", Color(0xFF99DE1E), Color(0xFF9B6FE0), Color(0xFFFF5C5C)),
    SPIDEY("Spidey", Color(0xFFE23636), Color(0xFF0476F2), Color(0xFFFFD400)),
    TRON("Tron", Color(0xFFFF6600), Color(0xFF00D4FF), Color(0xFFF0E800)),
    VESPER("Vesper", Color(0xFF99FFE4), Color(0xFFFFC799), Color(0xFFFF8080)),

    // No hue at all. Paired with the Minimal switch this is the plain black-and-white timer; on its
    // own it's a white aura. Work and rest look identical here — that is the whole point, but it
    // does mean the preset list and editor lose their colour coding while it's selected.
    MONO("Mono", Color.White, Color.White, Color.White),
}

// Phase colours — one source for the timer glow, the home aurora, the editor and the legend, so
// they can't drift. Getters, not vals: they read Settings.palette as a snapshot state, so picking a
// new theme recomposes every one of them without a restart.
val WorkColor: Color get() = Settings.palette.work
val RestColor: Color get() = Settings.palette.rest
val PrepColor: Color get() = Settings.palette.prep

// Fixed across themes. Done is deliberately colourless — the workout is over, nothing is signalled
// — and destructive red is a safety signal, not decoration, so no palette gets to repaint it.
val DoneGray = Color(0xFF9CA3AF)

/** Destructive actions: ending a workout, deleting a preset. */
val DangerRed = Color(0xFFFF4D4D)

/**
 * Clickable without the Material ripple. The ripple is a circle that expands from your fingertip
 * across the whole element, and on anything big — a full screen, a preset card, large type — it
 * reads as an effect the app is playing at you rather than as feedback.
 */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null) { onClick() }

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

/** Press-and-hold before a stepper starts repeating — long enough that a normal tap is just a tap. */
private const val REPEAT_DELAY_MS = 350L

/** Gap between auto-repeats. Constant: the hold speeds up by taking bigger steps, not faster ones. */
private const val REPEAT_EVERY_MS = 90L

/** How long you hold before each repeat counts double — 5s steps become 10s, i.e. 2x. */
private const val DOUBLE_AFTER_MS = 1_000L

/**
 * The +/- button. Every one of these in the app is a stepper, so hold-to-repeat lives here rather
 * than in each caller: press and it fires once, keep holding and it repeats.
 *
 * [onStep] is handed a multiplier — 1 normally, 2 once you've held past [DOUBLE_AFTER_MS] — so a
 * long hold tops out at twice the speed and no further. Deliberately a hard ceiling: the old
 * tap-streak escalation could reach 6x and would blow straight past the number you were aiming for.
 *
 * [size] shrinks the whole control, glyph included — the editor's interval rows carry a stepper,
 * a phase switch and a delete on one line, which the full-size circle can't fit on a narrow phone.
 */
@Composable
fun GlassCircle(
    glyph: String,
    onStep: (multiplier: Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
) {
    // rememberUpdatedState: pointerInput(Unit) captures onStep once, and the steppers pass a fresh
    // lambda closing over the *current* value every recomposition. Without this, a hold would keep
    // re-applying the step to the value as it was when the gesture started, and 30s + 5s would come
    // out 35 every time.
    val step = rememberUpdatedState(onStep)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    step.value(1)
                    // null == the timeout won, i.e. still held. A lift or a cancel completes the
                    // block and returns Unit, which ends the repeat.
                    var lifted = withTimeoutOrNull(REPEAT_DELAY_MS) { waitForUpOrCancellation(); Unit }
                    var held = REPEAT_DELAY_MS
                    while (lifted == null) {
                        step.value(if (held >= DOUBLE_AFTER_MS) 2 else 1)
                        lifted = withTimeoutOrNull(REPEAT_EVERY_MS) { waitForUpOrCancellation(); Unit }
                        held += REPEAT_EVERY_MS
                    }
                }
            },
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
