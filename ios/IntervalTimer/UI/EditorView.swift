import SwiftUI
import IntervalTimerCore

/// Identity for a card that outlives its position, so reordering animates instead of redrawing.
/// Handed out by a monotonic counter, never derived from the position or the value: two structurally
/// identical groups are still two different cards.
private struct UiBlock: Identifiable {
    let id: Int
    var block: Block
}

/// Interval-row heights, deliberately kept off `RowHeightKey`. Preferences bubble all the way up, so
/// rows reporting on the shared key would merge their indices into the outer collection and steer the
/// group drag with row geometry.
private struct ItemHeightKey: PreferenceKey {
    static var defaultValue: [Int: CGFloat] { [:] }
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) {
        value.merge(nextValue()) { _, new in new }
    }
}

private func clock(_ totalSec: Int) -> String {
    let sec = totalSec % 60
    return "\(totalSec / 60):" + (sec < 10 ? "0\(sec)" : "\(sec)")
}

/// The sequence editor: groups of intervals, each with its own ×N, under one outer ×N.
struct EditorView: View {
    let initial: Preset?
    let onStart: (Preset) -> Void
    let onSave: (Preset) -> Void
    let onCancel: () -> Void

    @State private var name: String
    @State private var blocks: [UiBlock]
    @State private var repeatAll: Int
    @State private var nextId: Int
    /// A row inside one of the cards is under the finger, so the page must not scroll along with it.
    @State private var rowDragging = false
    /// Which card has its name row open for typing, by `UiBlock.id`. By identity, not position: a
    /// delete above shifts every index below it, and a positional one would move the keyboard to a
    /// different card.
    @State private var namingCard: Int?

    @StateObject private var groupDrag = ReorderState()

