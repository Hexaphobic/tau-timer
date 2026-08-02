import SwiftUI

// Every glass surface in the app reads from these, so contrast is tuned here rather than per screen.
let glassFill = Color.white.opacity(0.16)

func glassBorder() -> LinearGradient {
    LinearGradient(colors: [.white.opacity(0.60), .white.opacity(0.14)],
                   startPoint: .top, endPoint: .bottom)
}

private func hex(_ v: UInt32) -> Color {
    Color(.sRGB,
          red: Double((v >> 16) & 0xFF) / 255,
          green: Double((v >> 8) & 0xFF) / 255,
          blue: Double(v & 0xFF) / 255,
          opacity: 1)
}

/// Selectable colour palettes, named after the monkeytype themes they're taken from.
///
/// Every palette needs three plainly different hues. Work, rest and prepare have to be tellable
/// apart at a glance, mid-set, sweating, from arm's length — so a gorgeous monochrome theme is not
/// an option here however well it reads on a typing test.
///
/// They also want one channel near zero. These colours are painted as a full-screen bloom, and a
/// pale one has no dark channel to keep the background black with.
enum Palette: String, CaseIterable, Identifiable {
    // Case order IS the order of the picker, so it's hand-set rather than alphabetical: the two
    // plainest lead, the loud ones follow, and Vesper — the quietest — sits near the end where you go
    // looking for it rather than land on it. Mono is second rather than exiled to the bottom: it's a
    // deliberate choice, not the leftover.
    case standard = "DEFAULT"
    // No hue at all. Paired with the Minimal switch this is the plain black-and-white timer; on its
    // own it's a white aura. Work and rest look identical here — that is the whole point, but it
    // does mean the preset list and editor lose their colour coding while it's selected.
    case mono = "MONO"
    case spidey = "SPIDEY"
    case miami = "MIAMI"
    case trance = "TRANCE"
    case laser = "LASER"
    case vesper = "VESPER"
    case tron = "TRON"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .standard: return "Default"
        case .vesper: return "Vesper"
        case .mono: return "Mono"
        case .miami: return "Miami"
        case .trance: return "Trance"
        case .spidey: return "Spidey"
        case .laser: return "Laser"
        case .tron: return "Tron"
        }
    }

    var work: Color {
        switch self {
        case .standard: return hex(0x22E06A)
        case .vesper: return hex(0x99FFE4)
        case .mono: return .white
        case .miami: return hex(0xFF2D8F)
        case .trance: return hex(0x02D3B0)
        case .spidey: return hex(0xE23636)
        case .laser: return hex(0xA8D400)
        case .tron: return hex(0xFF6600)
        }
    }

    var rest: Color {
        switch self {
        case .standard: return hex(0x38BDF8)
        case .vesper: return hex(0xFFC799)
        case .mono: return .white
        case .miami: return hex(0x05DFD7)
        case .trance: return hex(0x6C8BE8)
        case .spidey: return hex(0x0476F2)
        case .laser: return hex(0x22C9DC)
        case .tron: return hex(0x00D4FF)
        }
    }

    var prep: Color {
        switch self {
        case .standard: return hex(0x8B5CF6)
        case .vesper: return hex(0xFF8080)
        case .mono: return .white
        case .miami: return hex(0xFFC400)
        case .trance: return hex(0xE51376)
        case .spidey: return hex(0xFFD400)
        case .laser: return hex(0xFF3D7F)
        case .tron: return hex(0xF0E800)
        }
    }
}

// Phase colours — one source for the timer glow, the home aurora, the editor and the legend, so
// they can't drift. Computed, not stored: they read Settings.palette, so picking a new theme
// redraws every one of them (any view that shows them already observes Settings).
var workColor: Color { Settings.shared.palette.work }
var restColor: Color { Settings.shared.palette.rest }
var prepColor: Color { Settings.shared.palette.prep }

// Fixed across themes. Done is deliberately colourless — the workout is over, nothing is signalled
// — and destructive red is a safety signal, not decoration, so no palette gets to repaint it.
let doneGray = hex(0x9CA3AF)

