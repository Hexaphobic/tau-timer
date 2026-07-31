import SwiftUI
import UIKit
import IntervalTimerCore

/// Deliberate hold needed to pause, so a pocket brush or a stray palm can't stop a set.
private let HOLD_TO_PAUSE: TimeInterval = 0.4
/// How long the hint stays up after a tap — long enough to read, short enough not to nag.
private let HINT_SEC: TimeInterval = 1.6
// Blue reads far dimmer than green at equal saturation, so rest uses a bright sky tone to stay
// legible once the progress arms shrink.
private func glowColor(_ phase: Phase) -> Color {
    switch phase {
    case .prepare: return prepColor
    case .work: return workColor
    case .rest: return restColor
    case .done: return doneGray
    }
}

/// Bold only where a real bold face exists. The system CJK fonts are Regular-only, so a bold
/// request there gets synthesised by inflating the outline — which spikes at acute stroke joins.
private func glyphWeight(_ lang: Language) -> Font.Weight { lang.cjk ? .regular : .bold }
/// The same choice in UIKit terms, because the fitter measures with UIFont.
private func fitWeight(_ lang: Language) -> UIFont.Weight { lang.cjk ? .regular : .bold }

struct TimerView: View {
    let ui: TimerUiState
    let onPause: () -> Void
    let onResume: () -> Void
    let onEnd: () -> Void

    @ObservedObject private var settings = Settings.shared

    // Pause is a deliberate hold, not a tap. A tap just says so.
    @State private var holding = false
    @State private var showHint = false
    @State private var holdFill: Double = 0

    /// The window's own safe-area insets.
    ///
    /// Read from the window rather than the `GeometryProxy`, because this screen is deliberately
    /// full-bleed — the aura and the progress arms run to the physical edges — and a proxy inside a
    /// view that has already ignored the safe area reports zero. That zero put the phase label
    /// straight under the Dynamic Island.
    private var windowSafeAreaInsets: EdgeInsets {
        let i = (UIApplication.shared.connectedScenes.first as? UIWindowScene)?
            .keyWindow?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }

    var body: some View {
        let lang = Language.of(settings.languageCode)
        // No crossfade between phase colours: the glow is a shader parameter on a plain View, and
        // SwiftUI only interpolates values it owns, so the 700ms tween the Kotlin had has no
        // counterpart short of running two shaders through a dissolve.
        let glow = glowColor(ui.phase)

        GeometryReader { geo in
            // The Flip's cover screen had a second, squatter layout here. Android-only — no iPhone
            // has that geometry, so there is one layout now.
            let w = geo.size.width
            let h = geo.size.height
            let insets = windowSafeAreaInsets

            ZStack {
                // Pause and finish never blur or dim this — they only swap out the centre text, so
                // the glow, the progress arms and the round counter stay exactly as they were.
                //
                // Done keeps the glow at full bloom: letting the progress dim it made the whole
                // finish read as the screen going dark.
                AuraBackground(glow: glow, progress: ui.done ? 1 : ui.fraction)
                if !ui.done { SplitProgress(remaining: 1 - ui.fraction, color: glow) }

                TimerContent(
                    ui: ui,
                    lang: lang,
                    wordMode: settings.wordMode,
                    w: w,
                    h: h,
                    labelSize: min(max(w / 7, 16), 72),
                    counterSize: min(max(w / 14, 11), 34)
                )
                .padding(insets)
                .padding(.horizontal, 14)

                if ui.done {
                    // Two overlapping streams rather than one, so shells land on top of each other.
                    ConfettiBurst(seed: 11, startDelay: 0.35).allowsHitTesting(false)
                    ConfettiBurst(seed: 77, startDelay: 0.90).allowsHitTesting(false)
                    // The whole screen dismisses — no aiming for a button when you're spent.
                    Text("Done")
                        .font(.system(size: 64, weight: .bold))
                        .foregroundStyle(.white)
                        .tracking(2)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .contentShape(Rectangle())
                        .onTapGesture(perform: onEnd)
                } else if ui.paused {
                    PauseMenu(onResume: onResume, onEnd: onEnd)
                }

                // Sits below the round counter and its pips, not on them.
                //
                // Mounted even while invisible: a bar that only comes into existence once the press
                // begins is inserted already AT its target, with nothing for SwiftUI to interpolate
                // from — it drew full on its first frame instead of sweeping. Kept alive, the press
                // is a real value change and the sweep runs.
                HoldHint(fill: holdFill)
                    .opacity(!ui.done && !ui.paused && (holding || showHint) ? 1 : 0)
                    // An invisible pill must not eat the press it exists to explain.
                    .allowsHitTesting(false)
                    .padding(.bottom, insets.bottom + 16)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            }
            .contentShape(Rectangle())
            .gesture(holdGesture)
        }
        .ignoresSafeArea()
        // Keep the display awake for the whole screen, Done included — releasing on `done` visibly
        // dimmed the finish. Only the idle timer is held: iOS has no app-level brightness override
        // worth taking, since UIScreen.brightness moves the user's own system slider and hands it
        // back changed.
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        .task(id: holding) {
            guard holding else { return }
            try? await Task.sleep(for: .seconds(HOLD_TO_PAUSE))
            // Cancelled means the finger lifted first, which is a tap.
            guard !Task.isCancelled else { return }
            holding = false
            releaseHold()
            onPause()
        }
        .task(id: showHint) {
            guard showHint else { return }
            try? await Task.sleep(for: .seconds(HINT_SEC))
            if !Task.isCancelled { showHint = false }
        }
    }