    init(
        initial: Preset?,
        onStart: @escaping (Preset) -> Void,
        onSave: @escaping (Preset) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.initial = initial
        self.onStart = onStart
        self.onSave = onSave
        self.onCancel = onCancel
        let seeded = groupIntervals(initial?.intervals ?? [SeqInterval(.work, 30), SeqInterval(.rest, 15)])
        _name = State(initialValue: initial?.name ?? "")
        _blocks = State(initialValue: seeded.enumerated().map { UiBlock(id: $0.offset, block: $0.element) })
        _nextId = State(initialValue: seeded.count)
        _repeatAll = State(initialValue: initial?.repeatAll ?? 1)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            HomeBackground().ignoresSafeArea()
            VStack(spacing: 0) {
            // Pinned, like the Back row on the other screens: a long sequence used to push Cancel,
            // Start and Save off the top, and they are the only ways out of this screen.
            actionBar
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 10)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header
                    ForEach(Array(blocks.enumerated()), id: \.element.id) { pair in
                        card(pair.offset, pair.element)
                            .reportRowHeight(pair.offset)
                            .reorderOffset(groupDrag, pair.offset)
                            .zIndex(groupDrag.isFloating(pair.offset) ? 1 : 0)
                    }
                    footer
                }
                .onPreferenceChange(RowHeightKey.self) { groupDrag.record($0) }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
            // A card under the finger must not also drag the page along with it.
            .scrollDisabled(groupDrag.isDragging || rowDragging)
            // Scroll the keyboard away, like the home — a name row near the bottom of a long
            // sequence is otherwise sat on by the IME with no way past it but Done.
            .scrollDismissesKeyboard(.interactively)
            // No scroll indicator anywhere in the app — see HomeView. Owner's standing preference.
            .scrollIndicators(.never)
            }
        }
    }

    // MARK: - Header

    private var actionBar: some View {
        HStack(spacing: 0) {
            PlainTextButton(text: "Cancel", action: onCancel)
            Spacer(minLength: 0)
            GlassPill(text: "Start", action: { onStart(build()) }, enabled: !blocks.isEmpty)
            Spacer().frame(width: 10)
            GlassPill(text: "Save", action: { onSave(build()) }, enabled: !blocks.isEmpty)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            nameField
            legend
                .padding(.top, 14)
            // Only worth saying once there is something to drag.
            if blocks.count > 1 || blocks.contains(where: { $0.block.items.count > 1 }) {
                HStack(spacing: 7) {
                    GripDots(alpha: 0.45, height: 13)
                    Text("Drag to reorder — groups by the header, intervals by their own grip")
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.45))
                }
                .padding(.top, 8)
            }
        }
        .padding(.bottom, 6)
    }

    /// Rounded glass, like every other control on the screen — the stock bordered field was the only
    /// square-ish thing here.
    private var nameField: some View {
        TextField("", text: $name,
                  prompt: Text("Name this sequence").foregroundStyle(.white.opacity(0.45)))
            .textFieldStyle(.plain)
            .font(.system(size: 16))
            .foregroundStyle(.white)
            .tint(.white)
            .autocorrectionDisabled()
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(glassFill, in: Capsule())
            .overlay(Capsule().strokeBorder(glassBorder(), lineWidth: 1))
    }

    /// Colour key. The rows carry no text, so this is what tells you which colour means what.
    private var legend: some View {
        HStack(spacing: 16) {
            swatch(workColor, "Work")
            swatch(restColor, "Rest")
            Spacer(minLength: 0)
        }
    }

    private func swatch(_ colour: Color, _ label: String) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 3).fill(colour).frame(width: 10, height: 10)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(.white.opacity(0.6))
        }
    }

    // MARK: - Cards

    private func card(_ i: Int, _ ui: UiBlock) -> some View {
        BlockEditorCard(
            block: ui.block,
            index: i,
            groupCount: blocks.count,
            naming: namingCard == ui.id,
            groupDrag: groupDrag,
            rowDragging: $rowDragging,
            moveGroup: moveGroup,
            onName: {
                withAnimation(.easeInOut(duration: 0.26)) {
                    namingCard = namingCard == ui.id ? nil : ui.id
                }
            },
            onNameDone: { withAnimation(.easeInOut(duration: 0.26)) { namingCard = nil } },
            onNameEdit: { namingCard = ui.id },
            onChange: { change(index: i, to: $0) },
            onAddItem: { addInterval(i) },
            onRemoveItem: { j in removeInterval(i, j) },
            onDeleteGroup: { deleteGroup(i) }
        )
    }

    // MARK: - Footer

    private var footer: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Rounds directly under the cards, then the add button — the home's order. It is sitting
            // under the whole stack that makes it read as governing the whole stack, so nothing may
            // come between them.
            if !blocks.isEmpty {
                RoundsRow(repeatAll: repeatAll, onChange: setRepeatAll)
            }
            GlassPill(text: "+  Add group", action: addGroup, wide: true)
                .padding(.top, blocks.isEmpty ? 12 : 16)
            if !blocks.isEmpty {
                totalsLine
                    .padding(.top, 12)
            }
            Spacer().frame(height: 24)
        }
    }


    /// What the whole thing adds up to, so the repeat counts have a number attached to them.
    private var totalsLine: some View {
        let once = flatten(current)
        let groups = blocks.count == 1 ? "1 group" : "\(blocks.count) groups"
        // Counted the way it will be played, without building the expanded list: the whole thing × N,
        // less the trailing rest the timer drops.
        let count = once.count * repeatAll
        let trailingRest = count > 1 && once.last?.phase == .rest ? (once.last?.durationSec ?? 0) : 0
        let played = trailingRest > 0 ? count - 1 : count
        let seconds = once.reduce(0) { $0 + $1.durationSec } * repeatAll - trailingRest
        return VStack(alignment: .leading, spacing: 4) {
            Text("\(groups) · \(played) intervals · \(clock(seconds))")
                .font(.system(size: 13))
                .foregroundStyle(.white.opacity(0.55))
            // Only reachable with the rule switched off, or on a sequence built before it was — worth
            // saying out loud rather than letting it play as one long silent gap.
            if backToBackRests(current, repeatAll) > 0 {
                Text("Two rests play back to back")
                    .font(.system(size: 13))
                    .foregroundStyle(restColor.opacity(0.85))
            }
        }
    }

    // MARK: - The rule

    private var current: [Block] { blocks.map(\.block) }

    // MARK: - Edits

    private func replace(_ i: Int, _ b: Block) -> [Block] {
        var next = current
        guard next.indices.contains(i) else { return next }
        next[i] = b
        return next
    }

    /// Every edit to a group goes through here; deletes deliberately don't. Returns whether it
    /// landed, so a drag that couldn't knows to put the row back where it came from.
    @discardableResult
    private func change(index i: Int, to next: Block) -> Bool {
        // Card callbacks capture their index; a second tap landing before the view rebuilds indexes a
        // list that already shrank. Same guard moveGroup carries, for the same reason.
        guard blocks.indices.contains(i) else { return false }
        blocks[i].block = next
        return true
    }

    private func setRepeatAll(_ next: Int) { repeatAll = next }

    private func addInterval(_ i: Int) {
        guard blocks.indices.contains(i) else { return }
        let b = blocks[i].block
        // Alternate: a rest after work, work after anything else. Not a rule, just the next row you
        // almost always want — every stepper and the phase word are still yours to change.
        let next = b.items.last?.phase == .work
            ? b.with(items: b.items + [SeqInterval(.rest, 15)])
            : b.with(items: b.items + [SeqInterval(.work, 30)])
        // Wrapped here rather than inside `change`, which is also the phase chip's, the ±5s
        // steppers' and the row reorder's path — easing those would ease every stepper tap and
        // replay a drop Reorder.swift already springs by hand. Bare, this was the one editor
        // mutation that jumped the card a row taller in a single frame, while "+ Add group" below
        // it, and the ✕ that takes the group's last row with it, both eased.
        withAnimation(.easeInOut(duration: 0.25)) { _ = change(index: i, to: next) }
    }

    private func removeInterval(_ i: Int, _ j: Int) {
        guard blocks.indices.contains(i) else { return }
        var items = blocks[i].block.items
        guard items.indices.contains(j) else { return }
        // The last interval standing takes the group with it — an empty group is nothing. This has
        // always eased; what it was easing was the wrong row. Under offset keys a middle delete read
        // as the LAST identity leaving, so the bottom row faded while the tapped one took on its
        // neighbour's phase and duration in place. Both branches are now keyed by something that
        // outlives a position — `UiBlock.id` for the cards, `SeqInterval.id` for the rows — which is
        // what puts the fade on the row you tapped.
        withAnimation(.easeInOut(duration: 0.25)) {
            if items.count == 1 {
                blocks.remove(at: i)
            } else {
                items.remove(at: j)
                blocks[i].block.items = items
            }
        }
    }

    private func addGroup() {
        withAnimation(.easeInOut(duration: 0.25)) {
            blocks.append(UiBlock(id: nextId, block: Block([SeqInterval(.work, 30), SeqInterval(.rest, 15)], 1)))
        }
        nextId += 1
    }

    private func deleteGroup(_ i: Int) {
        guard blocks.indices.contains(i) else { return }
        // Whatever was being named has gone or shifted — a stale id would leave the keyboard up over
        // a card that no longer exists.
        namingCard = nil
        withAnimation(.easeInOut(duration: 0.25)) { _ = blocks.remove(at: i) }
    }

    /// Not animated: Reorder.swift places the dropped card by hand and springs it into its slot, so
    /// animating the array rewrite too would play the same move twice.
    private func moveGroup(_ from: Int, _ to: Int) -> Bool {
        // The drag tracks list indices of its own; a group deleted by a second finger mid-drag would
        // otherwise take them out of range.
        guard blocks.indices.contains(from), blocks.indices.contains(to), from != to else { return false }
        blocks.insert(blocks.remove(at: from), at: to)
        return true
    }

    private func build() -> Preset {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return Preset(trimmed.isEmpty ? "Sequence" : trimmed, flatten(current), repeatAll: repeatAll)
    }
}

