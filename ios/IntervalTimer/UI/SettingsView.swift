import SwiftUI
import IntervalTimerCore

struct SettingsView: View {
    let onBack: () -> Void

    @ObservedObject private var settings = Settings.shared

    var body: some View {
        // The pill floats over the scroll rather than sitting above it, so the content passes
        // underneath instead of being cut off at a hard edge.
        ZStack(alignment: .topLeading) {
            HomeBackground().ignoresSafeArea()
            VStack(spacing: 0) {
                ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SettingsCard(title: "Settings") {
                        VStack(alignment: .leading, spacing: 16) {
                            ToggleRow(label: "Mute", isOn: settings.muted) { settings.updateMuted($0) }
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Volume").font(.system(size: 18)).foregroundStyle(.white)
                                VolumeSlider()
                            }
                            ToggleRow(label: "Run in background", isOn: settings.runInBackground) {
                                settings.updateRunInBackground($0)
                            }
                            prepareRow
                        }
                    }

                    // Minimal rides the title line rather than sitting under the grid. Orthogonal
                    // to the palette on purpose — Minimal + Vesper is a black timer with Vesper on
                    // the edge, and Mono as well gives the plain black-and-white one — so it belongs
                    // to the whole card, not to the row of swatches it used to hang below.
                    SettingsCard(title: "Theme", trailing: {
                        HStack(spacing: 8) {
                            Text("Minimal")
                                .font(.system(size: 14))
                                .foregroundStyle(.white.opacity(0.75))
                            Toggle("", isOn: .constant(settings.minimalBg))
                                .labelsHidden()
                                .tint(.white.opacity(0.35))
                                .allowsHitTesting(false)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { settings.updateMinimalBg(!settings.minimalBg) }
                        // The switch above is inert for the same reason ToggleRow's is — a live
                        // Toggle inside this ScrollView answers a drag but not a tap.
                        .accessibilityRepresentation {
                            Toggle(isOn: Binding(get: { settings.minimalBg },
                                                 set: { settings.updateMinimalBg($0) })) { Text("Minimal") }
                        }
                    }) {
                        PalettePicker()
                    }

                    // Its own panel rather than a dropdown inside Theme: the grid is the biggest thing
                    // on this screen, and burying it behind a disclosure row made it feel like a
                    // footnote.
                    SettingsCard(title: "Language") {
                        VStack(alignment: .leading, spacing: 16) {
                            Text(Language.of(settings.languageCode).english)
                                .font(.system(size: 15))
                                .foregroundStyle(.white.opacity(0.75))
                            ToggleRow(
                                label: "Word mode",
                                // One line, and short enough to stay one. The second sentence
                                // explained a rule you can see the moment you flip it — the Chinese
                                // tile keeps 九 either way — and it was the longest string on the
                                // screen to say it. "≤60s" rather than "under 60s only": the symbol
                                // says it in two characters, and what it buys is vertical space
                                // between this row and the grid, not brevity for its own sake.
                                sub: "Thirty-two, not 32  ( ≤60s )",
                                isOn: settings.wordMode
                            ) { settings.updateWordMode($0) }
                            LanguageGrid().padding(.top, 8)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
                // Clear of the pill at rest; scrolled, the content simply travels under it.
                .padding(.top, 60)
                .frame(maxWidth: .infinity, alignment: .leading)
                }
                // No scroll indicator anywhere in the app — see HomeView. Owner's standing preference.
                .scrollIndicators(.never)
            }
            BackPill(onBack: onBack).padding(.leading, 16).padding(.top, 4)
        }
    }

    private var prepareRow: some View {
        HStack(spacing: 0) {
            Text("Get ready")
                .font(.system(size: 18))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
            GlassCircle(glyph: "−") { m in settings.updatePrepareSec(settings.prepareSec - 5 * m) }
            Text(settings.prepareSec == 0 ? "Off" : secLabel(settings.prepareSec))
                .font(.system(size: 17))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .frame(width: 64)
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { settings.updatePrepareSec(DEFAULT_PREPARE_SEC) }
            GlassCircle(glyph: "+") { m in settings.updatePrepareSec(settings.prepareSec + 5 * m) }
        }
    }
}

// MARK: - Card

/// `trailing` is a control that belongs to the whole card, parked on its title line.
private struct SettingsCard<Content: View, Trailing: View>: View {
    let title: String
    @ViewBuilder let trailing: Trailing
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 0) {
                Text(title.uppercased())
                    .font(.system(size: 13, weight: .bold))
                    .kerning(2)
                    .foregroundStyle(.white.opacity(0.45))
                Spacer(minLength: 8)
                trailing
            }
            content
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(glassFill, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(glassBorder(), lineWidth: 1))
    }
}

