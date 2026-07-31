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

private let DOUBLE_REST_NOTICE = "Two rests would land in a row — allow it in Settings"

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
    /// Why an edit was refused.
    @State private var notice: String?
    /// A row inside one of the cards is under the finger, so the page must not scroll along with it.
    @State private var rowDragging = false

    @StateObject private var groupDrag = ReorderState()
    @ObservedObject private var settings = Settings.shared

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
                        card(pair.offset, pair.element.block)
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
            }

            if let notice {
                NoticePill(text: notice)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                    .allowsHitTesting(false)
                    // Keyed on the message, which is what lets a repeat of the same one run out the
                    // clock it already started rather than restarting it (see `flash`).
                    .task(id: notice) {
                        try? await Task.sleep(for: .seconds(2.6))
                        self.notice = nil
                    }
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

    private func card(_ i: Int, _ block: Block) -> some View {
        BlockEditorCard(
            block: block,
            index: i,
            groupCount: blocks.count,
            groupDrag: groupDrag,
            rowDragging: $rowDragging,
            canRest: { j in canRest(i, j) },
            moveGroup: moveGroup,
            onChange: { change(index: i, to: $0) },
            onAddItem: { addInterval(i) },
            onRemoveItem: { j in removeInterval(i, j) },
            onDeleteGroup: { deleteGroup(i) }
        )
    }

    // MARK: - Footer

    private var footer: some View {
        VStack(alignment: .leading, spacing: 0) {
            GlassPill(text: "+  Add group", action: addGroup, wide: true)
                .padding(.top, 12)
            if !blocks.isEmpty {
                RepeatAllCard(repeatAll: repeatAll, onChange: setRepeatAll)
                    .padding(.top, 16)
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

    /// The no-back-to-back-rests rule, asked of the sequence as it will actually play.
    ///
    /// Compared against what's already there rather than against zero: a sequence that already has a
    /// double rest (built with the rule off, or turned on afterwards) must still be editable, so only
    /// an edit that *adds* one is refused.
    private func violates(_ candidate: [Block], _ repeats: Int) -> Bool {
        settings.noDoubleRest && backToBackRests(candidate, repeats) > backToBackRests(current, repeatAll)
    }

    private func allow(_ candidate: [Block], _ repeats: Int) -> Bool {
        guard violates(candidate, repeats) else { return true }
        flash(DOUBLE_REST_NOTICE)
        return false
    }

    private func flash(_ message: String) {
        // A refused drag asks again on every frame it is held there. Re-arming the timer each time
        // would pin the pill open and rebuild the whole editor at 60fps, so a message already on
        // screen is left to run out its own clock.
        guard notice != message else { return }
        notice = message
    }

    /// Switching a work interval to rest is the only edit the rule can refuse, so it's the only one
    /// the cards need to grey out.
    private func canRest(_ i: Int, _ j: Int) -> Bool {
        guard blocks.indices.contains(i) else { return true }
        let b = blocks[i].block
        guard b.items.indices.contains(j) else { return true }
        var items = b.items
        items[j] = SeqInterval(.rest, items[j].durationSec)
        return !violates(replace(i, Block(items, b.repeatCount)), repeatAll)
    }

    // MARK: - Edits

    private func replace(_ i: Int, _ b: Block) -> [Block] {
        var next = current
        guard next.indices.contains(i) else { return next }
        next[i] = b
        return next
    }

    /// Every rule-checked edit to a group goes through here; deletes deliberately don't. Returns
    /// whether it landed, so a refused drag knows to put the row back where it came from.
    @discardableResult
    private func change(index i: Int, to next: Block) -> Bool {
        // Card callbacks capture their index; a second tap landing before the view rebuilds indexes a
        // list that already shrank. Same guard moveGroup carries, for the same reason.
        guard blocks.indices.contains(i), allow(replace(i, next), repeatAll) else { return false }
        blocks[i].block = next
        return true
    }

    private func setRepeatAll(_ next: Int) {
        if allow(current, next) { repeatAll = next }
    }

    private func addInterval(_ i: Int) {
        guard blocks.indices.contains(i) else { return }
        let b = blocks[i].block
        let rest = Block(b.items + [SeqInterval(.rest, 15)], b.repeatCount)
        // Alternate by default; where a rest would double up, work is the sane fallback.
        let next = b.items.last?.phase == .work && !violates(replace(i, rest), repeatAll)
            ? rest
            : Block(b.items + [SeqInterval(.work, 30)], b.repeatCount)
        change(index: i, to: next)
    }

    private func removeInterval(_ i: Int, _ j: Int) {
        guard blocks.indices.contains(i) else { return }
        var items = blocks[i].block.items
        guard items.indices.contains(j) else { return }
        withAnimation(.easeInOut(duration: 0.25)) {
            // The last interval standing takes the group with it — an empty group is nothing.
            if items.count == 1 {
                blocks.remove(at: i)
            } else {
                items.remove(at: j)
                blocks[i].block.items = items
            }
        }
    }

    private func addGroup() {
        let pair = Block([SeqInterval(.work, 30), SeqInterval(.rest, 15)], 1)
        let next = violates(current + [pair], repeatAll) ? Block([SeqInterval(.work, 30)], 1) : pair
        guard allow(current + [next], repeatAll) else { return }
        withAnimation(.easeInOut(duration: 0.25)) {
            blocks.append(UiBlock(id: nextId, block: next))
        }
        nextId += 1
    }

    private func deleteGroup(_ i: Int) {
        guard blocks.indices.contains(i) else { return }
        withAnimation(.easeInOut(duration: 0.25)) { _ = blocks.remove(at: i) }
    }

    /// Not animated: Reorder.swift places the dropped card by hand and springs it into its slot, so
    /// animating the array rewrite too would play the same move twice.
    private func moveGroup(_ from: Int, _ to: Int) -> Bool {
        // The drag tracks list indices of its own; a group deleted by a second finger mid-drag would
        // otherwise take them out of range.
        guard blocks.indices.contains(from), blocks.indices.contains(to), from != to else { return false }
        var candidate = current
        candidate.insert(candidate.remove(at: from), at: to)
        guard allow(candidate, repeatAll) else { return false }
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
    @ObservedObject var groupDrag: ReorderState
    @Binding var rowDragging: Bool
    let canRest: (Int) -> Bool
    let moveGroup: (Int, Int) -> Bool
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
            cardHeader
            bracket
            repeatRow
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(EdgeInsets(top: 8, leading: 4, bottom: 14, trailing: 14))
        .liftedCard(groupDrag.isLifted(index), shape: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .padding(.vertical, 6)
        // The page owns the scroll, so it has to hear about a row drag happening down here.
        .onChange(of: rowDrag.isDragging) { _, dragging in rowDragging = dragging }
    }

    private var cardHeader: some View {
        HStack(spacing: 0) {
            DragHandle(
                index: index, label: "group \(index + 1)", state: groupDrag, commit: moveGroup,
                // Dragging is a gesture VoiceOver can't perform, so the ends of the list lose the
                // move that would run off them and keep the other.
                onMoveUp: index > 0 ? { _ = moveGroup(index, index - 1) } : nil,
                onMoveDown: index < groupCount - 1 ? { _ = moveGroup(index, index + 1) } : nil
            )
            Text(groupCount > 1 ? "Group \(index + 1)" : "Group")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(.white.opacity(0.45))
            Spacer(minLength: 0)
            PlainTextButton(text: "Delete", action: onDeleteGroup, size: 12)
        }
    }

    /// The bracket: a rail down the left edge tying every interval in the group together.
    private var bracket: some View {
        HStack(alignment: .top, spacing: 8) {
            RoundedRectangle(cornerRadius: 2)
                .fill(.white.opacity(0.28))
                .frame(width: 3)
            VStack(alignment: .leading, spacing: 0) {
                rows
                PlainTextButton(text: "+ interval", action: onAddItem, size: 13)
            }
        }
        .padding(.leading, 10)
    }

    /// The intervals of one group, reorderable among themselves.
    private var rows: some View {
        VStack(spacing: 0) {
            ForEach(Array(block.items.enumerated()), id: \.offset) { pair in
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
            // Its own control with its own hit box. The row used to swap phase on any tap, which
            // meant a near-miss on a stepper flipped work to rest instead of nudging the clock.
            PhaseChip(
                phase: iv.phase,
                // Greyed, but still tappable: the tap is what surfaces the reason.
                dimmed: isWork && !canRest(j),
                action: { set(j, SeqInterval(isWork ? .rest : .work, iv.durationSec)) }
            )
            Spacer().frame(width: 4)
            GlassCircle(glyph: "−",
                        onStep: { m in set(j, SeqInterval(iv.phase, max(iv.durationSec - 5 * m, 5))) },
                        size: 44)
            Text(secLabel(iv.durationSec))
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                // Flexible rather than fixed: still a constant width whatever the label says, but it
                // gives way first when the row is squeezed on a narrow screen.
                .frame(maxWidth: .infinity)
            GlassCircle(glyph: "+",
                        onStep: { m in set(j, SeqInterval(iv.phase, iv.durationSec + 5 * m)) },
                        size: 44)
            CloseX { onRemoveItem(j) }
        }
        .padding(6)
        .background(tint, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.vertical, 4)
    }

    /// Stated in words so there's no guessing what the number applies to.
    private var repeatRow: some View {
        HStack(spacing: 0) {
            Text("Repeat this group")
                .font(.system(size: 14))
                .foregroundStyle(.white.opacity(0.7))
            Spacer(minLength: 12)
            GlassCircle(glyph: "−",
                        onStep: { m in _ = onChange(Block(block.items, max(block.repeatCount - m, 1))) },
                        size: 44)
            Text("× \(block.repeatCount)")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 52)
            GlassCircle(glyph: "+",
                        onStep: { m in _ = onChange(Block(block.items, block.repeatCount + m)) },
                        size: 44)
        }
        .padding(.top, 10)
        .padding(.leading, 10)
    }

    private func set(_ j: Int, _ iv: SeqInterval) {
        var items = block.items
        guard items.indices.contains(j) else { return }
        items[j] = iv
        _ = onChange(Block(items, block.repeatCount))
    }

    /// The bounds check covers the one index that can still go stale: a second finger deleting a row
    /// mid-drag.
    private func moveItem(_ from: Int, _ to: Int) -> Bool {
        var items = block.items
        guard items.indices.contains(from), items.indices.contains(to), from != to else { return false }
        items.insert(items.remove(at: from), at: to)
        return onChange(Block(items, block.repeatCount))
    }
}

// MARK: - Phase chip

/// Work / rest switch for one interval. `dimmed` means the rule won't allow a rest here.
private struct PhaseChip: View {
    let phase: Phase
    let dimmed: Bool
    let action: () -> Void

    private var accent: Color { phase == .work ? workColor : restColor }

    var body: some View {
        Button(action: action) {
            Text(phase == .work ? "WORK" : "REST")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(.white.opacity(dimmed ? 0.45 : 0.95))
                .lineLimit(1)
                .fixedSize()
                .frame(width: 48)
                .padding(.vertical, 7)
                .background(accent.opacity(dimmed ? 0.14 : 0.30), in: Capsule())
                .overlay(Capsule().strokeBorder(accent.opacity(dimmed ? 0.22 : 0.55), lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
