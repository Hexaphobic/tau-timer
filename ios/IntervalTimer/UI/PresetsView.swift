import SwiftUI
import IntervalTimerCore

/// Where a row came from and where in that list, which is the row's identity for `ForEach`.
///
/// Built-ins and saved presets are two separate arrays drawn into one stack, so the position on its
/// own collides between them — saved[0] would be the same row as builtin[0].
///
/// Deliberately positional rather than derived from the preset: it survives a delete, so removing
/// one preset doesn't hand every row below it a new identity and slam all of them shut. What that
/// costs is handled in `PresetRow`.
private enum RowKey: Hashable {
    case builtin(Int)
    case saved(Int)
}

private struct Row: Identifiable {
    let id: RowKey
    let preset: Preset
}

private func clock(_ totalSec: Int) -> String {
    let sec = totalSec % 60
    return "\(totalSec / 60):" + (sec < 10 ? "0\(sec)" : "\(sec)")
}

struct PresetsView: View {
    let onBack: () -> Void
    let onStart: (Preset) -> Void
    let onNew: () -> Void
    let onEdit: (Int) -> Void
    let onEditBuiltin: (Preset) -> Void

    @ObservedObject private var store = PresetStore.shared
    @ObservedObject private var settings = Settings.shared

    var body: some View {
        // The pill floats over the scroll rather than sitting above it, so the list passes
        // underneath instead of being cut off at a hard edge.
        ZStack(alignment: .topLeading) {
            HomeBackground().ignoresSafeArea()
            VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(rows) { row in
                        PresetRow(
                            preset: row.preset,
                            onStart: { onStart(row.preset) },
                            onEdit: editAction(row),
                            onDelete: { delete(row.id, row.preset.name) }
                        )
                    }

                    GlassPill(text: "+  New sequence", action: onNew, wide: true)
                        .padding(.top, 16)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
                // Clear of the pill at rest; scrolled, the list simply travels under it.
                .padding(.top, 60)
            }
            // No scroll indicator anywhere in the app — see HomeView. Owner's standing preference.
            .scrollIndicators(.never)
            }
            BackPill(onBack: onBack).padding(.leading, 12).padding(.top, 4)
        }
    }

    private var rows: [Row] {
        let builtins = BUILTIN_PRESETS
            .filter { !settings.hiddenBuiltins.contains($0.name) }
            .enumerated()
            .map { Row(id: .builtin($0.offset), preset: $0.element) }
        let saved = store.saved.enumerated().map { Row(id: .saved($0.offset), preset: $0.element) }
        return builtins + saved
    }

    /// Editable like anything else — a built-in edit saves the copy and hides the original, so
    /// "pre-made" is a starting point, not a locked case.
    private func editAction(_ row: Row) -> (() -> Void)? {
        switch row.id {
        case .saved(let index): return { onEdit(index) }
        case .builtin: return { onEditBuiltin(row.preset) }
        }
    }

    // Anything in the list can be deleted. Built-ins are compiled in rather than stored, so they're
    // hidden by name instead of removed; saved ones are deleted outright.
    private func delete(_ key: RowKey, _ name: String) {
        switch key {
        case .builtin: settings.hideBuiltin(name)
        case .saved(let index): store.deleteAt(index)
        }
    }
}