/// Destructive actions: ending a workout, deleting a preset.
let dangerRed = hex(0xFF4D4D)

// MARK: - Controls

struct GlassPill: View {
    let text: String
    let action: () -> Void
    /// Stretch to the container's width. Off by default so an inline pill ("Start ▶") hugs its label.
    var wide: Bool = false
    var big: Bool = false
    var enabled: Bool = true

    var body: some View {
        Button(action: { if enabled { action() } }) {
            Text(text)
                .font(.system(size: big ? 22 : 16, weight: .bold))
                .foregroundStyle(.white.opacity(enabled ? 1 : 0.4))
                // A pill's label is always one line — a crowded row must not fold "Start ▶" in half.
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .frame(maxWidth: wide ? .infinity : nil)
                .padding(.horizontal, 24)
                .padding(.vertical, big ? 18 : 12)
                .background(glassFill, in: Capsule())
                .overlay(Capsule().strokeBorder(glassBorder(), lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Press-and-hold before a stepper starts repeating — long enough that a normal tap is just a tap.
private let REPEAT_DELAY: TimeInterval = 0.350
/// Gap between auto-repeats. Constant: the hold speeds up by taking bigger steps, not faster ones.
private let REPEAT_EVERY: TimeInterval = 0.090
/// How long you hold before each repeat counts double — 5s steps become 10s, i.e. 2x.
private let DOUBLE_AFTER: TimeInterval = 1.0

/// The point size every glyph's *layout metrics* are taken at, whatever the circle's diameter —
/// see the note in `body`. The largest circle in the app, so every call site scales down, never up.
private let BASE_GLYPH: CGFloat = 54

/// The +/- button. Every one of these in the app is a stepper, so hold-to-repeat lives here rather
/// than in each caller: tap and it steps once on lift, keep holding and it repeats.
///
/// `onStep` is handed a multiplier — 1 normally, 2 once you've held past `DOUBLE_AFTER` — so a long
/// hold tops out at twice the speed and no further. Deliberately a hard ceiling: an escalating
/// tap-streak could reach 6x and would blow straight past the number you were aiming for.
struct GlassCircle: View {
    let glyph: String
    let onStep: (Int) -> Void
    var size: CGFloat = 54

    /// Reference box for the repeat callback. GlassCircle is a struct, so the timer closure
    /// captures a frozen copy of it — including whatever `onStep` was current when the press began.
    /// The editor's steppers close over `let` snapshots (`iv`, `block`), so repeating through the
    /// captured copy re-applies the same delta to a stale base and the number sticks. @State's
    /// storage box is shared with the captured copy, so routing every step through this box always
    /// reaches the closure from the latest body evaluation.
    private final class StepSink {
        var step: (Int) -> Void = { _ in }
    }

    @State private var timer: Timer?
    @State private var held: TimeInterval = 0
    @State private var pressing = false
    @State private var dead = false
    @State private var fired = false
    @State private var sink = StepSink()

    var body: some View {
        // Re-point the sink at the freshest closure every render; begin()/step() only call the sink.
        sink.step = onStep
        return Text(glyph)
            // Pin the font size and carry the diameter on the scale instead. `.frame` below centres
            // the Text's LINE box, but the ink of − and + sits below that box's centre, and SF Pro's
            // optical-size axis moves it by a different fraction at every point size — measured at
            // 3x, 2.97% / 3.41% / 2.57% of the diameter at 54 / 50 / 40, not even monotonic. So a
            // size that animates dragged the glyph around a small arc inside its own circle, and
            // through a transition that *moves* the row as well it fell as far as the bottom rim
            // (measured: 15.3pt). Pinning makes the error one fixed fraction, which one nudge below
            // cancels at every size. It costs no sharpness: only the metrics are pinned here, the
            // outline is still resolved at the effective size (measured — the 50%-ink width runs
            // wider than a uniform scale of the 54pt glyph at 40 and 36, which a scaled raster
            // could not do). Same trade as the Rounds number at HomeView.swift:196.
            .font(.system(size: BASE_GLYPH * 0.44, weight: .medium))
            .foregroundStyle(.white)
            // Lifts the ink to the circle's centre: measured 4.803px low at size 54 on a 3x screen.
            // Inside the scale, so it shrinks with the glyph and holds at 36…54.
            //
            // Quantised, so don't bother re-tuning it: SwiftUI rounds a Text's offset to a whole
            // device pixel, and everything from about −1.500 to −1.833 delivers the same −5px. That
            // leaves 0.2px of over-correction, which is the floor for this approach. There is no x
            // term for the same reason — the 0.473px horizontal error rounds to nothing, and − and
            // + want different corrections anyway, so no one constant nulls both. Sub-pixel would
            // mean nudging the Circle instead, which isn't snapped. See PUNCHLIST §51.
            .offset(y: -1.601)
            .scaleEffect(size / BASE_GLYPH)
            .frame(width: size, height: size)
            .background(glassFill, in: Circle())
            .overlay(Circle().strokeBorder(glassBorder(), lineWidth: 1))
            .contentShape(Circle())
            // A minimumDistance-0 drag claims the touch outright, so the enclosing ScrollView's pan
            // never gets a flick that starts on a stepper — exclusive vs simultaneous makes no
            // difference (measured), it's the same SwiftUI behaviour ReorderState.handleGesture
            // relies on deliberately. What this layer CAN guarantee is that the stolen flick edits
            // nothing: no step fires on touch-down, so an aborted gesture leaves the value alone.
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        guard !dead else { return }
                        // 24pt: a genuine still hold was measured drifting ~14pt of thumb roll,
                        // and a real flick travels far further than this before lift.
                        if max(abs(v.translation.width), abs(v.translation.height)) > 24 {
                            dead = true
                            end()
                            return
                        }
                        if !pressing { pressing = true; begin() }
                    }
                    .onEnded { _ in
                        // A tap commits on lift; a hold's first step was the timer's at
                        // REPEAT_DELAY, and a dead gesture commits nothing.
                        let tap = pressing && !dead && !fired
                        end()
                        dead = false
                        if tap { sink.step(1) }
                    }
            )
            .onDisappear(perform: end)
            // Nothing here for VoiceOver to hold: a bare "+" says nothing about what it adds to, and
            // the DragGesture above is invisible to the accessibility layer, so the circle could
            // never be activated anyway. The number it flanks carries the whole control instead —
            // see `stepperSemantics`.
            .accessibilityHidden(true)
    }

    private func begin() {
        // Schedules only — stepping on touch-down is what let a cancelled gesture edit the value.
        held = REPEAT_DELAY
        timer = Timer.scheduledTimer(withTimeInterval: REPEAT_DELAY, repeats: false) { _ in
            step()
            timer = Timer.scheduledTimer(withTimeInterval: REPEAT_EVERY, repeats: true) { _ in step() }
        }
    }

    private func step() {
        fired = true
        sink.step(held >= DOUBLE_AFTER ? 2 : 1)
        held += REPEAT_EVERY
    }

    private func end() {
        timer?.invalidate()
        timer = nil
        pressing = false
        fired = false
        held = 0
    }
}

extension View {
    /// VoiceOver's entire view of a stepper: the number is the control, and swipe up/down is what
    /// moves it. The circles either side are hidden (see `GlassCircle`), so this is the only element
    /// left to say what the value is or to change it.
    ///
    /// Merge the whole row into one element only where nothing in it needs an element of its own; put
    /// it on the number alone where the row also holds a grip or a ✕, since merging those away would
    /// take the grip's Move up / Move down with them. A double-tap-to-reset on the number survives a
    /// merge either way — VoiceOver can't perform it as a gesture, so both kinds of row re-offer it
    /// as `.accessibilityAction(named: "Reset")`.
    ///
    /// `down`/`up` are the circles' own `onStep` closures, handed the same 1 a single tap sends, so
    /// the floors and clamps can't drift between adjusting a value and tapping it.
    func stepperSemantics(_ label: String, _ value: String,
                          down: @escaping (Int) -> Void, up: @escaping (Int) -> Void) -> some View {
        accessibilityLabel(label)
            .accessibilityValue(value)
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: up(1)
                case .decrement: down(1)
                @unknown default: break
                }
            }
    }
}

/// The small ✕ that removes whatever it sits on: a home section, an interval row, a name field.
struct CloseX: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text("✕")
                .font(.system(size: 15))
                .foregroundStyle(.white.opacity(0.55))
                .frame(width: 32, height: 32)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

/// A short, self-dismissing explanation. The editor refuses a few edits (a rest that would land on
/// another rest); refusing silently would just read as a broken button, so it says why.
struct NoticePill: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundStyle(.white.opacity(0.92))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 18)
            .padding(.vertical, 11)
            .background(Color.black.opacity(0.75), in: Capsule())
            .overlay(Capsule().strokeBorder(glassBorder(), lineWidth: 1))
    }
}