// MARK: - Group card

/// One repeat-group. The intervals sit inside a bracket so they visibly belong together, and the
/// repeat count is stated in words under it. The grip and delete live in the header, far from the ×N
/// stepper — sitting side by side, a reorder control reads as if it drove the repeat count.
private struct BlockEditorCard: View {
    let block: Block
    let index: Int
    let groupCount: Int
    /// This card's name row is open for typing. A named group keeps its row either way — this is
    /// only about the keyboard.
    let naming: Bool
    @ObservedObject var groupDrag: ReorderState
    @Binding var rowDragging: Bool
    let moveGroup: (Int, Int) -> Bool
    let onName: () -> Void
    let onNameDone: () -> Void
    /// Tapping a name that is already on the card is the other way into editing it.
    let onNameEdit: () -> Void
    /// Returns whether the edit was accepted — a reorder that isn't has to spring back.
    let onChange: (Block) -> Bool
    let onAddItem: () -> Void
    let onRemoveItem: (Int) -> Void
    let onDeleteGroup: () -> Void

    /// The rows reorder among themselves, on a state of their own: the group drag is already tracking
    /// card geometry, and one state cannot hold two sets of indices and heights at once.
    @StateObject private var rowDrag = ReorderState()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // The name, above everything else in the card — the same field the home's sections use,
            // in the same place, because this card and a home section are one object seen on two
            // screens. Naming a group here is what makes the name reachable at all on a sequence
            // built from scratch: the editor is the only way into one.
            SectionName(
                block: block,
                naming: naming,
                onChange: { _ = onChange($0) },
                onDone: onNameDone,
                onEdit: onNameEdit
            )
            .transition(.opacity.combined(with: .move(edge: .top)))
            cardHeader
            rows
            PlainTextButton(text: "+ interval", action: onAddItem, size: 13)
                .padding(.leading, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // Even padding, and 28 to match a home section: this card and a section are the same object
        // seen on two screens. The lopsided inset was making room for the bracket rail that used to
        // run down the left of the rows, and 16 made the editor read as a different kind of surface.
        .padding(EdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12))
        .liftedCard(groupDrag.isLifted(index), shape: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .padding(.vertical, 6)
        // The page owns the scroll, so it has to hear about a row drag happening down here.
        .onChange(of: rowDrag.isDragging) { _, dragging in rowDragging = dragging }
    }

