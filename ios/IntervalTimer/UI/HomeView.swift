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
    /// Which section's name field is open for typing, by row id — nil for none. Hoisted rather than
    /// kept per card because only one can ever be open: the field takes the caret when it opens, and
    /// there is one caret. By id, not index, so a drag or a delete can't leave it pointing at the
    /// wrong card. The tag opens and closes this row outright — closing keeps the name, it just puts
    /// the field away; only deleting the section takes the name with it.
    @State private var namingRow: Int?

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

    /// The sections as they are saved and run — with the lone-section case's name stripped.
    ///
    /// A name only means anything when there is another section to tell it apart from, which is
    /// exactly why the tag lives in the header and the header only arrives with `grouped`. Delete
    /// down to one and the name would otherwise survive, ride onto the timer, and have nowhere on
    /// screen left to edit it from. Normalised here rather than at each delete because it is the one
    /// place both the save and the workout read, so neither can disagree with the other.
    private var blocks: [Block] {
        grouped ? rows.map(\.block) : rows.map { $0.block.with(name: "") }
    }

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
        .onChange(of: rows) { _, _ in
            Settings.shared.updateHome(blocks)
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

                    // Everything below the header, and the ONLY thing that centres. The minHeight
                    // used to sit on the whole stack, header included, so a home short enough not
                    // to scroll — one plain section — centred the header along with the rest and
                    // left it floating in the middle of the page instead of at the top of it.
                    // The header keeps its 56 at the top; the page centres in what is left.
                    //
                    // 112 = that 56 plus the bottom padding below, so short content still fills the
                    // scroll view exactly and long content grows past it and scrolls, taking the
                    // header up with it exactly as before.
                    VStack(spacing: 0) {
                        if !solo { saveControl }
                        summaryLine
                        // The group box: one rounded frame around the ×N and every section under
                        // it, so "repeat all of this" is something you can see rather than read.
                        VStack(spacing: 0) {
                            ForEach(Array(rows.enumerated()), id: \.element.id) { pair in
                                section(pair.offset, pair.element)
                                    // The copy grows downward out of the card above it, and its
                                    // spring is held back a beat so the box lands first: box
                                    // closes, then it pushes a duplicate out below. Anchored to the
                                    // top because that is the edge it is emerging from.
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
                    .frame(maxWidth: .infinity, minHeight: max(0, geo.size.height - 112))
                }
                .onPreferenceChange(RowHeightKey.self) { reorder.record($0) }
                .padding(.horizontal, 24)
                // No top padding: the header row is the list's first item now and holds that space
                // open itself.
                .padding(.bottom, 56)
                .frame(maxWidth: .infinity)
            }
            // A page that fits has nothing to scroll, so it should not answer a drag at all — the
            // plain home rubber-banded and read as a page with more below it. basedOnSize bounces
            // only when the content actually overflows, which is exactly the question being asked;
            // the alternative is measuring the content ourselves, and §37 is what that costs.
            .scrollBounceBehavior(.basedOnSize)
            // A card under the finger must not also drag the page along with it.
            .scrollDisabled(reorder.isDragging)
            // Scroll the keyboard away, which is the gesture iOS users reach for and the only way
            // off this field other than the tag itself and Return. It resigns first responder, so
            // the caret goes with it — see SectionName, which closes its row on losing focus.
            .scrollDismissesKeyboard(.interactively)
            // No scroll indicator, here or on any other screen: a bar that flashes up to say how far
            // down a short list you are answers a question none of these screens leave open. Owner's
            // standing preference — don't add it back.
            .scrollIndicators(.never)
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
        let down: (Int) -> Void = { m in setHomeRounds(homeRounds - m) }
        let up: (Int) -> Void = { m in setHomeRounds(homeRounds + m) }
        return HStack(spacing: 0) {
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
            GlassCircle(glyph: "−", onStep: down, size: grouped ? 50 : 54)
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
            GlassCircle(glyph: "+", onStep: up, size: grouped ? 50 : 54)
            Spacer().frame(maxWidth: grouped ? 260 : 0)
        }
        .padding(.top, grouped ? 4 : 16)
        .padding(.bottom, grouped ? 2 : 32)
        // One element rather than "Rounds" followed by two nameless circles. All the merge costs is
        // the number's double-tap reset, and that is a gesture VoiceOver cannot make in the first
        // place — it comes back below as a rotor action, which is the only form it can take.
        .accessibilityElement(children: .ignore)
        .stepperSemantics("Rounds", "\(homeRounds)", down: down, up: up)
        .accessibilityAction(named: "Reset") { setHomeRounds(DEFAULT_ROUNDS) }
    }

    /// What GO will run, at the top where you read it before you start rather than at the bottom
    /// where it sat on the button.
    ///
    /// Sets, not "rounds": the control below already owns that word and means the other thing by it
    /// — this is the count the timer will walk you through, the one the pips draw.
    private var summaryLine: some View {
        let sets = homeSets(blocks, repeatAll: effectiveRepeatAll)
        return Text("\(sets) \(sets == 1 ? "set" : "sets")  ·  "
                    + clockLabel(homeSeconds(blocks, repeatAll: effectiveRepeatAll)))
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
        let fewer: (Int) -> Void = { m in change(i) { $0.repeatCount = max($0.repeatCount - m, 1) } }
        let more: (Int) -> Void = { m in change(i) { $0.repeatCount += m } }
        return VStack(spacing: solo ? 16 : 6) {
            // The name, above everything else in the card — tag pressed, this expands out of the
            // top on the same move the header lid rides, and the card grows to make room exactly
            // the way it does for a new interval. Pressed again it folds away with the name intact.
            // Only on a card: with one section there is nothing to tell apart.
            if grouped {
                SectionName(
                    block: b,
                    naming: namingRow == row.id,
                    onChange: { next in change(i) { $0 = next } },
                    onDone: { withAnimation(.easeInOut(duration: 0.26)) { namingRow = nil } }
                )
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
            // The header carries the drag grip, the per-section ×N, the section's own running time
            // and the ✕ — every one of which is meaningless when there is only one section. It
            // arrives with the second one.
            if grouped {
                HStack(spacing: 0) {
                    DragHandle(
                        index: i, label: "section \(i + 1)", state: reorder,
                        commit: move, onMoveUp: up, onMoveDown: down
                    )
                    GlassCircle(glyph: "−", onStep: fewer, size: 36)
                    Text("× \(b.repeatCount)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 52)
                        // On the number, not the header row: the grip and the ✕ share this row and
                        // merging it would swallow the grip's Move up / Move down. Spoken in words
                        // because the label ends in an index — "section 2, 3" is two numerals in a
                        // row where only one of them is a count.
                        .stepperSemantics("Repeat section \(i + 1)",
                                          b.repeatCount == 1 ? "once" : "\(b.repeatCount) times",
                                          down: fewer, up: more)
                    GlassCircle(glyph: "+", onStep: more, size: 36)
                    Spacer(minLength: 6)
                    // The tag, in the header's dead middle. Just the trigger — the name itself
                    // lives in the row that expands out of the card's top. Draws nothing at all
                    // when section names are off, and the two flexible gaps then merge back into
                    // the one this row always had.
                    SectionTag {
                        withAnimation(.easeInOut(duration: 0.26)) {
                            namingRow = namingRow == row.id ? nil : row.id
                        }
                    }
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
                            homePreset(blocks, repeatAll: effectiveRepeatAll)
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
        // Ids are minted as max + 1, so deleting the last row frees its id for the next one added —
        // and a stale namingRow would then open that fresh section with the keyboard already up.
        namingRow = nil
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
            // The shape copies; the name doesn't — it names the section it's on, not the next one.
            // Not only taste: a copied name on an identical shape makes the two sections compare
            // equal, and groupIntervals then folds the pair into one card ×2 on the next launch.
            if rows.count == 1 {
                setRepeatAll(last.block.repeatCount)
                var one = last.block
                one.repeatCount = 1
                rows[0].block = one
                rows.append(HomeRow(id: nextId, block: one.with(name: "")))
            } else {
                rows.append(HomeRow(id: nextId, block: last.block.with(name: "")))
            }
        }
    }

    private func go() {
        // The same builder the total above it is measured from, so the number and the button can
        // never describe different workouts.
        onGo(homeWorkout(blocks,
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

// MARK: - Section name

/// What this section is *of* — "Splits", "Pistol squats". Sits above everything else in the card and
/// rides onto the timer while the section plays.
///
/// Its own view, and it observes Settings itself rather than letting HomeView do it: `Settings.home`
/// republishes on every edit (the `.onChange(of: rows)` that saves the page), so an @ObservedObject
/// up there would rebuild the whole home a second time per stepper frame. Same reason `SectionTag`
/// below is a view rather than an `if` in the header.
///
/// Not private: the sequence editor's cards are the same object on another screen and name their
/// sections with this exact field. One field, so the cap, the caret and the Done key can't drift.
struct SectionName: View {
    let block: Block
    /// Whether the row is open at all. The tag toggles it, and closing is only ever a fold — the
    /// name is left exactly where it was, for the section to wear on the timer and for the next tag
    /// press to find. Deleting the section is the one thing that takes a name away.
    let naming: Bool
    let onChange: (Block) -> Void
    let onDone: () -> Void

    @FocusState private var focused: Bool
    /// What the text view actually holds. The field is bound to THIS, not straight to `block.name`,
    /// because the 40-character cap has to be applied a beat late. Handing a UIKit text view a value
    /// different from the one it just reported desyncs the two: measured, typing past the cap emptied
    /// the field and started collecting again from the next keystroke — "Pistol squats weighted deep
    /// pause at the bottom" came out as "bottom". Clamping in `.onChange` lets the text view settle
    /// on its own value first, and the correction lands as an ordinary update.
    @State private var draft = ""

    var body: some View {
        if naming {
            TextField("", text: $draft,
                      prompt: Text("Name").foregroundStyle(.white.opacity(0.35)),
                      // Wraps rather than scrolling sideways. 3 rows, not 2, because only CJK ever
                      // needs the third and clipping it there is exactly what the 40-character cap
                      // exists to avoid.
                      axis: .vertical)
                .lineLimit(1...3)
                .textFieldStyle(.plain)
                .font(.system(size: 15))
                .foregroundStyle(.white)
                .tint(.white)
                .focused($focused)
                .padding(.horizontal, 8)
                .padding(.top, 2)
                .padding(.bottom, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
                // The caret is a function of focus, and focus is what this view is handed. Opening
                // takes it; closing — the tag pressed again, or another section's tag — gives it up,
                // which is what puts the keyboard away.
                //
                // `.task(id:)`, NOT `.onChange` — this is the Kotlin's `LaunchedEffect(focusTick)`
                // arrived at from the other end. Assigning @FocusState inside onChange lands in the
                // middle of the update that changed `naming` and the request is dropped: measured on
                // an already-mounted field, the row opened with no keyboard and no caret. A task
                // runs after that update settles, where it takes. It also covers first mount, which
                // is the one case onChange never sees and onAppear was quietly carrying.
                .task(id: naming) { focused = naming }
                // And the other direction: the keyboard scrolled away (see
                // `.scrollDismissesKeyboard`) resigns first responder without anything up here
                // knowing, which would leave a caret blinking in a field that can no longer be
                // typed into. Losing focus closes the row.
                .onChange(of: focused) { _, has in if !has && naming { onDone() } }
                // The model is the source of truth; the draft mirrors it. Seeding on appear is what
                // fills an already-named card, and following it afterwards covers a change this
                // field didn't make — the ✕ on the section above shifting a different block into
                // this view's identity slot.
                .onAppear { draft = block.name }
                .onChange(of: block.name) { _, name in if draft != name { draft = name } }
                .onChange(of: draft) { _, raw in commit(raw) }
        }
    }

    private func commit(_ raw: String) {
        // 40. Picked to be the largest cap that CANNOT clip in any script, which retires the
        // character-vs-row mismatch outright rather than just making it rarer. Measured on the
        // Android twin at the same 15pt in this field, not estimated:
        //
        //   Latin  ~26 per row → 40 fills 2 rows
        //   CJK      15 per row → 40 fills 3 rows exactly (15 + 15 + 10)
        //
        // So CJK is the binding constraint and the ceiling is 45; 40 keeps five characters of
        // headroom. A bigger cap buys only names nobody writes: a section is one exercise, not a
        // sentence.
        var name = String(raw.replacingOccurrences(of: "\n", with: " ").prefix(40))
        // A vertical-axis field has no Done key of its own and never fires `.onSubmit`, so Return
        // arriving as a newline IS the Done press. The name never carries the break: wrapping is the
        // layout's call, and a trailing space would shift the centred name on the timer off true.
        if raw.contains("\n") {
            while name.hasSuffix(" ") { name.removeLast() }
            onDone()
        }
        if draft != name { draft = name }
        if block.name != name { onChange(block.with(name: name)) }
    }
}

/// The button that opens the name field: a luggage tag, drawn rather than typed — same as `CloseX`
/// and `GripDots`, and for the same reason, that no font is guaranteed to carry a glyph.
///
/// The action is "name this section", not "edit", and a tag says that — unlike every pencil in every
/// icon set it points right on its own, with no mirroring against convention.
///
/// Not private, for the reason given on `SectionName` above.
struct SectionTag: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            // Five points on Lucide's 24-unit grid: flat top and left edges meeting the squared
            // corner that holds the punch hole, then the long diagonals out to the point.
            tag
                .frame(width: 18, height: 18)
                .frame(width: 36, height: 36)   // the same circle as the steppers beside it
                .background(glassFill, in: Circle())
                .overlay(Circle().strokeBorder(glassBorder(), lineWidth: 1))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Name this section")
    }

    private var tag: some View {
        Canvas { ctx, size in
            let u = min(size.width, size.height) / 24
            let ink = Color.white.opacity(0.85)
            let outline = roundedPolygon([
                CGPoint(x: 12 * u, y: 2 * u),
                CGPoint(x: 2 * u, y: 2 * u),
                CGPoint(x: 2 * u, y: 12 * u),
                CGPoint(x: 13 * u, y: 23 * u),
                CGPoint(x: 23 * u, y: 13 * u),
            ], radius: 2.4 * u)
            ctx.stroke(outline, with: .color(ink),
                       style: StrokeStyle(lineWidth: 2 * u, lineCap: .round, lineJoin: .round))
            // The punch hole, filled: an outlined ring this small is two concentric circles a pixel
            // apart, which just reads as grey.
            let r = 1.4 * u
            ctx.fill(Path(ellipseIn: CGRect(x: 7.6 * u - r, y: 7.6 * u - r, width: r * 2, height: r * 2)),
                     with: .color(ink))
        }
    }
}

/// A closed polygon with every corner rounded — Compose's `PathEffect.cornerPathEffect`, which
/// SwiftUI has no counterpart for, written out.
///
/// Each corner becomes a quadratic curve whose control point is the corner itself and whose ends sit
/// `radius` back along the two edges meeting there. A round line JOIN is not the same thing and was
/// tried first on the Android side: it softens by half the stroke width, which came out visibly
/// sharper than the reference icon.
private func roundedPolygon(_ pts: [CGPoint], radius: CGFloat) -> Path {
    var path = Path()
    let n = pts.count
    guard n > 2 else { return path }
    /// `radius` back from `a` towards `b`, capped at half the edge so two close corners can't
    /// overshoot each other and fold the edge between them inside out.
    func along(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
        let dx = b.x - a.x, dy = b.y - a.y
        let len = max((dx * dx + dy * dy).squareRoot(), 0.0001)
        let t = min(radius, len / 2) / len
        return CGPoint(x: a.x + dx * t, y: a.y + dy * t)
    }
    for i in 0..<n {
        let corner = pts[i]
        let start = along(corner, pts[(i + n - 1) % n])
        let end = along(corner, pts[(i + 1) % n])
        if i == 0 { path.move(to: start) } else { path.addLine(to: start) }
        path.addQuadCurve(to: end, control: corner)
    }
    path.closeSubpath()
    return path
}

// MARK: - Stepper row

/// How long one pass of the gradient takes to drift across a row.
private let DRIFT_SECONDS: Double = 9

/// Label, then − / value / + . Double-tapping the number puts it back to the stock value — the way
/// out of a hold that overshot.
struct StepperRow: View {
    let label: String
    /// What VoiceOver calls this row. The visible label is a bare "Work", which is all you need when
    /// you can see which row you are on and nothing at all when three of them read alike, so the
    /// caller says which one it is.
    let spokenLabel: String
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
                // On the number, not the row: the label is a tap target of its own (work↔rest) and
                // the ✕ is a button, and merging the row would take both with it.
                .stepperSemantics(spokenLabel, value, down: onMinus, up: onPlus)
                // VoiceOver cannot perform the double-tap above, so the way out of a hold that
                // overshot is offered as a rotor action instead.
                .accessibilityAction(named: "Reset", onReset)
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
    ///
    /// Reduce Motion pauses that clock rather than dropping the wash: paused still renders, so the
    /// row keeps its full phase colour and only the dips stop sliding across it.
    @ViewBuilder private var wash: some View {
        if let tint, !settings.minimalBg {
            GeometryReader { geo in
                // Capped at 30Hz: every tinted row runs its own clock, and at display refresh a
                // grouped home rebuilt a seven-stop gradient per row per frame — 120 a second each
                // on ProMotion — to move the wash 0.5pt. The drift covers a span in 9s (~55pt/s),
                // so a 33ms step is under 2pt of soft gradient with no edge in it to judder.
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { ctx in
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
            // Keyed by the interval's own id, not by position: `remove` below eases, and under
            // positional keys a middle delete presented as "the last identity left" — the bottom
            // row faded while the tapped one took on its neighbour's label and duration. The offset
            // stays for the spoken label, which really is a position.
            ForEach(Array(block.items.enumerated()), id: \.element.id) { pair in
                let j = pair.offset
                let iv = pair.element
                let isWork = iv.phase == .work
                let phase = isWork ? "Work" : "Rest"
                StepperRow(
                    label: phase,
                    spokenLabel: "\(phase) interval \(j + 1)",
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
        onChange(block.with(items: items))
    }

    private func remove(_ j: Int) {
        guard block.items.count > 1, block.items.indices.contains(j) else { return }
        // Animated, matching append(): on the last section, three intervals down to a plain
        // work/rest pair flips `solo` — the whole carded→plain transition rides this one mutation.
        // That means the card chrome and wash, the row spacing, every ✕, the footer's "+ Add
        // intervals". Leaving the mutation bare snapped all of that in one frame, and left
        // "+ interval" fading away the box that the ✕ beside it slammed back.
        withAnimation(.easeInOut(duration: 0.2)) {
            onChange(block.with(items: block.items.enumerated().filter { $0.offset != j }.map(\.element)))
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
            onChange(block.with(items: block.items + [next]))
        }
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