/// Most cards have nothing on the title line but the title.
private extension SettingsCard where Trailing == EmptyView {
    init(title: String, @ViewBuilder content: () -> Content) {
        self.init(title: title, trailing: { EmptyView() }, content: content)
    }
}

private struct ToggleRow: View {
    let label: String
    var sub: String? = nil
    let isOn: Bool
    let onChange: (Bool) -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.system(size: 18)).foregroundStyle(.white)
                if let sub {
                    Text(sub).font(.system(size: 13)).foregroundStyle(.white.opacity(0.5))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // Indicator only; the row owns the gesture. A live Toggle here answered a drag but not a
            // tap — it is a UIKit control, and the enclosing ScrollView delays touches to its content
            // long enough that a quick press never lands. Measured: a synthetic tap dead on the knob
            // left the value unchanged, while the palette swatches beside it, which use a SwiftUI tap
            // gesture, flipped every time. So use that instead — and put it on the whole row, which
            // makes the label a target too.
            Toggle("", isOn: .constant(isOn))
                .labelsHidden()
                // Chrome stays grey here. The Android theme's "on" track is near-white, which on iOS
                // would swallow the knob — that one is always white and can't be tinted.
                .tint(.white.opacity(0.35))
                .allowsHitTesting(false)
        }
        .contentShape(Rectangle())
        .onTapGesture { onChange(!isOn) }
        // The switch above is inert, so VoiceOver is handed a live one in its place — otherwise the
        // row reads as a label with an untouchable control next to it.
        .accessibilityRepresentation {
            Toggle(isOn: Binding(get: { isOn }, set: onChange)) {
                Text(sub.map { "\(label). \($0)" } ?? label)
            }
        }
    }
}

// MARK: - Volume

/// Touch-friendly volume slider with a big round dot thumb and a simple rounded track.
///
/// Hand-drawn rather than a styled `Slider`: UIKit's slider won't take a 28pt dot on a 6pt bed
/// without a UIViewRepresentable, and the gesture is three lines. The split `Slider` gives you free
/// — live value, persist on lift — is kept explicitly in `onChanged`/`onEnded`, because a drag
/// otherwise queues one defaults write per touch sample.
private struct VolumeSlider: View {
    @ObservedObject private var settings = Settings.shared

    /// The thumb's own copy of the volume. `Settings.volume` is deliberately not @Published (see its
    /// comment), so the drag renders from here and pushes the value through `updateVolume` for the
    /// Beeper to read. Safe to seed once: this slider is the only writer, so the two can't diverge,
    /// and re-entering Settings re-seeds it anyway.
    @State private var value = Settings.shared.volume

    private let thumb: CGFloat = 28

    var body: some View {
        // Muted pins the slider to 0; dragging up un-mutes.
        let value = settings.muted ? 0 : value
        GeometryReader { geo in
            let travel = max(geo.size.width - thumb, 1)
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.16)).frame(height: 6)
                Capsule().fill(.white.opacity(0.7)).frame(width: thumb / 2 + travel * value, height: 6)
                Circle().fill(.white).frame(width: thumb, height: thumb).offset(x: travel * value)
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in set((g.location.x - thumb / 2) / travel) }
                    .onEnded { _ in settings.persistVolume() }
            )
        }
        .frame(height: thumb)
    }

    private func set(_ raw: Double) {
        let v = min(max(raw, 0), 1)
        if settings.muted && v > 0 { settings.updateMuted(false) }
        value = v
        settings.updateVolume(v)
    }
}

// MARK: - Theme picker

/// Theme picker: each swatch is the palette's own colours in the order a workout meets them —
/// prepare, work, rest — so you choose by looking at the actual thing rather than by reading a name
/// you've never heard of.
///
/// Two across, growing downward. Plain rows over a chunked list rather than a `LazyVGrid`: this sits
/// inside the settings screen's scroll view, and with a fixed handful of swatches there is nothing to
/// be lazy about anyway.
///
/// Two, not the three the language grid uses. Width is the only dimension a swatch can grow in
/// without looking stretched — the stripes are the aura seen through a slot, and a tall thin slot
/// shows less of it than a wide one, not more. The cost is five rows instead of three, on a page that
/// already scrolls.
private struct PalettePicker: View {
    @ObservedObject private var settings = Settings.shared

