import SwiftUI
import IntervalTimerCore

/// The home screen: dial in work / rest / rounds and GO, or fold the whole thing into a sequence of
/// sections. One section is the classic home; more than one gives each its own card.
struct HomeView: View {
    @Binding var rows: [HomeRow]
    let onGo: (Workout) -> Void
    let onSettings: () -> Void
    let onPresets: () -> Void

    @StateObject private var reorder = ReorderState()
    /// The outer ×N — how many times the whole home runs. Live only while `grouped`; with a single
    /// section that section's own repeat is the total, and a second number governing one thing is a
    /// number you can only get wrong. Crossing the boundary MOVES the value rather than parking it
    /// (see `addBlock`/`remove`), so the number the user is looking at never changes meaning.
    @State private var repeatAll = Settings.shared.homeRepeatAll
    @State private var naming = false
    @State private var name = ""
    @State private var saved = false

    /// The classic home — three bare steppers, no box — is exactly one section holding work and rest.
    /// Add a second section OR a third interval and every section folds into its own card, because at
    /// that point there is a shape to show and the plain layout has nowhere to show it.
    /// `items.count == 2`, not just `isBasic`: a section can be whittled down to a lone Work by
    /// deleting its Rest row inside the card, and `isBasic` still says yes. Falling back to the plain
    /// home there strands you — the plain home has no `+ interval`, so nothing left on screen puts
    /// the Rest back. A one-interval section keeps its card and its way out.
    private var solo: Bool { rows.count == 1 && rows[0].block.items.count == 2 && rows[0].block.isBasic }

    /// Two different lines, and they are not the same line. `solo` is about *chrome* — one plain
    /// work/rest section wears no card. `grouped` is about *repeats*: an outer ×N only means
    /// anything once there is more than one section to wrap. A single section holding work/work/rest
    /// gets a card but no group, because its own repeat already is the total and a second number
    /// governing one thing is a number you can only get wrong.
    private var grouped: Bool { rows.count > 1 }

    private var effectiveRepeatAll: Int { grouped ? repeatAll : 1 }

    /// What the one Rounds control reads, whichever side of that boundary we are on.
    private var homeRounds: Int { grouped ? repeatAll : (rows.first?.block.repeatCount ?? DEFAULT_ROUNDS) }

    private func setHomeRounds(_ n: Int) {
        let v = max(n, 1)
        if grouped { setRepeatAll(v) } else if !rows.isEmpty { change(0) { $0.repeatCount = v } }
    }

    var body: some View {
        ZStack(alignment: .top) {
            HomeBackground().ignoresSafeArea()
            list
            if saved {
                NoticePill(text: "Saved to presets")
                    .padding(.bottom, 28)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .allowsHitTesting(false)
                    .task {
                        try? await Task.sleep(for: .seconds(2.2))
                        saved = false
                    }
            }
        }
        // The whole home is remembered, not just the first section's three numbers — a section can
        // hold a sequence of its own now, and dropping that on relaunch would be the same bug as
        // never saving it. Mirrored from the live list rather than written on the edit path: drag,
        // the move-up/down actions and delete all change the list without going through change().
        .onChange(of: rows) { _, live in
            Settings.shared.updateHome(live.map(\.block))
        }
    }

