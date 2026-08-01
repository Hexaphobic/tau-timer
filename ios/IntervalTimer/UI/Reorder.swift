import SwiftUI
import UIKit
import os

/// Diagnostic tap for the drag state machine. Debug builds only; stream it with
/// `xcrun simctl spawn booted log stream --level debug --predicate 'category == "reorder"'`.
private let dragLog = Logger(subsystem: "com.chrispoole.intervaltimer", category: "reorder")

/// Drag-to-reorder for a hand-laid-out vertical stack.
///
/// A `List` with `.onMove` would be the free option, but it only reorders in edit mode and it
/// insists on its own row chrome — these screens are glass cards floating over a shader background,
/// so the list would be fighting the design the whole way.
///
/// The model is the one the Android build settled on: the card under the finger is drawn at an
/// offset, the rows it has passed shift one slot to open the gap, and the backing array is rewritten
/// **once, on the drop**. Nothing moves twice, and a move the owner refuses simply rides back.
///
/// Row heights are captured from the live layout, so cards of different heights reorder correctly.
@MainActor
final class ReorderState: ObservableObject {

    @Published private(set) var draggingIndex: Int?
    @Published private(set) var dragged: CGFloat = 0
    /// Bumped at every release and again when a gesture claims the card; the pending landing commit
    /// checks it, so a re-grab during the glide-in can never have a stale commit fire underneath it.
    private var dropGeneration = 0
    /// `dragged` at the moment the live gesture claimed the card; nil when no gesture is down.
    private var grabBase: CGFloat?
    /// True while a refused drop is springing the card home. For that whole glide the MODEL value
    /// of `dragged` is already 0 — only the presentation is still travelling — so a re-grab would
    /// rebase from 0 and pin the card a full drag's travel off the finger. Grabs are simply ignored
    /// until the spring lands: the window is ~0.3s and the card is already going where a refusal
    /// sends it. Cleared in the refusal animation's completion, and defensively wherever
    /// `draggingIndex` resets — a stuck flag would dead-lock every handle on this state.
    private var landingRefused = false
    /// Set when a gesture touches down during the refusal glide. Its translation is measured from
    /// a touch-down point that goes stale while the glide is being ignored, so if it claimed the
    /// moment `landingRefused` cleared it would pin the card that stale distance off the finger
    /// for the whole drag. A refused gesture never claims; the poison lifts with the finger.
    /// Shared, like `buzzed`: a second finger's release on another handle clears it a beat early —
    /// a two-finger window nobody hits by accident, at worst one stale-offset drag.
    private var grabRefused = false

    private var heights: [Int: CGFloat] = [:]
    private var count = 0
    /// Whether this drag has already given its pick-up haptic.
    private var buzzed = false

    /// Called from the layout: each row reports its own height so targets can be computed against
    /// the real geometry rather than an assumed uniform pitch.
    func record(_ measured: [Int: CGFloat]) {
        heights = measured
        count = measured.count
        #if DEBUG
        dragLog.debug("record n=\(measured.count) h=\(measured.sorted { $0.key < $1.key }.map { "\($0.key):\(Int($0.value))" }.joined(separator: " "))")
        #endif
    }

    var isDragging: Bool { draggingIndex != nil }

    func isLifted(_ index: Int) -> Bool { index == draggingIndex }

    /// True while the row is drawn out of its slot — held, or gliding into its slot after release.
    /// `draggingIndex` deliberately stays set through the glide (see the drop in `handleGesture`),
    /// which is exactly how long the card has to stay on top of its neighbours.
    func isFloating(_ index: Int) -> Bool { index == draggingIndex }

    /// Where the index'th row sits under the finger. Applied raw: it has to be exactly where the
    /// finger is. On the drop the whole thing — this returning to 0 and the array rewrite — rides
    /// one spring transaction, which is what lands the card the few points into its slot.
    func dragOffset(for index: Int) -> CGFloat {
        index == draggingIndex ? dragged : 0
    }

    /// How far the index'th row steps aside to open the gap. Animated by `reorderOffset` — a whole
    /// card-height applied in one frame reads as the row being deleted and redrawn somewhere else,
    /// which is what it looked like before, and worst with cards of different heights.
    func shift(for index: Int) -> CGFloat {
        guard let from = draggingIndex, let to = target, index != from else { return 0 }
        let pitch = heights[from] ?? 0
        if from < to, index > from, index <= to { return -pitch }
        if to < from, index >= to, index < from { return pitch }
        return 0
    }