    private var holdGesture: some Gesture {
        // minimumDistance 0 so the press registers on touch-down rather than on the first movement.
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !ui.done, !ui.paused, !holding else { return }
                showHint = false
                // Outside the animation: the pill itself appears at once, only the bar sweeps.
                holding = true
                withAnimation(.linear(duration: HOLD_TO_PAUSE)) { holdFill = 1 }
            }
            .onEnded { _ in
                // Still holding means the timeout hasn't fired yet, so this lift is a tap.
                guard holding else { return }
                holding = false
                showHint = true
                releaseHold()
            }
    }

    private func releaseHold() {
        withAnimation(.linear(duration: 0.18)) { holdFill = 0 }
    }
}

/// "Hold to pause" pill; [fill] 0...1 sweeps a brighter bar across it as the hold progresses, so the
/// 400ms press reads as progress rather than a dead screen.
private struct HoldHint: View {
    let fill: Double

    var body: some View {
        Text("Hold to pause")
            .font(.system(size: 15, weight: .medium))
            .foregroundStyle(.white.opacity(0.9))
            .padding(.horizontal, 22)
            .padding(.vertical, 11)
            .background {
                // Scaled rather than width-animated: the bar is a plain rectangle, and a scale from
                // the leading edge is the same sweep without a GeometryReader to read the pill.
                Color.white.opacity(0.22)
                    .scaleEffect(x: fill, anchor: .leading)
                    .background(Color.black.opacity(0.55))
                    .clipShape(Capsule())
            }
            .overlay(Capsule().strokeBorder(glassBorder(), lineWidth: 1))
    }
}

/// One remembered fit, held across body evaluations. The state ticks at 30fps but measuring text is
/// a real layout, and its inputs change once per interval (or once a second in word mode) — this is
/// the Kotlin's `remember(key)` doing the same job.
private final class FitCache {
    private var key: [AnyHashable] = []
    private var value: CGFloat = 0

    func size(_ key: [AnyHashable], _ compute: () -> CGFloat) -> CGFloat {
        if key != self.key {
            self.key = key
            value = compute()
        }
        return value
    }
}

private struct TimerContent: View {
    let ui: TimerUiState
    let lang: Language
    let wordMode: Bool
    /// Screen dimensions, not this view's: the budgets below are the room the number has on the
    /// panel, which is what the Kotlin measured too.
    let w: CGFloat
    let h: CGFloat
    let labelSize: CGFloat
    let counterSize: CGFloat