    private var list: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 0) {
                    // The whole header row is the list's first item: Presets and Settings scroll
                    // away with the page exactly as Δτ does, because that is all "leaving the top
                    // of the screen" ever needed to mean. Pinned over the scroll they overlapped
                    // the cards passing under them, and everything that followed — a fraction off
                    // the scroll offset, then accumulated deltas, a commit on release, an in-bias
                    // near the top, two geometry probes and a preference key — was scaffolding to
                    // fake this for two buttons.
                    //
                    // The 56 is the space the row used to hold open as an overlay, so nothing below
                    // it moved. The negative padding undoes the button's own 14 so the labels land
                    // 18 from the edge, where they have always been, rather than 38 in.
                    ZStack(alignment: .top) {
                        Text("Δτ")
                            .font(.system(size: 19, weight: .bold))
                            .tracking(1)
                            .foregroundStyle(.white.opacity(0.5))
                            .padding(.top, 12)
                            .allowsHitTesting(false)
                        HStack {
                            PlainTextButton(text: "Presets", action: onPresets)
                            Spacer()
                            PlainTextButton(text: "Settings", action: onSettings)
                        }
                        .padding(.top, 4)
                        .padding(.horizontal, -20)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56, alignment: .top)
                    if !solo { saveControl }
                    summaryLine
                    // The group box: one rounded frame around the ×N and every section under it, so
                    // "repeat all of this" is something you can see rather than a sentence you read.
                    VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { pair in
                        section(pair.offset, pair.element)
                            // The copy grows downward out of the card above it, and its spring is
                            // held back a beat so the box lands first: box closes, then it pushes a
                            // duplicate out below. Anchored to the top because that is the edge it
                            // is supposed to be emerging from.
                            .transition(.asymmetric(
                                insertion: .scale(scale: 0.94, anchor: .top)
                                    .combined(with: .opacity)
                                    .animation(.spring(response: 0.34, dampingFraction: 0.82).delay(0.14)),
                                removal: .scale(scale: 0.94, anchor: .top)
                                    .combined(with: .opacity)
                                    .animation(.easeIn(duration: 0.18))
                            ))
                    }
                    homeRoundsRow
                    }
                    .padding(grouped ? 8 : 0)
                    .background(groupBox)
                    footer
                }
                .onPreferenceChange(RowHeightKey.self) { reorder.record($0) }
                .padding(.horizontal, 24)
                // No top padding: the header row is the list's first item now and holds that space
                // open itself.
                .padding(.bottom, 56)
                // minHeight, not a fixed one: short content sits centred, long content scrolls.
                .frame(maxWidth: .infinity, minHeight: geo.size.height)
            }
            // A card under the finger must not also drag the page along with it.
            .scrollDisabled(reorder.isDragging)
        }
    }

    /// The home's Rounds: how many times the whole thing runs, top to bottom.
    ///
    /// ONE view across both shapes, deliberately — never a plain one that leaves and a group one
    /// that arrives. With a single section this is that section's own repeat; with more than one it
    /// is the outer ×N governing all of them, and the screen moves the number across that boundary
    /// so it never changes under you. Keeping it as one view is what makes "Add intervals" read as
    /// *the rounds you were already setting becoming the rounds for the group*, rather than as one
    /// control being thrown away and a different one appearing somewhere else.
    private var homeRoundsRow: some View {
        // Flexible spacers with animated caps, not two alignments. An HStack can't animate from
        // spread to centred, but it can animate the space either side of its contents, which comes
        // to the same thing and is continuous: the outer pair opens as the inner one closes, walking
        // the control from the plain home's spread row — lined up with Work and Rest above it — to
        // centred under the group. The cap only has to exceed the slack a phone row can have.
        HStack(spacing: 0) {
            Spacer().frame(maxWidth: grouped ? 260 : 0)
            Text("Rounds")
                .font(.system(size: 20))
                .foregroundStyle(.white)
                // Flexible spacers either side will squeeze a Text before they give up their own
                // width, which folded the label into "Roun / ds" the moment the row centred.
                .fixedSize()
                .padding(.vertical, 6)
            Spacer().frame(maxWidth: grouped ? 0 : 260)
            // The weighted gap collapses to nothing when centred, which butts the label against the
            // −. A fixed sliver keeps them apart once there is no flexible space left to do it.
            Spacer().frame(width: grouped ? 12 : 0)
            GlassCircle(glyph: "−", onStep: { m in setHomeRounds(homeRounds - m) }, size: grouped ? 50 : 54)
            Text("\(homeRounds)")
                .font(.system(size: 24, weight: .bold))
                // scaleEffect, not a bigger font: a font-size change re-renders the glyph and snaps,
                // where a scale interpolates with everything else moving around it.
                .scaleEffect(grouped ? 1.25 : 1)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .frame(width: grouped ? 78 : 96)
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { setHomeRounds(DEFAULT_ROUNDS) }
            GlassCircle(glyph: "+", onStep: { m in setHomeRounds(homeRounds + m) }, size: grouped ? 50 : 54)
            Spacer().frame(maxWidth: grouped ? 260 : 0)
        }
        .padding(.top, grouped ? 4 : 16)
        .padding(.bottom, grouped ? 2 : 32)
    }

    /// What GO will run, at the top where you read it before you start rather than at the bottom
    /// where it sat on the button.
    ///
    /// Sets, not "rounds": the control below already owns that word and means the other thing by it
    /// — this is the count the timer will walk you through, the one the pips draw.
    private var summaryLine: some View {
        let sets = homeSets(rows.map(\.block), repeatAll: effectiveRepeatAll)
        return Text("\(sets) \(sets == 1 ? "set" : "sets")  ·  "
                    + clockLabel(homeSeconds(rows.map(\.block), repeatAll: effectiveRepeatAll)))
            .font(.system(size: 14))
            .tracking(1)
            .foregroundStyle(.white.opacity(0.5))
            .padding(.bottom, 14)
    }

    private var groupBox: some View {
        let shape = RoundedRectangle(cornerRadius: 36, style: .continuous)
        return ZStack {
            shape.fill(glassFill.opacity(0.45))
            shape.strokeBorder(.white.opacity(0.14), lineWidth: 1)
        }
        .opacity(grouped ? 1 : 0)
    }

    private func setRepeatAll(_ n: Int) {
        repeatAll = max(n, 1)
        Settings.shared.updateHomeRepeatAll(repeatAll)
    }

    // MARK: - Sections

    /// One section, plain or carded — deliberately ONE view rather than two branches of an `if`.
    ///
    /// Two branches is what made "Add intervals" a crossfade: SwiftUI saw an entirely different
    /// subtree and dissolved one into the other, so the work and rest rows you were looking at died
    /// and a fresh pair faded up in their place. Kept as one view they are the *same* rows the whole
    /// way through, and the only thing that animates is the box closing around them — and, going
    /// back, the box being let go.
    private func section(_ i: Int, _ row: HomeRow) -> some View {
        let b = row.block
        let isLast = i == rows.count - 1
        // Dragging is a gesture VoiceOver can't perform, so the ends of the list lose the move that
        // would run off them and keep the other.
        let up: (() -> Void)? = i > 0 ? { _ = move(i, i - 1) } : nil
        let down: (() -> Void)? = isLast ? nil : { _ = move(i, i + 1) }
        return VStack(spacing: solo ? 16 : 6) {
            // The header carries the drag grip, the per-section ×N, the section's own running time
            // and the ✕ — every one of which is meaningless when there is only one section. It
            // arrives with the second one.
            if grouped {
                HStack(spacing: 0) {
                    DragHandle(
                        index: i, label: "section \(i + 1)", state: reorder,
                        commit: move, onMoveUp: up, onMoveDown: down
                    )
                    GlassCircle(glyph: "−", onStep: { m in change(i) { $0.repeatCount = max($0.repeatCount - m, 1) } }, size: 36)
                    Text("× \(b.repeatCount)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 52)
                    GlassCircle(glyph: "+", onStep: { m in change(i) { $0.repeatCount += m } }, size: 36)
                    Spacer(minLength: 6)
                    // How long this block is — the intervals under it, run the × N to its left. M:SS,
                    // not formatMs: these are block lengths now rather than shares of the whole
                    // workout, so most are under a minute, and a bare "50" in a column under "1:30"
                    // reads as anything but fifty seconds.
                    Text(clockLabel(sectionSeconds(b)))
                        .font(.system(size: 13))
                        .foregroundStyle(.white.opacity(0.55))
                    Spacer().frame(width: 6)
                    CloseX { remove(i) }
                }
                // Unfolds from the top edge rather than appearing: the header is the lid of the box,
                // so it should arrive with it.
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            IntervalStack(block: b, tinted: !solo) { next in change(i) { $0 = next } }
        }
        .padding(.horizontal, solo ? 0 : 14)
        .padding(.vertical, solo ? 0 : 10)
        .frame(maxWidth: .infinity)
        .sectionChrome(boxed: !solo, lifted: reorder.isLifted(i),
                       shape: RoundedRectangle(cornerRadius: 28, style: .continuous))
        // 0 when solo: the Rounds row below carries its own 16pt top padding, and that must be the
        // WHOLE gap — Work↔Rest inside the stack is 16pt, so Rest↔Rounds has to be 16pt too or the
        // plain home reads as two steppers plus a straggler.
        .padding(.bottom, solo ? 0 : 10)
        .reportRowHeight(i)
        .reorderOffset(reorder, i)
        .zIndex(reorder.isFloating(i) ? 1 : 0)
    }

    // MARK: - Save as preset

    private var saveControl: some View {
        Group {
            if naming {
                NameField(
                    name: $name,
                    onClose: { naming = false; name = "" },
                    onSave: {
                        PresetStore.shared.add(
                            homePreset(rows.map(\.block), repeatAll: effectiveRepeatAll)
                                .renamed(name.trimmingCharacters(in: .whitespacesAndNewlines))
                        )
                        naming = false
                        name = ""
                        saved = true
                    }
                )
            } else {
                GlassPill(text: "Save as preset", action: { naming = true }, wide: true)
            }
        }
        // Animating the container's size turns the swap into the button growing into the field,
        // rather than one control vanishing and another appearing.
        .animation(.easeInOut(duration: 0.26), value: naming)
        .padding(.bottom, 14)
    }

    // MARK: - Footer

    private var footer: some View {
        VStack(spacing: 0) {
            if !solo {
                // Clear of the group frame's bottom edge — the + adds a section *to* the group, so
                // it sits just outside it rather than inside.
                Spacer().frame(height: 18)
                GlassPill(text: "+", action: addBlock, wide: true)
                Spacer().frame(height: 20)
            }
            GlassPill(text: "GO", action: go, wide: true, big: true)
            if solo {
                Spacer().frame(height: 12)
                GlassPill(text: "+  Add intervals", action: addBlock, wide: true)
            }
        }
    }

    // MARK: - Edits

    /// Guard the captured index: a second tap can land before the view rebuilds.
    private func change(_ i: Int, _ edit: (inout Block) -> Void) {
        guard rows.indices.contains(i) else { return }
        edit(&rows[i].block)
    }

    /// Deliberately NOT wrapped in its own withAnimation: on the drop path Reorder.swift runs this
    /// inside the single drop transaction, and adding another here would nest a second one. The
    /// accessibility move actions call it bare, which is fine — VoiceOver doesn't watch animations.
    private func move(_ from: Int, _ to: Int) -> Bool {
        guard rows.indices.contains(from), rows.indices.contains(to), from != to else { return false }
        rows.insert(rows.remove(at: from), at: to)
        return true
    }

    private func remove(_ i: Int) {
        // count > 1, not just a bounds check: the ✕ only exists once there are two sections, so
        // "never remove the last one" is the real invariant. Two ✕ taps landing in one frame both
        // passed the bounds check and took rows to empty, where the footer's + reads rows.last.
        guard rows.count > 1, rows.indices.contains(i) else { return }
        // Going the other way the box is let go: the copy shrinks away and, if that leaves one plain
        // work/rest section, the chrome fades off the one that's left in the same move.
        withAnimation(.easeInOut(duration: 0.28)) {
            rows.remove(at: i)
            // Back to one: fold the home's rounds into the section that's left, so the number on
            // screen keeps meaning the same thing and the workout keeps its length.
            if rows.count == 1 {
                rows[0].block.repeatCount = max(rows[0].block.repeatCount * repeatAll, 1)
                setRepeatAll(1)
            }
        }
    }

    /// The number on screen has to keep meaning the same thing across the one-to-two boundary.
    ///
    /// With one section, Rounds *is* that section's repeat. With two, it is the outer ×N. Adding a
    /// section therefore lifts the number you were already looking at up to the group and leaves the
    /// sections at 1 — otherwise "4" would quietly start meaning "each section, four times" and the
    /// workout would come out sixteen rounds long instead of four. Removing folds it back down the
    /// same way, so the round trip is lossless.
    private func addBlock() {
        guard let last = rows.last else { return }
        // This animation is the box closing; the inserted row carries its own, delayed, so the two
        // read as one move in two beats rather than everything arriving at once.
        withAnimation(.easeOut(duration: 0.24)) {
            let nextId = (rows.map(\.id).max() ?? 0) + 1
            if rows.count == 1 {
                setRepeatAll(last.block.repeatCount)
                var one = last.block
                one.repeatCount = 1
                rows[0].block = one
                rows.append(HomeRow(id: nextId, block: one))
            } else {
                rows.append(HomeRow(id: nextId, block: last.block))
            }
        }
    }

    private func go() {
        // The same builder the total above it is measured from, so the number and the button can
        // never describe different workouts.
        onGo(homeWorkout(rows.map(\.block),
                         repeatAll: effectiveRepeatAll,
                         prepareMs: Settings.shared.prepareSec * 1000))
    }
}

// MARK: - Name field

/// Name-and-save, as one fully rounded glass pill so it sits in the same family as every other
/// control.
private struct NameField: View {
    @Binding var name: String
    let onClose: () -> Void
    let onSave: () -> Void

    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 6) {
            TextField("", text: $name,
                      prompt: Text("Name this preset").foregroundStyle(.white.opacity(0.45)))
                .textFieldStyle(.plain)
                .font(.system(size: 16))
                .foregroundStyle(.white)
                .tint(.white)
                .autocorrectionDisabled()
                .focused($focused)
            CloseX(action: onClose)
            // Nothing typed, nothing to save — that's the whole gate, no second step.
            GlassPill(text: "Save", action: onSave,
                      enabled: !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(.leading, 20)
        .padding(.trailing, 6)
        .padding(.vertical, 6)
        .background(glassFill, in: Capsule())
        .overlay(Capsule().strokeBorder(glassBorder(), lineWidth: 1))
        // Opened deliberately, so it takes the caret without a second tap.
        .onAppear { focused = true }
    }
}

// MARK: - Stepper row

/// How long one pass of the gradient takes to drift across a row.
private let DRIFT_SECONDS: Double = 9

/// Label, then − / value / + . Double-tapping the number puts it back to the stock value — the way
/// out of a hold that overshot.
struct StepperRow: View {
    let label: String
    let value: String
    let onMinus: (Int) -> Void
    let onPlus: (Int) -> Void
    let onReset: () -> Void
    var compact = false
    var tint: Color?
    /// Tap the label to flip work↔rest. Its own hit box, well clear of the steppers: the editor
    /// learned the hard way that a whole-row tap turns a near-miss on − into a phase change.
    var onLabelTap: (() -> Void)?
    /// Sits at the right-hand end, inside the pill — the ✕ that removes this interval.
    var onDelete: (() -> Void)?

    @ObservedObject private var settings = Settings.shared

    var body: some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.system(size: compact ? 15 : 20))
                .foregroundStyle(.white)
                .frame(width: compact ? 70 : 90, alignment: .leading)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
                .onTapGesture { onLabelTap?() }
                .allowsHitTesting(onLabelTap != nil)
            Spacer(minLength: 0)
            GlassCircle(glyph: "−", onStep: onMinus, size: compact ? 40 : 54)
            Text(value)
                .font(.system(size: compact ? 17 : 24, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: compact ? 72 : 96)
                .contentShape(Rectangle())
                .onTapGesture(count: 2, perform: onReset)
            GlassCircle(glyph: "+", onStep: onPlus, size: compact ? 40 : 54)
            if let onDelete {
                CloseX(action: onDelete)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, tint == nil ? 0 : 12)
        .padding(.vertical, tint == nil ? 0 : (compact ? 4 : 6))
        .background(alignment: .leading) { wash }
        .overlay(edge)
    }

    /// What GO actually shows is a full wash of the phase colour, so the row is filled edge to edge
    /// and the only movement is dips in brightness drifting across it. No shape inside the pill — a
    /// shape is what read as a sticker sitting on the row instead of the row being lit.
    ///
    /// Minimal mode's timer is black with only the perimeter stroke, so its preview is the same idea:
    /// a bubble around the edge, nothing filled in. Only glow rows pay for an animation clock; plain
    /// and minimal rows never start one.
    @ViewBuilder private var wash: some View {
        if let tint, !settings.minimalBg {
            GeometryReader { geo in
                TimelineView(.animation) { ctx in
                    // The stops are one span of the pattern written twice, so the two spans of
                    // gradient always cover the row whatever the phase, and sliding it exactly one
                    // span over the loop makes the wrap invisible.
                    let span = geo.size.width * 1.5
                    let phase = ctx.date.timeIntervalSinceReferenceDate
                        .truncatingRemainder(dividingBy: DRIFT_SECONDS) / DRIFT_SECONDS
                    LinearGradient(stops: [
                        .init(color: tint.opacity(0.58), location: 0.00),
                        .init(color: tint.opacity(0.43), location: 0.16),
                        .init(color: tint.opacity(0.60), location: 0.33),
                        .init(color: tint.opacity(0.58), location: 0.50),
                        .init(color: tint.opacity(0.43), location: 0.66),
                        .init(color: tint.opacity(0.60), location: 0.83),
                        .init(color: tint.opacity(0.58), location: 1.00),
                    ], startPoint: .leading, endPoint: .trailing)
                    .frame(width: span * 2)
                    .offset(x: phase * span - span)
                }
            }
            .clipShape(Capsule())
        }
    }

    @ViewBuilder private var edge: some View {
        if let tint {
            Capsule().strokeBorder(tint.opacity(settings.minimalBg ? 0.55 : 0.28),
                                   lineWidth: settings.minimalBg ? 1.5 : 1)
        }
    }
}