    /// The slot the card has travelled far enough to land in.
    ///
    /// Walks the real row heights out from the grabbed slot rather than dividing by an average: with
    /// cards of different heights an average pitch overshoots on the short ones and stalls on the
    /// tall ones.
    var target: Int? {
        guard let from = draggingIndex, count > 0 else { return nil }
        var index = from
        var travelled: CGFloat = 0
        if dragged > 0 {
            while index + 1 < count {
                let next = heights[index + 1] ?? 0
                if dragged - travelled < next / 2 { break }
                travelled += next
                index += 1
            }
        } else if dragged < 0 {
            while index - 1 >= 0 {
                let prev = heights[index - 1] ?? 0
                if -dragged - travelled < prev / 2 { break }
                travelled += prev
                index -= 1
            }
        }
        return index
    }

    /// The gesture to attach to a row's grip handle.
    ///
    /// `commit` performs the move and returns whether it was accepted — an owner with a rule of its
    /// own (no two rests in a row) can refuse, and the card then springs back to where it started.
    func handleGesture(index: Int, commit: @escaping (Int, Int) -> Bool) -> some Gesture {
        // minimumDistance 0, and this is the whole reason the drag works at all.
        //
        // At any non-zero distance the enclosing ScrollView's pan recogniser gets the touch first
        // and keeps it: `scrollDisabled(isDragging)` can only flip once the drag has recognised,
        // which is already too late. The page scrolled instead of the card lifting, and letting go
        // handed the ScrollView a fling — which is exactly what "they just shoot to the top and
        // bottom of the screen, it doesn't matter where I let go" was. Claiming the gesture on
        // touch-down disables the scroll before there is any movement to steal.
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .onChanged { value in
                // The grabBase rebase below is only sound on the accepted glide, where the model
                // value of `dragged` IS the visual destination. On the refusal glide it is the
                // START of the journey home (0), so a rebase would teleport the card — refuse the
                // claim instead and let the spring finish. Refusing once poisons the whole
                // gesture: its touch-down point is stale by however far the card still had to
                // travel, so a late claim after the flag clears would drag the card that far off
                // the finger. Checked BEFORE ownership: the neighbours are springing home too, so
                // a grab on ANY handle in this window has a stale origin — and no live drag can
                // exist while the flag is set, so the poison can't kill one.
                guard !self.landingRefused else { self.grabRefused = true; return }
                // Own or claimable only: draggingIndex stays set through the landing glide, and
                // without this a drag begun on another card's handle in that window would steer
                // the card that is still landing.
                guard self.draggingIndex == nil || self.draggingIndex == index else { return }
                guard !self.grabRefused else { return }
                if self.grabBase == nil {                 // this gesture is claiming — fresh grab or mid-glide re-grab
                    self.dropGeneration += 1              // void any pending landing commit
                    // 0 on a fresh grab; delta if a glide was in flight. On the accepted glide the
                    // model value is the destination, so a re-grab is off by at most the
                    // un-travelled remainder — under half a row by construction of `target`. Same
                    // bound on the to == from spring-back (a sub-half-pitch drag riding home).
                    // Both accepted; `landingRefused` only covers the refusal glide, whose
                    // residual would be the whole drag.
                    self.grabBase = self.dragged
                    self.draggingIndex = index
                    #if DEBUG
                    dragLog.debug("begin idx=\(index) heights=\(self.heights.count)")
                    #endif
                }
                // Finger-on-glass feedback while you are actively dragging a row — held back until
                // the finger has actually moved, because touch-down now counts as the gesture
                // starting and a press that goes nowhere is not a drag. Distinct from a workout
                // cue: the phone never buzzes at you mid-set, on either platform.
                if !self.buzzed, abs(value.translation.height) > 4 {
                    self.buzzed = true
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                }
                let previous = self.target
                self.dragged = self.clamped((self.grabBase ?? 0) + value.translation.height, from: index)
                if self.target != previous {
                    UISelectionFeedbackGenerator().selectionChanged()
                }
            }
            .onEnded { _ in
                self.buzzed = false
                self.grabRefused = false  // the poison lifts with the finger, whatever the guards below decide
                // A gesture releasing during a refusal glide never claimed anything (its onChanged
                // was refused above), so its release must not bump the generation or start a second
                // landing — the refusal completion owns the cleanup.
                guard self.draggingIndex == index, !self.landingRefused else { return }
                // Past the guard so a second finger's release on another handle can't clear the
                // live gesture's base — that would make its next onChanged re-claim and rebase,
                // double-counting the translation.
                self.grabBase = nil
                let from = index
                let to = self.target ?? from
                // NEVER commit while anything is animating — that is the lesson this drop has now
                // taught twice. The array rewrite makes each card's continuity the difference of
                // two large opposing deltas (its layout jump and its offset release), and when
                // those ride different animation clocks they desync: on a big-travel release both
                // cards visibly rewound ~half a slot and replayed the move (measured: the
                // neighbour reached 10pt from home, then snapped back 176pt).
                //
                // So the release animates exactly ONE thing: `dragged`, gliding the card the rest
                // of the way onto its destination slot. The neighbours stay stepped aside because
                // draggingIndex and target stay live. Only when everything is at rest — the card
                // pixel-exact over its new slot — does the completion swap the array and zero the
                // state, both unanimated: the layout jump and the offset release then cancel by
                // arithmetic, not by choreography, and there is nothing left to replay.
                self.dropGeneration += 1
                let gen = self.dropGeneration
                let delta = self.slotDelta(from: from, to: to)
                #if DEBUG
                dragLog.debug("release from=\(from) dragged=\(Int(self.dragged)) to=\(to) delta=\(Int(delta)) heights=\(self.heights.count)")
                #endif
                // The landing itself must not animate ANYTHING. The card is already pixel-exact
                // over its slot and every delta cancels arithmetically — but the neighbours'
                // `.animation(value: shift)` modifiers would still catch their shift unwinding and
                // replay it against the instant layout jump, which is precisely the rewind this
                // drop kept exhibiting. disablesAnimations silences implicit animations too.
                let land = {
                    var t = Transaction()
                    t.disablesAnimations = true
                    // Ask inside the disabled transaction: an accepted move rewrites the array and must not
                    // animate. A refusal mutates nothing, so there is no layout jump to cancel — but nothing
                    // unwinds "continuously" either: `dragged` is @Published, not Animatable, so the refusal
                    // branch below is ONE body pass in which target and every neighbour's shift snap to 0 at
                    // the model level. The card's offset rides that branch's explicit spring; the neighbours
                    // ride their own `.animation(value: shift)` spring in reorderOffset. They only stay in
                    // step because both springs share response 0.31 / damping 1 and start the same instant —
                    // change one without the other and the card and its neighbours part company mid-glide.
                    let moved = withTransaction(t) { to != from && commit(from, to) }
                    #if DEBUG
                    dragLog.debug("land moved=\(moved)")
                    #endif
                    if moved || to == from {
                        withTransaction(t) {
                            self.draggingIndex = nil
                            self.dragged = 0
                        }
                        // Defensive pair to the completion's clear: anywhere draggingIndex resets,
                        // the refusal flag must too, or a missed completion strands every handle.
                        self.landingRefused = false
                    } else {
                        self.landingRefused = true
                        withAnimation(.spring(response: 0.31, dampingFraction: 1), completionCriteria: .logicallyComplete) {
                            self.dragged = 0
                        } completion: {
                            // Before the guard: whatever else happened, the glide is over and grabs
                            // must come back — a stuck flag would dead-lock every handle.
                            self.landingRefused = false
                            guard gen == self.dropGeneration, self.draggingIndex == from else { return }
                            self.draggingIndex = nil
                        }
                    }
                }
                if abs(self.dragged - delta) < 0.5 {
                    // Nothing left to glide — a clamped drag is already pinned to its slot. Land
                    // now; a zero-length animation's completion timing is nothing to build on.
                    land()
                } else {
                    withAnimation(.spring(response: 0.31, dampingFraction: 1), completionCriteria: .logicallyComplete) {
                        self.dragged = delta
                    } completion: {
                        guard gen == self.dropGeneration, self.draggingIndex == from else { return }
                        land()
                    }
                }
            }
    }