    var body: some View {
        let swatchRows = rows(Palette.allCases, across: 2)
        // No heading of its own: the card it sits in is called Theme, and two of those in a row read
        // as a section inside a section.
        VStack(alignment: .leading, spacing: 14) {
            ForEach(swatchRows.indices, id: \.self) { r in
                HStack(spacing: 12) {
                    ForEach(0..<2, id: \.self) { c in
                        if let p = swatchRows[r][c] {
                            PaletteSwatch(palette: p)
                        } else {
                            emptyCell
                        }
                    }
                }
            }
        }
    }
}

private struct PaletteSwatch: View {
    let palette: Palette

    @ObservedObject private var settings = Settings.shared

    var body: some View {
        let selected = palette == settings.palette
        let ordinal = Palette.allCases.firstIndex(of: palette) ?? 0
        // The order a workout meets them.
        let stripes = [palette.prep, palette.work, palette.rest]
        VStack(spacing: 6) {
            HStack(spacing: 4) {
                // Each stripe is the timer's actual shader at swatch size, not paint mixed to look
                // like it: every hand-tuned imitation was either too hot or too flat, and would have
                // drifted the moment the real aura changed. Separately rounded and spaced, so the
                // three read as three phases rather than as one band that changes colour twice.
                ForEach(stripes.indices, id: \.self) { i in
                    // Mixing in the stripe index stops one theme's three stripes sharing a frame.
                    AuraSwatch(glow: stripes[i], seed: Float(ordinal * 3 + i) * 3.7)
                        .clipShape(RoundedRectangle(cornerRadius: 7))
                }
            }
            .padding(4)
            // 48: the 44 it always was, plus 10%. Height alone was the wrong lever — at three across
            // it gave tall thin slivers, and stretching a swatch the long way reads as a distortion
            // rather than as more of anything. With the width fixed first (two across, then 10% back
            // off it) the extra height costs no vignette: the shader's `p.x *= w/h` means a taller
            // box reaches *less* far into the falloff.
            .frame(height: 48)
            // Ring only on the selected one. A frame around every swatch was a grid of boxes
            // competing with the colours they were framing; now the ring means something. Padding
            // stays either way, so nothing shifts as the selection moves.
            .overlay {
                if selected {
                    RoundedRectangle(cornerRadius: 12).strokeBorder(.white, lineWidth: 2)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { settings.updatePalette(palette) }

            Text(palette.label)
                .font(.system(size: 12, weight: selected ? .bold : .regular))
                .foregroundStyle(.white.opacity(selected ? 1 : 0.5))
                .lineLimit(1)
        }
        // 6 off each side, which takes ~10% off every stripe. The shader corrects for aspect
        // (`p.x *= iResolution.x / iResolution.y`), so a box wider than it is tall reaches further
        // out into the falloff horizontally and the corners go black. Square enough, and the stripe
        // sits inside the bloom instead of showing you the vignette around it.
        .padding(.horizontal, 6)
    }
}

// MARK: - Language picker

/// Language picker: every language counting 9 down to 1 at once, each over the current theme's work
/// colour, so you pick by watching what the timer will actually look like rather than by reading a
/// list of names.
///
/// One clock for the whole grid — every tile ticking off the same second stays in step, and it's one
/// update a second rather than one per tile.
/// The width of one tile in a full row, so a short row can match it instead of stretching.
private struct CellWidthKey: PreferenceKey {
    static var defaultValue: CGFloat { 0 }
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct LanguageGrid: View {
    @ObservedObject private var settings = Settings.shared
    @State private var start = Date()
    @State private var cellWidth: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // Chinese and Japanese draw the same numerals from the same digits and the same 十 rule —
        // only three phase words tell them apart — so they share one tile that cycles between them,
        // and the tile shows whichever is live.
        let langs = Language.allCases
            .filter { $0 != .ja }
            .map { $0 == .zh && settings.languageCode == "ja" ? Language.ja : $0 }
        let rows = rowsOfThree(langs)
        // Reduce Motion holds the grid on one second instead of dropping it — the tiles are here to
        // be read, and a blank panel picks no language. Nine because that's where the count starts,
        // so a still grid shows the same frame a moving one opens on. `.periodic` has no `paused:`
        // form the way `.animation` does, hence a branch rather than a flag.
        if reduceMotion {
            grid(rows, second: 9)
        } else {
            TimelineView(.periodic(from: start, by: 1)) { ctx in
                grid(rows, second: 9 - max(0, Int(ctx.date.timeIntervalSince(start))) % 9)
            }
        }
    }

    // Tight gaps: the tiles are the content here, and wide gutters left the panel mostly black.
    private func grid(_ rows: [[Language?]], second: Int) -> some View {
        VStack(spacing: 8) {
            ForEach(rows.indices, id: \.self) { r in
                let tiles = rows[r].compactMap { $0 }
                if tiles.count == 3 {
                    HStack(spacing: 8) {
                        ForEach(tiles, id: \.self) { lang in
                            LanguageTile(lang: lang, second: second)
                                // One measurement feeds every short row below. Taken from a full row,
                                // so it is the real cell width rather than a guess at it.
                                .background(GeometryReader { g in
                                    Color.clear.preference(key: CellWidthKey.self, value: g.size.width)
                                })
                        }
                    }
                } else {
                    // A short last row is centred rather than left-aligned against one big gap. The
                    // tiles are pinned to the measured cell width, so they stay the size of every
                    // other row's instead of stretching to fill it — which is the whole reason this
                    // needs a measurement at all: SwiftUI has no fractional flex to hand half a cell
                    // to a spacer, the way the Kotlin's `weight(holes / 2f)` does.
                    HStack(spacing: 8) {
                        Spacer(minLength: 0)
                        ForEach(tiles, id: \.self) { lang in
                            LanguageTile(lang: lang, second: second)
                                .frame(width: cellWidth > 0 ? cellWidth : nil)
                        }
                        Spacer(minLength: 0)
                    }
                }
            }
        }
        .onPreferenceChange(CellWidthKey.self) { cellWidth = $0 }
    }
}

private struct LanguageTile: View {
    let lang: Language
    let second: Int

    @ObservedObject private var settings = Settings.shared

    var body: some View {
        let selected = lang.code == settings.languageCode
        // The selected tile previews the theme's *starting* colour and its "get ready" word; every
        // other tile is mid-work. So selection reads as a second colour from the same theme rather
        // than only as a ring — and the phase word is what finally tells 运动 from 運動.
        let phaseWord = selected ? lang.ready : lang.work
        VStack(spacing: 6) {
            GeometryReader { geo in
                // A bubble, not a box: a percentage radius stays organic at any tile size, matching
                // the gradients drifting inside them rather than framing them in hard corners.
                // Halved from 0.38 — that much rounding was eating the corners of a tile whose whole
                // job is to hold a numeral.
                let w = geo.size.width - 8
                let h = geo.size.height - 8
                ZStack {
                    // Seeded off the enum position: without it every tile froze the aura at the same
                    // instant and the grid read as one image stamped out once per tile. Both halves
                    // of the shared tile take Chinese's seed, so cycling relabels the tile instead of
                    // restamping its aura — otherwise a tap reads as a different tile.
                    // Full frame, unlike the theme stripes. A tile this size has room for the
                    // falloff to read as depth behind the numeral rather than as darkness.
                    AuraSwatch(glow: selected ? prepColor : workColor,
                               seed: Float(lang.han ? Language.zh.ordinal : lang.ordinal) * 3.7,
                               zoom: 1)
                        .clipShape(RoundedRectangle(cornerRadius: min(w, h) * 0.19))

                    // The phase word rides up top like the timer's own label, small and out of the
                    // way, rather than sharing the middle with the numeral.
                    Text(phaseWord)
                        .font(.system(size: fittedFontSize(phaseWord, weight: .medium,
                                                           maxW: w * 0.80, maxH: h * 0.14,
                                                           minPt: 5, maxPt: 9),
                                      weight: .medium))
                        .foregroundStyle(.white.opacity(0.7))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        // 3, down from 9. Measured on the Android twin: the glyphs are 6.7pt tall and sat 8pt
                        // below the tile's coloured edge, so this lifts them by about their own
                        // height and leaves 2pt of air.
                        .padding(.top, 3)
                        .frame(maxHeight: .infinity, alignment: .top)

                    numeral(w: w, h: h)
                }
                .padding(4)
                .overlay {
                    if selected {
                        RoundedRectangle(cornerRadius: geo.size.width * 0.19)
                            .strokeBorder(.white, lineWidth: 2)
                    }
                }
                .contentShape(Rectangle())
                // The shared tile cycles: first tap takes Chinese, each one after flips to the other.
                // Everything else just selects itself.
                .onTapGesture {
                    settings.updateLanguage(
                        lang.han ? (settings.languageCode == "zh" ? "ja" : "zh") : lang.code
                    )
                }
            }
            // Taller than wide, like the screen it's previewing — and the extra height is what lets
            // the numeral run big with the label tucked above it.
            .aspectRatio(0.82, contentMode: .fit)

            Text(
                // Just the endonym: "中文 · Chinese" clips mid-string in a third-width tile, and the
                // native name is the more useful half here anyway.
                lang.english.components(separatedBy: " ·")[0]
            )
            .font(.system(size: 12, weight: selected ? .bold : .regular))
            .foregroundStyle(.white.opacity(selected ? 1 : 0.5))
            .lineLimit(1)
        }
    }

    @ViewBuilder private func numeral(w: CGFloat, h: CGFloat) -> some View {
        if lang.cistercian {
            // Tighter than the text budget, not the same: a fitted glyph draws well inside its font's
            // box, but the canvas draws to its edges, so 0.52 of the height put the top of the stave
            // through the phase label.
            let side = min(w * 0.72, h * 0.44)
            // Centred on the stave is not centred to the eye. The tile only ever counts 9 down to 1,
            // so every glyph is stave + one top-right quadrant: ink spans x 0..1 of the 2-wide box,
            // putting its true centre a quarter-box right of where it's drawn. Shifting back by that
            // quarter is what makes it sit in the middle. The drop lands its centre where the
            // neighbouring tiles put theirs — a fitted numeral clears the phase label on its own, but
            // a full-height stave runs right up under it.
            CistercianNumeral(number: second, strokeWidth: side / 14)
                .frame(width: side, height: side)
                .offset(x: -side * 0.25, y: side * 0.23)
        } else {
            // Follows the Word mode switch, which sits directly above this grid. It used to spell
            // whatever the setting said, on the grounds that English, Russian, Spanish and French
            // otherwise print the same Western 9 and the tiles would be indistinguishable. They
            // aren't: the phase word up top is already Work / Работа / Trabajo / Effort. Ignoring the
            // switch made it look broken — you flip it and the nine words under your thumb don't move.
            let text = settings.wordMode && lang.digits == nil && !lang.stacks
                ? Numbers.words(second * 1000, lang)
                : (Numbers.clockLines(second * 1000, lang).last ?? "")
            // Bold only where a real bold face exists. The system CJK fallback ships Regular only, so
            // a bold request there gets synthesised by inflating the outline, which spikes at acute
            // stroke joins (visible as a notch in 九).
            let bold = !lang.cjk
            // The numeral is the point of the tile, so it gets most of the box.
            Text(text)
                .font(.system(size: fittedFontSize(text, weight: bold ? .bold : .regular,
                                                   maxW: w * 0.80, maxH: h * 0.52,
                                                   minPt: 9, maxPt: 46),
                              weight: bold ? .bold : .regular))
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                // Down off dead centre, like the Cistercian glyph: a word sits high in its line box
                // (the box reserves descender room "cinco" never uses), so centring the box leaves the
                // ink riding above the middle of the tile.
                .offset(y: 8)
        }
    }
}

// MARK: - Shared layout

/// Rows of three, short last row padded with nils — which keeps its cells at the same width instead
/// of stretching them across the panel.
private func rowsOfThree<T>(_ items: [T]) -> [[T?]] { rows(items, across: 3) }

/// Chunked into fixed-width rows, the short last one padded with nils so every cell keeps its share
/// of the width instead of the survivors stretching across it.
private func rows<T>(_ items: [T], across: Int) -> [[T?]] {
    stride(from: 0, to: items.count, by: across).map { i in
        (i..<i + across).map { $0 < items.count ? items[$0] : nil }
    }
}

/// The hole a nil leaves: claims its third of the row and no height at all.
private var emptyCell: some View {
    Color.clear.frame(maxWidth: .infinity, maxHeight: 0)
}