// MARK: - Intervals inside a section

/// A section's intervals, one row each, and — on a card — the + that adds another.
///
/// Shared by the card and by the plain single-section home, so the two can't drift. `tinted` is doing
/// double duty as "this is a card": the plain home is deliberately just work, rest and rounds, so it
/// gets the rows and nothing else. No +, no ✕, no tap-to-flip. Building a section with a shape of its
/// own is what "Add intervals" is for, and that is where all of it lives.
struct IntervalStack: View {
    let block: Block
    /// Cards wash their rows in the phase colour and carry the editing controls; the plain home
    /// stays colourless and bare.
    let tinted: Bool
    let onChange: (Block) -> Void

    var body: some View {
        VStack(spacing: tinted ? 6 : 16) {
            ForEach(Array(block.items.enumerated()), id: \.offset) { pair in
                let j = pair.offset
                let iv = pair.element
                let isWork = iv.phase == .work
                StepperRow(
                    label: isWork ? "Work" : "Rest",
                    value: secLabel(iv.durationSec),
                    // Work has a 5s floor; rest keeps its 0, because dialling rest to nothing has
                    // always been how you say "no rest here" and the row stays put so you can dial
                    // it back.
                    onMinus: { m in set(j, iv.with(durationSec: max(iv.durationSec - 5 * m, isWork ? 5 : 0))) },
                    onPlus: { m in set(j, iv.with(durationSec: iv.durationSec + 5 * m)) },
                    onReset: { set(j, iv.with(durationSec: isWork ? DEFAULT_WORK_SEC : DEFAULT_REST_SEC)) },
                    compact: tinted,
                    tint: tinted ? (isWork ? workColor : restColor) : nil,
                    onLabelTap: tinted ? { set(j, iv.with(phase: isWork ? .rest : .work)) } : nil,
                    // Only once there is something to remove — a section with one interval is the
                    // floor. On the plain home, dialling a rest to 0 is how you drop it, same as
                    // it has always been.
                    onDelete: tinted && block.items.count > 1 ? { remove(j) } : nil
                )
            }
            if tinted {
                PlainTextButton(text: "+ interval", action: append, size: 14)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func set(_ j: Int, _ iv: SeqInterval) {
        guard block.items.indices.contains(j) else { return }
        var items = block.items
        items[j] = iv
        onChange(Block(items, block.repeatCount))
    }

    private func remove(_ j: Int) {
        guard block.items.count > 1, block.items.indices.contains(j) else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            onChange(Block(block.items.enumerated().filter { $0.offset != j }.map(\.element),
                           block.repeatCount))
        }
    }

    /// What the + adds: alternate by default, because work/rest/work/rest is the shape nearly every
    /// section wants. Tap the row's label afterwards to make it the other one — which is how you get
    /// work, work, rest.
    private func append() {
        let next = block.items.last?.phase == .work
            ? SeqInterval(.rest, DEFAULT_REST_SEC)
            : SeqInterval(.work, DEFAULT_WORK_SEC)
        withAnimation(.easeInOut(duration: 0.2)) {
            onChange(Block(block.items + [next], block.repeatCount))
        }
    }
}

private extension SeqInterval {
    func with(phase: Phase? = nil, durationSec: Int? = nil) -> SeqInterval {
        SeqInterval(phase ?? self.phase, durationSec ?? self.durationSec)
    }
}

/// How long this block is: its intervals, run its own ×N. Nothing else.
///
/// It used to be the block's *share of the workout* — multiplied by the home's rounds as well, and
/// with the last one docking the closing rest the timer won't play. Both were defensible and both
/// were wrong to put here: the number sits in the same row as the block's own "× 2", so it has to
/// describe the same thing that × 2 does. Multiplying by the outer rounds made a block's length
/// change when you touched a control somewhere else entirely, and docking the rest made two
/// identical blocks read as different lengths depending on which one was last. What the whole thing
/// costs, with the rounds and the dropped rest, is the line at the top of the screen.
func sectionSeconds(_ b: Block) -> Int {
    b.items.reduce(0) { $0 + $1.durationSec } * b.repeatCount
}

/// A total, so always M:SS — a bare "45" under a minute reads as a count, not a duration.
func clockLabel(_ sec: Int) -> String {
    let s = sec % 60
    return "\(sec / 60):" + (s < 10 ? "0\(s)" : "\(s)")
}