/// The universal "grab me" mark. Drawn rather than typed — no font is guaranteed to have it.
struct GripDots: View {
    var alpha: Double = 0.55
    var height: CGFloat = 20

    var body: some View {
        Canvas { ctx, size in
            let r = size.height / 12
            for x in [size.width * 0.25, size.width * 0.75] {
                for y in [size.height * 0.16, size.height * 0.5, size.height * 0.84] {
                    ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                             with: .color(.white.opacity(alpha)))
                }
            }
        }
        .frame(width: height * 0.6, height: height)
    }
}

/// A plain text control, used for the corner navigation.
/// The only way off a secondary screen: a small floating disc in the top-left corner, with the
/// screen's own content scrolling underneath it.
///
/// No title beside it. A pinned bar wide enough to hold a word is a bar that has to clip whatever
/// scrolls past it, and the screen you are looking at is its own label.
struct BackPill: View {
    let onBack: () -> Void

    var body: some View {
        Button(action: onBack) {
            // The chevron's own side bearings sit it right of centre; nudge it back so the glyph,
            // not its box, is what looks centred.
            Text("‹")
                .font(.system(size: 28, weight: .regular))
                .foregroundStyle(.white.opacity(0.9))
                .offset(x: -1, y: -3)
                .frame(width: 44, height: 44)
                .background(.ultraThinMaterial, in: Circle())
                .overlay(Circle().strokeBorder(glassBorder(), lineWidth: 1))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Back")
    }
}

struct PlainTextButton: View {
    let text: String
    let action: () -> Void
    var size: CGFloat = 15

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: size))
                .foregroundStyle(.white.opacity(0.85))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// The outer ×N: the whole sequence, top to bottom, that many times.