/// One-tap start, straight off the collapsed row.
///
/// Its own hit box, deliberately larger than it looks like it needs to be: everything around it
/// expands the row, so the two actions must not share an edge. A near-miss should land on the
/// harmless one, which is why the row — the big target — is the one that merely opens, and this —
/// the small deliberate target — is the one that commits.
///
/// No confirmation behind it. The prepare countdown is the confirmation: it names the workout it is
/// about to run while you still have five seconds, and backing out of a wrong one is pause → End,
/// which lands right back on this list with nothing changed.
private struct PlayCircle: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "play.fill")
                .font(.system(size: 20))
                .foregroundStyle(.white)
                // A triangle's visual mass sits left of its bounding box, so dead centre reads as
                // nudged left. Every play button ever drawn carries this offset.
                .offset(x: 2)
                .frame(width: 52, height: 52)
                .background(glassFill, in: Circle())
                .overlay(Circle().strokeBorder(glassBorder(), lineWidth: 1))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct PresetRow: View {
    let preset: Preset
    let onStart: () -> Void
    let onEdit: (() -> Void)?
    let onDelete: () -> Void

    @State private var expanded = false
    @State private var armed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // No expand indicator. The ▸ that used to sit here was a play triangle in all but name,
            // and beside a real one it read as two different actions drawn as the same shape — but a
            // chevron in its place was still a second, quieter control competing with the one that
            // matters. Tap-to-open is implied by the card, the way the home's sections imply it.
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(preset.name)
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(.white)
                    Text(summary)
                        .font(.system(size: 14))
                        .foregroundStyle(.white.opacity(0.55))
                }
                Spacer(minLength: 12)
                PlayCircle(label: "Start \(preset.name)", action: onStart)
            }

            if expanded { details }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(glassFill, in: RoundedRectangle(cornerRadius: 16))
        .contentShape(RoundedRectangle(cornerRadius: 16))
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.18)) { expanded.toggle() }
            // Collapsing disarms the delete, so it can never sit armed unseen.
            armed = false
        }
        .padding(.vertical, 6)
        // Identity, not slot. @State here is positional and `RowKey` keeps it that way, so deleting
        // a preset shifts the row below into its slot — and it used to arrive carrying the armed
        // "Delete?" state, one tap from deleting the wrong preset. Resetting when the preset
        // occupying the slot changes is what breaks that inheritance.
        //
        // Keying on the value is not enough on its own: two structurally equal saved presets are
        // the same value, so that shift is invisible from here. The other half of the trap is
        // covered where the delete commits, below.
        .onChange(of: preset) { _, _ in
            expanded = false
            armed = false
        }
    }

    private var summary: String {
        let seq = preset.playbackIntervals()
        return "\(seq.count) intervals · \(clock(seq.reduce(0) { $0 + $1.durationSec }))"
    }

    @ViewBuilder private var details: some View {
        // One tinted band per interval, full width — the same treatment as the editor, so a preset
        // looks like the thing you'd edit.
        //
        // Grouped through `groupIntervals`, which is exactly what tapping Edit does to the same list:
        // a home saved as a preset arrives already multiplied out, so a section of eight rounds used
        // to read as sixteen identical bands with nothing to say they were one thing. Grouping folds
        // them back to the pair plus a × N — and gives the section's name somewhere to sit.
        //
        // The sequence as written, not as expanded — the ×N line below says how often the whole runs.
        VStack(spacing: 0) {
            ForEach(Array(groupIntervals(preset.intervals).enumerated()), id: \.offset) { pair in
                let group = pair.element
                let named = !group.name.isEmpty
                // Every group in a bubble of its own, the editor's card in miniature. The ×N used to
                // float in the gap between two groups, where it belonged to neither one — enclosing
                // the rows it multiplies is what answers "is that three of the pair above me or the
                // pair below me" without a word of explanation. Drawn even at ×1, so the boxes stay
                // the boundary between groups rather than becoming a second signal of their own.
                //
                // The count heads the group, above the rows it multiplies. Nothing at all at ×1 —
                // no reserved slot, no placeholder: a group that doesn't repeat must not have its
                // rows moved aside to make room for a number that isn't there.
                VStack(alignment: .leading, spacing: 0) {
                    if group.repeatCount > 1 {
                        Text("× \(group.repeatCount)")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundStyle(.white.opacity(0.75))
                            .padding(.leading, 4)
                            .padding(.bottom, 4)
                    }
                    ForEach(group.items.indices, id: \.self) { i in
                        let interval = group.items[i]
                        let colour = interval.phase == .work ? workColor : restColor
                        HStack {
                            // The name takes Work's place, the same swap the timer makes while the
                            // section plays: doing the named thing is what work means here. Rest
                            // keeps its own word — it belongs to no one section, and a rest reading
                            // "Pistol squats" would say you were doing them.
                            Text(interval.phase == .work ? (named ? group.name : "Work") : "Rest")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(colour)
                                .lineLimit(2)
                            Spacer(minLength: 8)
                            Text(secLabel(interval.durationSec))
                                .font(.system(size: 14))
                                .foregroundStyle(.white.opacity(0.85))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(colour.opacity(0.20), in: RoundedRectangle(cornerRadius: 12))
                        .padding(.vertical, 3)
                    }
                }
                .padding(8)
                .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(.white.opacity(0.10), lineWidth: 1))
                .padding(.vertical, 4)
            }
        }
        .padding(.top, 10)

        if preset.repeatAll > 1 {
            Text("All of it × \(preset.repeatAll)")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(.white.opacity(0.7))
                .padding(.top, 8)
        }

        // No Start down here any more. It was the reason this row had to be opened and scrolled
        // past its own contents to be run; the play button in the header does that job from the
        // collapsed row, and is still in view once opened. Two Starts in one card is one too many.
        HStack(spacing: 0) {
            if let onEdit {
                PlainTextButton(text: "Edit", action: onEdit)
            }
            deleteControl
            Spacer()
        }
        .padding(.top, 14)
    }

    // Tap the bin to arm, tap the red "Delete?" pill to commit. Both are kept narrow: a wide
    // confirm label squeezed the Start pill into wrapping onto a second line.
    @ViewBuilder private var deleteControl: some View {
        if armed {
            Button {
                // Disarm as it commits, before the row goes. Two structurally equal saved presets
                // compare equal, so deleting the first slides the second into this slot with
                // `onChange` none the wiser — armed, one tap from going too.
                armed = false
                onDelete()
            } label: {
                Text("Delete?")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(dangerRed)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(dangerRed.opacity(0.22), in: Capsule())
                    .overlay(Capsule().strokeBorder(dangerRed.opacity(0.55), lineWidth: 1))
            }
            .buttonStyle(.plain)
        } else {
            Button { armed = true } label: {
                Image(systemName: "trash")
                    .font(.system(size: 17))
                    .foregroundStyle(dangerRed)
                    .frame(width: 40, height: 40)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Delete preset")
        }
    }
}