    /// The home section's header, control for control: grip, the ×N, how long this group runs, and
    /// the ✕. No "Group 3" — the grip beside it already says "Reorder group 3" to VoiceOver, and the
    /// number was only ever there to be counted off against a heading nobody needed. No "Delete"
    /// either; the ✕ is the same button in one glyph.
    private var cardHeader: some View {
        let down: (Int) -> Void = { m in _ = onChange(block.with(repeatCount: max(block.repeatCount - m, 1))) }
        let up: (Int) -> Void = { m in _ = onChange(block.with(repeatCount: block.repeatCount + m)) }
        return HStack(spacing: 0) {
            DragHandle(
                index: index, label: "group \(index + 1)", state: groupDrag, commit: moveGroup,
                // Dragging is a gesture VoiceOver can't perform, so the ends of the list lose the
                // move that would run off them and keep the other.
                onMoveUp: index > 0 ? { _ = moveGroup(index, index - 1) } : nil,
                onMoveDown: index < groupCount - 1 ? { _ = moveGroup(index, index + 1) } : nil
            )
            Spacer().frame(width: 2)
            GlassCircle(glyph: "−", onStep: down, size: 36)
            // The sentence the row below used to spell out, kept where it still has to be said: out
            // loud. Named by group where there is more than one, since every card carries one of
            // these — and the value is spoken in words, because after that index a bare "3" is a
            // second numeral with nothing to say which of them is the count. On screen the ×N sits
            // inside the group it repeats, which is the whole argument for moving it here.
            Text("× \(block.repeatCount)")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 52)
                .accessibilityElement(children: .ignore)
                .stepperSemantics(groupCount > 1 ? "Repeat group \(index + 1)" : "Repeat this group",
                                  block.repeatCount == 1 ? "once" : "\(block.repeatCount) times",
                                  down: down, up: up)
            GlassCircle(glyph: "+", onStep: up, size: 36)
            // The tag, in the header's dead middle — the home's placement exactly. Just the trigger;
            // the name itself lives in the row that expands out of the card's top. Draws nothing at
            // all when section names are off, and the two flexible gaps then merge back into the one
            // this row always had.
            Spacer(minLength: 6)
            SectionTag(action: onName)
            Spacer(minLength: 6)
            // How long this group runs, the ×N included — the same readout the home puts here.
            Text(clock(block.items.reduce(0) { $0 + $1.durationSec } * block.repeatCount))
                .font(.system(size: 13))
                .foregroundStyle(.white.opacity(0.55))
            Spacer().frame(width: 6)
            CloseX(action: onDeleteGroup)
        }
    }

    /// The intervals of one group, reorderable among themselves.
    private var rows: some View {
        VStack(spacing: 0) {
            // Keyed by the interval's own id, with the offset kept only for the height preference
            // and the row drag, which genuinely are about position.
            ForEach(Array(block.items.enumerated()), id: \.element.id) { pair in
                row(pair.offset, pair.element)
                    .background(GeometryReader { geo in
                        Color.clear.preference(key: ItemHeightKey.self,
                                               value: [pair.offset: geo.size.height])
                    })
                    .reorderOffset(rowDrag, pair.offset)
                    .zIndex(rowDrag.isFloating(pair.offset) ? 1 : 0)
            }
        }
        .onPreferenceChange(ItemHeightKey.self) { rowDrag.record($0) }
    }

    private func row(_ j: Int, _ iv: SeqInterval) -> some View {
        let isWork = iv.phase == .work
        let tint = (isWork ? workColor : restColor).opacity(rowDrag.isLifted(j) ? 0.34 : 0.20)
        // `with`, not a fresh SeqInterval: the row is keyed by its id, and re-minting one mid-hold
        // would tear the stepper out from under its own repeat timer.
        let down: (Int) -> Void = { m in set(j, iv.with(durationSec: max(iv.durationSec - 5 * m, 5))) }
        let up: (Int) -> Void = { m in set(j, iv.with(durationSec: iv.durationSec + 5 * m)) }
        // Which duration this is. "Work" alone is no use where eight rows read alike, and the group
        // is named only when there is more than one — the same test the card header makes.
        let spoken = "\(isWork ? "Work" : "Rest") interval \(j + 1)"
            + (groupCount > 1 ? ", group \(index + 1)" : "")
        return HStack(spacing: 0) {
            if block.items.count > 1 {
                DragHandle(
                    index: j, label: "interval \(j + 1)", state: rowDrag, commit: moveItem,
                    onMoveUp: j > 0 ? { _ = moveItem(j, j - 1) } : nil,
                    onMoveDown: j < block.items.count - 1 ? { _ = moveItem(j, j + 1) } : nil
                )
            } else {
                // Held open anyway, so a group gaining its second interval doesn't shunt the whole
                // row sideways.
                Spacer().frame(width: 44)
            }
            // The word itself, on the tint, exactly as a home section says it — not a chip. The row
            // is already a coloured pill meaning "work" or "rest", so a second coloured pill inside
            // it saying the same word was a bubble in a bubble.
            //
            // Still its own hit box, though: it is the only part of the row that flips the phase.
            // The row used to swap on any tap, and a near-miss on a stepper flipped work to rest
            // instead of nudging the clock.
            Button { set(j, iv.with(phase: isWork ? .rest : .work)) } label: {
                Text(isWork ? "Work" : "Rest")
                    .font(.system(size: 15))
                    .foregroundStyle(.white.opacity(0.95))
                    .lineLimit(1)
                    .frame(width: 52)
                    .padding(.vertical, 6)
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            GlassCircle(glyph: "−", onStep: down, size: 44)
            Text(secLabel(iv.durationSec))
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                // Flexible rather than fixed: still a constant width whatever the label says, but it
                // gives way first when the row is squeezed on a narrow screen.
                .frame(maxWidth: .infinity)
                // On the number, not the row: the grip and the ✕ are controls of their own, and
                // merging the row would take the grip's Move up / Move down with them.
                .stepperSemantics(spoken, secLabel(iv.durationSec), down: down, up: up)
            GlassCircle(glyph: "+", onStep: up, size: 44)
            CloseX { onRemoveItem(j) }
        }
        .padding(6)
        // A pill, like the home's rows — 12pt corners were the other half of what made this screen
        // read as squarer than the one it mirrors.
        .background(tint, in: Capsule())
        .padding(.vertical, 4)
    }

    private func set(_ j: Int, _ iv: SeqInterval) {
        var items = block.items
        guard items.indices.contains(j) else { return }
        items[j] = iv
        _ = onChange(block.with(items: items))
    }

    /// The bounds check covers the one index that can still go stale: a second finger deleting a row
    /// mid-drag.
    private func moveItem(_ from: Int, _ to: Int) -> Bool {
        var items = block.items
        guard items.indices.contains(from), items.indices.contains(to), from != to else { return false }
        items.insert(items.remove(at: from), at: to)
        return onChange(block.with(items: items))
    }
}