    /// Keep the floating card inside the stack it belongs to: it can't be carried above the first
    /// slot or below the last one, so it can never be let go somewhere there is no slot to land in.
    ///
    /// Android has had this since the beginning (`DragDropState.clampToRegion`) and the port went out
    /// without it, which is why a card would follow the finger straight off the top of the screen and
    /// over the header. Simpler than the Kotlin because the list doesn't scroll under the finger
    /// here — `scrollDisabled` holds it still for the length of the drag — so the stack's own bounds
    /// are the whole story, with no viewport case and no auto-scroll to hand over to.
    private func clamped(_ raw: CGFloat, from: Int) -> CGFloat {
        guard let own = heights[from], !heights.isEmpty else { return raw }
        let above = slotTop(from)
        let below = stackHeight - above - own
        return min(max(raw, -above), max(below, -above))
    }

    /// Height of the whole reorderable stack.
    private var stackHeight: CGFloat { heights.values.reduce(0, +) }

    /// Distance from the top of the stack to the top of the index'th slot.
    private func slotTop(_ index: Int) -> CGFloat {
        (0..<max(index, 0)).reduce(CGFloat(0)) { $0 + (heights[$1] ?? 0) }
    }

    /// Distance between two slots, summing the real heights in between — the exact offset that puts
    /// the released card pixel-for-pixel over its destination slot before the array is touched.
    private func slotDelta(from: Int, to: Int) -> CGFloat {
        guard from != to else { return 0 }
        let range = from < to ? (from + 1)...to : to...(from - 1)
        let span = range.reduce(CGFloat(0)) { $0 + (heights[$1] ?? 0) }
        return from < to ? span : -span
    }
}

