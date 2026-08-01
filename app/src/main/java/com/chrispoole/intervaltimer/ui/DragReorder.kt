package com.chrispoole.intervaltimer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a lazy list.
 *
 * The list itself stays the source of truth: the moment the dragged card's middle crosses into a
 * neighbour's slot, the move is committed to the backing list and everything else slides aside
 * through the lazy list's own `animateItem` placement animation — nothing is faked.
 *
 * The card under the finger is then drawn at `pickedUpAt + dragged`, an absolute viewport position
 * that ignores where its slot has moved to. That one line is what makes it feel solid: the card
 * stays pinned to the finger however many times it swaps places and however far the list
 * auto-scrolls underneath it.
 *
 * Indices here are *list* indices, headers and footers included — [rememberDragDropState] takes the
 * range that may be reordered, and the caller maps that range onto its own data.
 */
@Stable
class DragDropState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val draggable: () -> IntRange,
    private val onMove: (from: Int, to: Int) -> Boolean,
    private val onPickUp: () -> Unit,
    private val onSwap: () -> Unit,
    private val edgePx: Float,
    private val maxScrollPerFrame: Float,
) {
    private var draggingKey by mutableStateOf<Any?>(null)
    private var draggingIndex = -1
    private var pickedUpAt = 0
    private var dragged by mutableFloatStateOf(0f)
    private var lastDelta = 0f

    // A lift that just snapped back to its slot would undo the whole illusion, so the card keeps
    // its offset for one last animation home after the finger goes. The offset is handed over
    // synchronously on release — waiting for the coroutine to start would flash the card into its
    // slot for a frame first.
    private var settlingKey by mutableStateOf<Any?>(null)
    private var settleOffset by mutableFloatStateOf(0f)
    private var settleJob: Job? = null

    val isDragging: Boolean get() = draggingKey != null

    /** True while [key] is under the finger — the card is lifted and should look it. */
    fun isLifted(key: Any): Boolean = key == draggingKey

    /** True while [key] is drawn out of its slot, either held or easing back into it. */
    fun isFloating(key: Any): Boolean = key == draggingKey || key == settlingKey

    /** Pixels to shift [key]'s card by so it tracks the finger. */
    fun offsetFor(key: Any): Float = when (key) {
        draggingKey -> infoFor(key)?.let { pickedUpAt + dragged - it.offset } ?: 0f
        settlingKey -> settleOffset
        else -> 0f
    }

    private fun infoFor(key: Any?): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    fun onDragStart(key: Any) {
        val info = infoFor(key) ?: return
        // Only this card's own settle is interrupted — cancelling another card's would teleport it
        // the rest of the way home for no reason. Its leftover offset is carried into the new
        // drag: zeroing it would teleport the card to its slot on the frame the finger lands.
        var carried = 0f
        if (settlingKey == key) {
            carried = settleOffset
            settleJob?.cancel()
            settlingKey = null
            settleOffset = 0f
        }
        draggingKey = key
        draggingIndex = info.index
        pickedUpAt = info.offset
        dragged = carried
        lastDelta = 0f
        onPickUp()
    }

    fun onDrag(deltaY: Float) {
        if (draggingKey == null) return
        dragged += deltaY
        if (deltaY != 0f) lastDelta = deltaY
        clampToRegion()
    }

    /**
     * Keep the floating card inside the reorderable region: it can't be carried over the header
     * above the first card or the buttons below the last one. When either end of the region is
     * scrolled out of view its position is unknowable, so the viewport edge stands in — which is
     * also exactly where auto-scroll takes over.
     */
    private fun clampToRegion() {
        val info = infoFor(draggingKey) ?: return
        val layout = listState.layoutInfo
        val range = draggable()
        val visible = layout.visibleItemsInfo
        val lo = visible.firstOrNull { it.index == range.first }?.offset?.toFloat()
            ?: layout.viewportStartOffset.toFloat()
        val hi = (visible.firstOrNull { it.index == range.last }?.let { it.offset + it.size }
            ?: layout.viewportEndOffset).toFloat() - info.size
        val top = pickedUpAt + dragged
        dragged += top.coerceIn(lo, hi.coerceAtLeast(lo)) - top
    }

    fun onDragEnd() {
        val key = draggingKey ?: return
        val restingAt = offsetFor(key)
        draggingKey = null
        draggingIndex = -1
        dragged = 0f
        if (restingAt == 0f) return
        // One settle at a time: two coroutines writing the same offset would fight over it.
        settleJob?.cancel()
        settleOffset = restingAt
        settlingKey = key
        settleJob = scope.launch {
            Animatable(restingAt).animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
            ) { settleOffset = value }
            if (settlingKey == key) {
                settlingKey = null
                settleOffset = 0f
            }
        }
    }

    /**
     * One frame of the drag: swap if the card has travelled into a neighbour, then scroll if it's
     * being held against an edge. Driven by frames rather than by pointer events so that holding
     * still at the top or bottom keeps scrolling instead of stalling.
     */
    internal suspend fun onFrame(frames: Float) {
        val info = infoFor(draggingKey) ?: return
        val top = pickedUpAt + dragged
        // Skip a frame if the last swap hasn't been laid out yet, otherwise the same move gets
        // applied twice off one stale reading.
        if (info.index == draggingIndex) {
            val target = neighbourToSwapWith(top, info.size)
            // A move the owner refuses (it would break a rule of its own) leaves the card where it
            // is: it keeps tracking the finger, and springs back on release.
            if (target >= 0 && onMove(draggingIndex, target)) {
                draggingIndex = target
                onSwap()
            }
        }
        val scroll = edgeScroll(top, info.size, frames)
        // Never scroll the card's own slot off the screen. It can fall behind the finger — a move
        // the owner keeps refusing pins it in place — and once the lazy list stops composing that
        // slot there is nothing left to draw the card with, so it would vanish mid-drag.
        val slotWouldLeave = (scroll > 0f && info.offset <= listState.layoutInfo.viewportStartOffset) ||
            (scroll < 0f && info.offset + info.size >= listState.layoutInfo.viewportEndOffset)
        if (scroll != 0f && !slotWouldLeave) listState.scrollBy(scroll)
        // Auto-scroll moves the region under a stationary finger, so re-clamp here too — the
        // pointer callback alone only runs while the finger moves.
        clampToRegion()
    }

    /**
     * The neighbour the card has travelled far enough to trade places with, or -1.
     *
     * Deliberately *not* "is the card's middle inside the neighbour's slot": with cards of different
     * heights that test is not stable. Swapping past a taller neighbour leaves the middle inside the
     * slot that neighbour has just moved into, so it swaps straight back, and the card sits there
     * flickering between two positions.
     *
     * An edge against the neighbour's midpoint is self-consistent instead: the reverse test is
     * exactly the complement of the forward one, so a swap can never immediately undo itself,
     * whatever the two heights are.
     */
    private fun neighbourToSwapWith(top: Float, size: Int): Int {
        val range = draggable()
        val visible = listState.layoutInfo.visibleItemsInfo
        val next = visible.firstOrNull { it.index == draggingIndex + 1 && it.index in range }
        if (next != null && top + size > next.offset + next.size / 2f) return next.index
        val previous = visible.firstOrNull { it.index == draggingIndex - 1 && it.index in range }
        if (previous != null && top < previous.offset + previous.size / 2f) return previous.index
        return -1
    }

    /**
     * Auto-scroll speed, ramping up over the last [edgePx] of the viewport. [frames] is the actual
     * frame time in units of one 60Hz frame — without it the speed would double on a 120Hz panel
     * and drift whenever an adaptive display changes rate mid-drag.
     */
    private fun edgeScroll(top: Float, size: Int, frames: Float): Float {
        val layout = listState.layoutInfo
        val pastBottom = (top + size) - (layout.viewportEndOffset - edgePx)
        val pastTop = (layout.viewportStartOffset + edgePx) - top
        // A card taller than the viewport is past both edges at once; the direction of travel breaks
        // the tie so it doesn't just pick one and run away.
        return when {
            pastBottom > 0f && (pastTop <= 0f || lastDelta > 0f) ->
                (pastBottom / edgePx).coerceAtMost(1f) * maxScrollPerFrame * frames
            pastTop > 0f -> -(pastTop / edgePx).coerceAtMost(1f) * maxScrollPerFrame * frames
            else -> 0f
        }
    }
}