    @State private var clockFit = FitCache()
    @State private var wordFit = FitCache()

    var body: some View {
        GeometryReader { g in
            ZStack {
                // The number owns the true middle of the screen, regardless of the label riding up
                // top. Finished or paused it steps aside entirely — the centred "Done"/"Paused"
                // takes that spot.
                number.frame(maxWidth: .infinity, maxHeight: .infinity)

                // Just the phase label up top; the count and its progress live at the bottom, so the
                // big number owns the whole middle of the screen.
                Text(label)
                    .font(.system(size: labelSize, weight: glyphWeight(lang)))
                    .foregroundStyle(.white)
                    .padding(.top, 24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

                // How far through the workout, down at the bottom: the count, then a pip per
                // repetition. Sits above the hold-to-pause hint, which keeps its own room.
                if ui.totalRounds > 0 {
                    VStack(spacing: 12) {
                        Text(counterText)
                            // In the chosen language's own numerals — this printed Western digits in
                            // every language, so a Chinese workout still counted "3 / 16" under 运动.
                            .font(lang.digits == nil
                                  ? Font.system(size: counterSize).monospacedDigit()
                                  : Font.system(size: counterSize))
                            .foregroundStyle(.white.opacity(ui.round > 0 ? 0.80 : 0))
                            .tracking(2)
                        OverallProgress(round: ui.round, totalRounds: ui.totalRounds,
                                        roundsPerPass: ui.roundsPerPass,
                                        live: ui.running && !ui.paused && !ui.done,
                                        width: g.size.width)
                    }
                    .padding(.bottom, 92)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                }
            }
            .frame(width: g.size.width, height: g.size.height)
        }
    }

    private var label: String {
        // Finished: the big centred "Done" is the whole message, so nothing rides up top.
        if ui.done { return "" }
        switch ui.phase {
        case .prepare: return lang.ready
        case .work: return lang.work
        case .rest: return lang.rest
        case .done: return ""
        }
    }

    private var counterText: String {
        ui.round > 0
            ? "\(Numbers.count(ui.round, lang)) / \(Numbers.count(ui.totalRounds, lang))"
            : " "
    }

    /// Words only stand in for languages with no numerals of their own — native-glyph scripts (Thai,
    /// Hindi, Chinese…) already look distinct, so they just show their glyphs. Under a minute only.
    private var showWords: Bool {
        wordMode && !ui.done && lang.digits == nil && !lang.cistercian && !lang.stacks
            && ui.remainingMs < 60_000
    }

    @ViewBuilder private var number: some View {
        // Space the big number may occupy. 26pt of side margin each side: the fit fills its width
        // budget exactly, and a three-glyph Han line is width-bound, so at 14 the 五十八 came to rest
        // a pixel from the perimeter stroke. Latin is height-bound and doesn't notice.
        let availW = max(w - 52, 1)
        let availH = max(h * 0.42, 1)

        // Everything here runs only while the clock is live: done and paused both step aside for the
        // centred "Done"/"Paused".
        if ui.done || ui.paused {
            EmptyView()
        } else if lang.cistercian {
            // One glyph for the whole count, not M:SS — the cipher packs four digits into a single
            // figure, so the clock is total seconds and never stacks or resizes.
            let side = max(min(w - 52, h * 0.42), 1)
            CistercianNumeral(number: cistercianSeconds(ui.remainingMs), strokeWidth: side / 18)
                .frame(width: side, height: side)
        } else if showWords {
            let word = Numbers.words(ui.remainingMs, lang)
            let size = wordFit.size([word, lang, availW, availH]) {
                fittedFontSize(word, weight: fitWeight(lang),
                               maxW: availW, maxH: availH, minPt: 24, maxPt: 150)
            }
            Text(word)
                .font(.system(size: size, weight: glyphWeight(lang)))
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize()
        } else {
            clock(availW: availW, availH: availH)
        }
    }

    @ViewBuilder private func clock(availW: CGFloat, availH: CGFloat) -> some View {
        let lines = Numbers.clockLines(ui.remainingMs, lang)
        // Monospaced digits for Western numerals; glyph scripts take the default face.
        let mono = lang.digits == nil && !lang.stacks

        // ONE size for the whole interval, fitted to the widest value the interval will ever reach —
        // not to what happens to be on screen this second.
        //
        // Fitting live meant the number resized whenever the glyph count changed: in Han, 三十九 is
        // three glyphs and 四十 is two, so every crossing of a ten made it lurch bigger. Pinning
        // trades that away — short values now sit smaller in the middle of the screen — for a count
        // that holds still, which is what you want on something you glance at mid-set.
        //
        // Keyed on the interval, so this measures once per interval rather than per second. Both
        // lines of a stacked clock take the smaller fit, or MM and SS would disagree.
        let size = clockFit.size([ui.intervalDurationMs, lang, availW, availH]) {
            let widest = Numbers.widestClockLines(ui.intervalDurationMs, lang)
            // The fitter budgets INK, but a stacked column is laid out in LINE BOXES, which carry
            // the font's full ascent and descent on top.
            let perLine = widest.count > 1
                ? availH * 1.3 / CGFloat(widest.count) * 0.87
                : availH
            return widest.map {
                fittedFontSize($0, weight: fitWeight(lang), monospaced: mono,
                               maxW: availW, maxH: perLine, minPt: 32, maxPt: 260)
            }.min() ?? 32
        }
        let base = Font.system(size: size, weight: glyphWeight(lang))
        let font = mono ? base.monospacedDigit() : base

        if lines.count > 1 {
            VStack(spacing: 0) {
                clockLine(lines[0], font)
                ColonDots(size: size)
                clockLine(lines[1], font)
            }
        } else {
            clockLine(lines.first ?? "", font)
        }
    }

    private func clockLine(_ text: String, _ font: Font) -> some View {
        Text(text)
            .font(font)
            .foregroundStyle(.white)
            .lineLimit(1)
            .fixedSize()
    }
}

/// The stacked clock's separator: the colon turned on its side, drawn rather than typed so the dot
/// size and the gaps above and below scale with [size] instead of inheriting a glyph's own metrics.
private struct ColonDots: View {
    let size: CGFloat