/// Per-row height reporting. Rows put this in their background; the container collects it.
struct RowHeightKey: PreferenceKey {
    static var defaultValue: [Int: CGFloat] { [:] }
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) {
        value.merge(nextValue()) { _, new in new }
    }
}

extension View {
    /// Place a row during a drag: the finger's own offset applied raw, plus the step aside it makes
    /// for the card being carried past it, which eases.
    ///
    /// Two separate `.offset`s rather than one sum, so the spring can be attached to the second
    /// alone — a single animated offset would put the dragged card a spring behind the finger. The
    /// spring is Android's `animateItem` placement default (`StiffnessMediumLow`, no bounce) written
    /// out in SwiftUI's terms, so a reorder feels the same on both.
    func reorderOffset(_ state: ReorderState, _ index: Int) -> some View {
        let shift = state.shift(for: index)
        return offset(y: state.dragOffset(for: index))
            .offset(y: shift)
            .animation(.spring(response: 0.31, dampingFraction: 1), value: shift)
    }

    /// Report this row's height under `index`, for `ReorderState.record`.
    func reportRowHeight(_ index: Int) -> some View {
        background(
            GeometryReader { geo in
                Color.clear.preference(key: RowHeightKey.self, value: [index: geo.size.height])
            }
        )
    }

    /// The card treatment, but able to fade the whole box out — which is what lets the home screen
    /// put a box *around* the rows already on screen instead of crossfading to a different view of
    /// them. `boxed` false is the plain home: same rows, no chrome at all.
    func sectionChrome(boxed: Bool, lifted: Bool, shape: RoundedRectangle) -> some View {
        self
            .background {
                ZStack {
                    if lifted {
                        shape.fill(.ultraThinMaterial)
                        shape.fill(.white.opacity(0.12))
                    } else {
                        shape.fill(glassFill)
                    }
                }
                .opacity(boxed ? 1 : 0)
            }
            .overlay {
                shape.strokeBorder(lifted ? AnyShapeStyle(Color.white.opacity(0.5))
                                          : AnyShapeStyle(glassBorder()), lineWidth: 1)
                    .opacity(boxed ? 1 : 0)
            }
            .shadow(color: .black.opacity(lifted && boxed ? 0.55 : 0),
                    radius: lifted ? 18 : 0, y: lifted ? 8 : 0)
            .scaleEffect(lifted ? 1.03 : 1)
            .animation(.easeOut(duration: 0.18), value: lifted)
    }

    /// The lift treatment shared by every draggable card: a real backdrop blur, a brightening, and a
    /// shadow. On a near-black background a shadow alone all but disappears, which is what makes a
    /// dragged card read as a smear over the one it is passing rather than as something held above it.
    ///
    /// `.ultraThinMaterial` is the blur — it samples what is actually behind the card, so the card
    /// underneath goes soft as this one passes over it. A flat white wash was only ever frost: it
    /// lightened the card without blurring anything, which is the difference between translucent and
    /// glass. The white on top of it is what's left of the brightening.
    func liftedCard(_ lifted: Bool, shape: RoundedRectangle) -> some View {
        self
            .background {
                if lifted {
                    shape.fill(.ultraThinMaterial)
                        .overlay(shape.fill(.white.opacity(0.12)))
                } else {
                    shape.fill(glassFill)
                }
            }
            .overlay(shape.strokeBorder(lifted ? AnyShapeStyle(Color.white.opacity(0.5))
                                               : AnyShapeStyle(glassBorder()), lineWidth: 1))
            .shadow(color: .black.opacity(lifted ? 0.55 : 0), radius: lifted ? 18 : 0, y: lifted ? 8 : 0)
            .scaleEffect(lifted ? 1.03 : 1)
            .animation(.easeOut(duration: 0.18), value: lifted)
    }
}

/// The grab point for a draggable card. A dedicated handle rather than a long-press on the card
/// itself: the cards are full of steppers and toggles, and a handle can't be triggered by mistake.
struct DragHandle: View {
    let index: Int
    let label: String
    @ObservedObject var state: ReorderState
    let commit: (Int, Int) -> Bool
    /// Dragging is a gesture VoiceOver can't perform, so the same two moves are offered as actions.
    var onMoveUp: (() -> Void)?
    var onMoveDown: (() -> Void)?

    var body: some View {
        GripDots()
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
            .gesture(state.handleGesture(index: index, commit: commit))
            .accessibilityLabel("Reorder \(label)")
            .accessibilityAction(named: "Move up") { onMoveUp?() }
            .accessibilityAction(named: "Move down") { onMoveDown?() }
    }
}