/**
 * @param draggable the list indices that may be reordered (skip headers and footers).
 * @param onMove commit a move from one list index to another — the list must reflect it immediately.
 *   Return false to refuse it, and the drag carries on as if the card had never crossed.
 */
@Composable
fun rememberDragDropState(
    listState: LazyListState,
    draggable: IntRange,
    onMove: (from: Int, to: Int) -> Boolean,
): DragDropState {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val range by rememberUpdatedState(draggable)
    val move by rememberUpdatedState(onMove)
    val state = remember(listState) {
        DragDropState(
            listState = listState,
            scope = scope,
            draggable = { range },
            onMove = { from, to -> move(from, to) },
            onPickUp = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            onSwap = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            edgePx = with(density) { 72.dp.toPx() },
            maxScrollPerFrame = with(density) { 14.dp.toPx() },
        )
    }
    LaunchedEffect(state.isDragging) {
        if (!state.isDragging) return@LaunchedEffect
        var last = 0L
        while (true) {
            val t = withFrameNanos { it }
            val dt = if (last == 0L) 16_666_666L else (t - last).coerceIn(0L, 50_000_000L)
            last = t
            state.onFrame(dt / 16_666_666f)
        }
    }
    return state
}

/**
 * The grab point for a draggable card. A dedicated handle rather than a long-press on the card
 * itself: the cards are full of steppers and toggles, and a handle can't be triggered by mistake.
 *
 * Keyed on the card's identity rather than its position — keying on the index would tear the
 * gesture down the instant the card swapped slots, dropping it mid-drag.
 */