    var body: some View {
        let dot = size * 0.10
        HStack(spacing: dot * 1.4) {
            Circle().fill(.white).frame(width: dot, height: dot)
            Circle().fill(.white).frame(width: dot, height: dot)
        }
        .padding(.vertical, dot * 1.6)
    }
}

/// How far through the workout you are — one pip per work set, lit as each one lands. It steps with
/// the "1 / 7" counter above it and holds still in between; a bar that crept every second just
/// duplicated the countdown already filling the screen.
///
/// Rows are the workout's own shape where it has one: four sets run twice is two rows of four, so the
/// grid says "and again" without a word on it. `roundsPerPass` carries that shape; 0 wraps by count.
///
/// The set you are in the middle of breathes rather than sitting flat, so the grid says *where* you
/// are and not just how far. It stops the moment the clock does — a pip still pulsing on a paused
/// timer would be the screen telling you something is running when nothing is.
///
/// White rather than the phase colour: it sits inside the phase-coloured aura, and a coloured bar on
/// a coloured wash reads as a smudge.
private struct OverallProgress: View {
    let round: Int
    let totalRounds: Int
    let roundsPerPass: Int
    /// Running, not paused and not finished — the only state in which anything should be moving.
    let live: Bool
    /// The row this sits in — the fractions below keep the pips off a wide screen's edges.
    let width: CGFloat

    /// One breath, in seconds, out and back.
    private static let breathPeriod = 1.6

    private var done: Int { min(max(round, 0), totalRounds) }

    /// `breath` is the pulse's current brightness, sampled from the clock rather than animated into.
    ///
    /// It has to be a continuously varying number, NOT a Bool flipped inside a `repeatForever`
    /// `withAnimation`. That is the obvious way to write this and it is wrong here: the animation
    /// attaches at the instant the flag changes, so exactly one pip would ever pulse — the one that
    /// happened to be current when the view appeared. Every later round would inherit a settled
    /// value and sit still. Sampling time has no such edge: whichever pip is current reads the phase
    /// the workout is already in.
    private func alpha(_ i: Int, breath: Double) -> Double {
        if live && i == done - 1 { return breath }
        return i < done ? 0.85 : 0.22
    }

