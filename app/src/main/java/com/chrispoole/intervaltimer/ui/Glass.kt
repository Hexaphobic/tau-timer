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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

// Every glass surface in the app reads from these two, so contrast is tuned here rather than per
// screen. Raised from 0.10/0.45 once the plain home lost its phase colours: with nothing tinted,
// the steppers were the only thing separating the controls from the black behind them.
val GlassFill = Color.White.copy(alpha = 0.16f)

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
 *
 * Two rules decide whether a theme earns its place, and four were cut for failing them: work and
 * rest must be far enough apart in hue to tell apart at a glance mid-set (Aurora was 33°), and a
 * theme must not be a near-copy of one already here (Aurora sat 23° from Default, Solarized 15°
 * from Laser). Gruvbox and Rosé Pine went for saturation — 0.21 and 0.28 — which reads as mud
 * once a colour fills a whole row or screen rather than edging one. Joker went the same way as
 * Aurora: lime-and-purple was Grape's pairing already.
 */
// Declaration order IS the order of the picker, so it's hand-set rather than alphabetical: the
// strongest and most distinct themes lead, and Mono sits third rather than exiled to the end —
// it's a deliberate choice, not the leftover at the bottom of the list.
enum class Palette(val label: String, val work: Color, val rest: Color, val prep: Color) {
    // Dracula went the way of Aurora: 15° from Default is the same theme with a different name.
    DEFAULT("Default", Color(0xFF22E06A), Color(0xFF38BDF8), Color(0xFF8B5CF6)),
    VESPER("Vesper", Color(0xFF99FFE4), Color(0xFFFFC799), Color(0xFFFF8080)),

    // No hue at all. Paired with the Minimal switch this is the plain black-and-white timer; on its
    // own it's a white aura. Work and rest look identical here — that is the whole point, but it
    // does mean the preset list and editor lose their colour coding while it's selected.
    MONO("Mono", Color.White, Color.White, Color.White),

    MIAMI("Miami", Color(0xFFFF2D8F), Color(0xFF05DFD7), Color(0xFFFFC400)),
    TRANCE("Trance", Color(0xFF02D3B0), Color(0xFF6C8BE8), Color(0xFFE51376)),
    GRAPE("Grape", Color(0xFFFF8F00), Color(0xFFB14EFF), Color(0xFFFF4081)),
    SPIDEY("Spidey", Color(0xFFE23636), Color(0xFF0476F2), Color(0xFFFFD400)),
    LASER("Laser", Color(0xFFA8D400), Color(0xFF22C9DC), Color(0xFFFF3D7F)),
    TRON("Tron", Color(0xFFFF6600), Color(0xFF00D4FF), Color(0xFFF0E800)),
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
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.14f)))

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
                    // null = timeout, i.e. still held. true = lifted (a tap). false = cancelled, which
                    // means a parent scroll took the gesture — the finger was passing through, not pressing.
                    val settled = withTimeoutOrNull(REPEAT_DELAY_MS) { waitForUpOrCancellation() != null }
                    if (settled == false) return@awaitEachGesture
                    // A tap commits on lift, like every clickable; a hold's first step is the
                    // loop's — stepping here too would land a double step at the 350ms mark.
                    if (settled == true) {
                        step.value(1)
                        return@awaitEachGesture
                    }
                    var lifted: Unit? = null
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
 * What makes a stepper reachable with TalkBack. Goes on the number between the two circles.
 *
 * [GlassCircle] is pointerInput and nothing else, so it publishes no semantics node at all: the +
 * and − are invisible to a screen reader, and all it finds of the app's primary control is a bare
 * number with no say in what it measures or how to change it.
 *
 * On the number rather than on the row, and deliberately without merging descendants. Every row
 * carrying a stepper also carries something else with semantics of its own — a reorder handle, a
 * WORK/REST chip, a ✕ — and a merged parent's [customActions] REPLACE its children's rather than
 * adding to them, so a row-level node would swallow the handle's Move up/Move down. The number is
 * already the one node TalkBack finds in the trio, so labelling it in place is the single control
 * the row wants, with nothing added to the tree and no hit testing touched.
 *
 * [value] must read off the same expression the row renders. A spoken wording of it is fine and
 * often better ("3 times" for a bare "× 3"); a second way of *deriving* it is not, or the spoken
 * reading and the visible one drift. Where the row's own text is conditional — Settings' "Off" at
 * zero — hoist the string and hand both the same one.
 */
fun Modifier.stepperSemantics(
    label: String,
    value: String,
    onMinus: (Int) -> Unit,
    onPlus: (Int) -> Unit,
): Modifier = semantics {
    contentDescription = label
    stateDescription = value
    // Same shape as the reorder handles': an action list, because the gesture itself — here a press
    // on a circle with no semantics — is not something a screen reader can aim at. Always the single
    // step, never the hold's 2x: there is no way to say "and keep going" one action at a time.
    customActions = listOf(
        CustomAccessibilityAction("Increase") { onPlus(1); true },
        CustomAccessibilityAction("Decrease") { onMinus(1); true },
    )
}

/** The small ✕ that removes whatever it sits on: a home section, an interval row, a name field. */
@Composable
fun CloseX(onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
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

/**
 * The only way off a secondary screen: a small floating disc in the top-left corner, with the
 * screen's own content scrolling underneath it.
 *
 * No title beside it. A pinned bar wide enough to hold a word is a bar that has to clip whatever
 * scrolls past it, and the screen you are looking at is its own label.
 *
 * Android has no backdrop blur — nothing public samples what is behind a composable — so this is the
 * app's glass instead: translucent enough to see the content pass under it, with the same 1dp
 * gradient edge every other surface here wears. iOS uses a real `.ultraThinMaterial`.
 */
@Composable
fun BackPill(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, glassBorder(), CircleShape)
            .noRippleClickable(onBack)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        // The chevron's own side bearings sit it right of centre; nudge it back so the glyph, not
        // its box, is what looks centred.
        Text(
            "‹",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 28.sp,
            modifier = Modifier.offset(x = (-1).dp, y = (-3).dp),
        )
    }
}
