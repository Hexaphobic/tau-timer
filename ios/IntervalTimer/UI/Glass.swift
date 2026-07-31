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
///
/// Declaration order IS the order of the picker, so it's hand-set rather than alphabetical: the
/// strongest and most distinct themes lead, and Mono sits third rather than exiled to the end —
/// it's a deliberate choice, not the leftover at the bottom of the list.
enum Palette: String, CaseIterable, Identifiable {
    case standard = "DEFAULT"
    case vesper = "VESPER"
    // No hue at all. Paired with the Minimal switch this is the plain black-and-white timer; on its
    // own it's a white aura. Work and rest look identical here — that is the whole point, but it
    // does mean the preset list and editor lose their colour coding while it's selected.
    case mono = "MONO"
    case miami = "MIAMI"
    case trance = "TRANCE"
    case grape = "GRAPE"
    case spidey = "SPIDEY"
    case laser = "LASER"
    case tron = "TRON"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .standard: return "Default"
        case .vesper: return "Vesper"
        case .mono: return "Mono"
        case .miami: return "Miami"
        case .trance: return "Trance"
        case .grape: return "Grape"
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
        case .grape: return hex(0xFF8F00)
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
        case .grape: return hex(0xB14EFF)
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
        case .grape: return hex(0xFF4081)
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

/// The +/- button. Every one of these in the app is a stepper, so hold-to-repeat lives here rather
/// than in each caller: press and it fires once, keep holding and it repeats.
///
/// `onStep` is handed a multiplier — 1 normally, 2 once you've held past `DOUBLE_AFTER` — so a long
/// hold tops out at twice the speed and no further. Deliberately a hard ceiling: an escalating
/// tap-streak could reach 6x and would blow straight past the number you were aiming for.
struct GlassCircle: View {
    let glyph: String
    let onStep: (Int) -> Void
    var size: CGFloat = 54

    @State private var timer: Timer?
    @State private var held: TimeInterval = 0
    @State private var pressing = false

    var body: some View {
        Text(glyph)
            .font(.system(size: size * 0.44, weight: .medium))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(glassFill, in: Circle())
            .overlay(Circle().strokeBorder(glassBorder(), lineWidth: 1))
            .contentShape(Circle())
            // minimumDistance 0 so the press registers immediately; a drag off the button still
            // ends the repeat through onEnded.
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in if !pressing { pressing = true; begin() } }
                    .onEnded { _ in end() }
            )
            .onDisappear(perform: end)
    }

    private func begin() {
        onStep(1)
        held = REPEAT_DELAY
        timer = Timer.scheduledTimer(withTimeInterval: REPEAT_DELAY, repeats: false) { _ in
            step()
            timer = Timer.scheduledTimer(withTimeInterval: REPEAT_EVERY, repeats: true) { _ in step() }
        }
    }

    private func step() {
        onStep(held >= DOUBLE_AFTER ? 2 : 1)
        held += REPEAT_EVERY
    }

    private func end() {
        timer?.invalidate()
        timer = nil
        pressing = false
        held = 0
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
/// Shared by the preset editor and the home screen rather than copied, so building a sequence in one
/// puts the same control in the same words in the same place as building one in the other.
struct RepeatAllCard: View {
    let repeatAll: Int
    let onChange: (Int) -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        // 40/44 rather than 44/52: on the narrowest phone the wider stepper left the label too
        // little to sit on one line, and "Repeat everything" folded in half above the subtitle.
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Repeat everything")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.white)
                Text(repeatAll == 1 ? "Plays through once" : "\(repeatAll) times through")
                    .font(.system(size: 12))
                    .foregroundStyle(.white.opacity(0.5))
            }
            Spacer(minLength: 8)
            GlassCircle(glyph: "−", onStep: { m in onChange(max(repeatAll - m, 1)) }, size: 40)
            Text("× \(repeatAll)")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 44)
            GlassCircle(glyph: "+", onStep: { m in onChange(repeatAll + m) }, size: 40)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(glassFill, in: shape)
        .overlay(shape.strokeBorder(glassBorder(), lineWidth: 1))
    }
}