    var body: some View {
        // Paused when the clock is, so a still screen costs no frames.
        TimelineView(.animation(paused: !live || done == 0)) { ctx in
            grid(breath: Self.breath(at: ctx.date))
        }
    }

    /// The trough stays above the unlit 0.22 so the current pip never reads as one you haven't done.
    private static func breath(at date: Date) -> Double {
        let phase = date.timeIntervalSinceReferenceDate
            .truncatingRemainder(dividingBy: breathPeriod) / breathPeriod
        return 0.45 + 0.55 * (0.5 - 0.5 * cos(2 * .pi * phase))
    }

    @ViewBuilder
    private func grid(breath: Double) -> some View {
        let layout = Pips.rows(totalRounds, pass: roundsPerPass)
        if !layout.isEmpty {
            // One cell size for the whole grid, from the width a full row of eight needs — so three
            // rounds and a row of a twenty-four-round workout draw the same square, and the rows can
            // simply be centred. The second term only bites in the single-line case, where the count
            // is allowed to run past eight and the cells have to shrink to fit it.
            let gap: CGFloat = 4
            let available = width * 0.46
            let widest = CGFloat(layout.max() ?? 1)
            let cell = min((available - gap * CGFloat(Pips.perRow - 1)) / CGFloat(Pips.perRow),
                           (available - gap * (widest - 1)) / widest)
            // Running start per row, so a row knows which pips it holds without a mutable counter.
            let starts = layout.reduce(into: [0]) { $0.append($0.last! + $1) }
            VStack(spacing: gap) {
                ForEach(layout.indices, id: \.self) { r in
                    HStack(spacing: gap) {
                        ForEach(0..<layout[r], id: \.self) { c in
                            RoundedRectangle(cornerRadius: cell * 0.34)
                                .fill(.white.opacity(alpha(starts[r] + c, breath: breath)))
                                .frame(width: cell, height: cell)
                        }
                    }
                }
            }
        } else {
            // Past the grid's ceiling even squares are a wall of dots, so it degrades to one bar —
            // still stepping per round, just no longer drawn one-per-round.
            Capsule()
                .fill(.white.opacity(0.22))
                .frame(width: width * 0.52, height: 4)
                .overlay(alignment: .leading) {
                    Capsule()
                        .fill(.white.opacity(0.85))
                        .frame(width: width * 0.52 * Double(done) / Double(max(totalRounds, 1)))
                }
        }
    }
}

/// Same voice as the finish screen: no panel, no scrim, nothing floating on top — this simply takes
/// the place of the number, so the glow and progress arms behind it read as untouched.
private struct PauseMenu: View {
    let onResume: () -> Void
    let onEnd: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text("Paused")
                .font(.system(size: 58, weight: .bold))
                .foregroundStyle(.white)
                .tracking(2)
            Spacer().frame(height: 40)
            HStack(spacing: 28) {
                PauseAction(play: true, accent: workColor, action: onResume)
                PauseAction(play: false, accent: dangerRed, action: onEnd)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A big round glyph button in the app's tinted-glass language — the same treatment as the coloured
/// pills elsewhere, just circular and scaled up for a thumb.
private struct PauseAction: View {
    let play: Bool
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Canvas { ctx, size in
                let s = min(size.width, size.height)
                var path = Path()
                if play {
                    // Vertices chosen so the triangle's centroid — not its bounding box — lands on
                    // the centre. A box-centred play triangle always looks shifted left.
                    path.move(to: CGPoint(x: s * 0.267, y: s * 0.11))
                    path.addLine(to: CGPoint(x: s * 0.967, y: s * 0.5))
                    path.addLine(to: CGPoint(x: s * 0.267, y: s * 0.89))
                    path.closeSubpath()
                    ctx.fill(path, with: .color(.white))
                } else {
                    let i = s * 0.10
                    path.move(to: CGPoint(x: i, y: i))
                    path.addLine(to: CGPoint(x: s - i, y: s - i))
                    path.move(to: CGPoint(x: s - i, y: i))
                    path.addLine(to: CGPoint(x: i, y: s - i))
                    ctx.stroke(path, with: .color(.white),
                               style: StrokeStyle(lineWidth: s * 0.15, lineCap: .round))
                }
            }
            .frame(width: 36, height: 36)
            .frame(width: 96, height: 96)
            .background(accent.opacity(0.22), in: Circle())
            .overlay(Circle().strokeBorder(accent.opacity(0.55), lineWidth: 1.5))
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

/// Five one-second shells ≈ five seconds of fireworks, then it settles.
private let BURST_COUNT = 5
private let SHELL_SEC: Double = 1.1

private struct Spark {
    let angle: Double
    let speed: Double
    let radius: Double
    let color: Color
}

private struct Shell {
    let sparks: [Spark]
    /// Fraction of the screen away from centre, so each shell launches from a different spot.
    let origin: CGPoint
}

/// A one-shot firework at the finish: sparks thrown out from an off-centre origin, heavily blurred
/// so they read as soft blooms of colour rather than confetti shapes, gone inside a second.
private struct ConfettiBurst: View {
    let startDelay: Double
    private let shells: [Shell]

    @State private var start = Date()

    init(seed: Int, startDelay: Double) {
        self.startDelay = startDelay
        let palette: [Color] = [workColor, restColor, prepColor, .white]
        shells = (0..<BURST_COUNT).map { i in
            var rnd = LCG(seed + i)
            let sparks = (0..<38).map { _ in
                Spark(angle: rnd.next() * 2 * .pi,
                      speed: 0.30 + rnd.next() * 0.70,
                      radius: 22 + rnd.next() * 46,
                      color: palette[rnd.int(palette.count)])
            }
            return Shell(sparks: sparks,
                         origin: CGPoint(x: rnd.next() * 0.6 - 0.3, y: rnd.next() * 0.5 - 0.25))
        }
    }

    var body: some View {
        TimelineView(.animation) { ctx in
            Canvas { gc, size in
                // The delay lets the finish land first, so the fireworks read as a reward rather
                // than as part of the transition.
                let t = ctx.date.timeIntervalSince(start) - startDelay
                let i = Int(t / SHELL_SEC)
                guard t > 0, i < BURST_COUNT else { return }
                // Compose's LinearOutSlowIn: thrown fast, settling long.
                let p = 1 - pow(1 - (t / SHELL_SEC - Double(i)), 3)
                let maxDist = min(size.width, size.height) * 0.55
                let from = CGPoint(x: size.width * (0.5 + shells[i].origin.x),
                                   y: size.height * (0.5 + shells[i].origin.y))
                let alpha = min(1 - p, 0.6)
                for s in shells[i].sparks {
                    let d = maxDist * s.speed * p
                    let r = s.radius * (1 - 0.45 * p)
                    let c = CGPoint(x: from.x + cos(s.angle) * d, y: from.y + sin(s.angle) * d)
                    gc.fill(
                        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                        with: .color(s.color.opacity(alpha))
                    )
                }
            }
            .blur(radius: 26)
        }
    }
}

/// A fixed-seed generator, so every finish throws the same pattern. It's decoration, and a stable
/// pattern can't randomly land badly — `Int.random` would reroll it on every workout.
private struct LCG {
    private var s: UInt64

    init(_ seed: Int) { s = UInt64(bitPattern: Int64(seed)) &+ 0x9E37_79B9_7F4A_7C15 }

    /// 0..<1. Top bits only: the low bits of an LCG cycle far too obviously.
    mutating func next() -> Double {
        s = s &* 6364136223846793005 &+ 1442695040888963407
        return Double(s >> 33) / Double(1 << 31)
    }

    mutating func int(_ n: Int) -> Int { Int(next() * Double(n)) }
}