///
/// Presented as the home's Rounds control, because it is the same number — and the home says what
/// that number governs by *where it sits*, directly under the stack of cards, rather than in a
/// sentence. So the sentence is gone: "Repeat everything" over "Plays through once", in a glass card
/// of its own, was the screen explaining in words what the layout can say by itself.
struct RoundsRow: View {
    let repeatAll: Int
    let onChange: (Int) -> Void

    var body: some View {
        let down: (Int) -> Void = { m in onChange(max(repeatAll - m, 1)) }
        let up: (Int) -> Void = { m in onChange(repeatAll + m) }
        // The home's resting numbers — 18pt label, 12pt gap, 50pt circles, a 30pt count in a 78pt
        // box. If those move on the home, they move here.
        return HStack(spacing: 0) {
            Text("Rounds")
                .font(.system(size: 18))
                .foregroundStyle(.white)
            Spacer().frame(width: 12)
            GlassCircle(glyph: "−", onStep: down, size: 50)
            Text("\(repeatAll)")
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 78)
            GlassCircle(glyph: "+", onStep: up, size: 50)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
        .padding(.bottom, 2)
        // Nothing in the row is a control but the stepper, so the row is the control.
        .accessibilityElement(children: .ignore)
        .stepperSemantics("Rounds", "\(repeatAll) times", down: down, up: up)
    }
}