@Composable
fun DragHandle(
    key: Any,
    label: String,
    state: DragDropState,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    // detectDragGestures fires neither callback when its node is torn down mid-gesture — a card
    // deleted by a second finger, or a header that stops being shown — so the drag would never end.
    DisposableEffect(key) { onDispose { if (state.isLifted(key)) state.onDragEnd() } }
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { state.onDragStart(key) },
                    onDragEnd = { state.onDragEnd() },
                    onDragCancel = { state.onDragEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        state.onDrag(amount.y)
                    },
                )
            }
            // Dragging is a gesture TalkBack can't perform, so the same two moves are offered as
            // accessibility actions on the handle.
            .semantics {
                contentDescription = "Reorder $label"
                customActions = listOfNotNull(
                    onMoveUp?.let { CustomAccessibilityAction("Move up") { it(); true } },
                    onMoveDown?.let { CustomAccessibilityAction("Move down") { it(); true } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        GripDots()
    }
}

/** The universal "grab me" mark. Drawn rather than typed — no font is guaranteed to have it. */
@Composable
fun GripDots(modifier: Modifier = Modifier, alpha: Float = 0.55f, height: androidx.compose.ui.unit.Dp = 20.dp) {
    Canvas(modifier.size(width = height * 0.6f, height = height)) {
        val r = size.height / 12f
        val columns = listOf(size.width * 0.25f, size.width * 0.75f)
        val rows = listOf(size.height * 0.16f, size.height * 0.5f, size.height * 0.84f)
        columns.forEach { x ->
            rows.forEach { y -> drawCircle(Color.White.copy(alpha = alpha), r, Offset(x, y)) }
        }
    }
}
