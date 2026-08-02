import SwiftUI
import UIKit
import IntervalTimerCore

/// Full-bleed animated glow over black. `glow` is the phase colour, `progress` 0...1 over the interval.
struct AuraBackground: View {
    let glow: Color
    let progress: Double
    @ObservedObject private var settings = Settings.shared

    var body: some View {
        // Minimal: no bloom, no grain, no shader running at 60fps. Just black, so the only things on
        // screen are the count and the perimeter line ticking down.
        if settings.minimalBg {
            Color.black
        } else {
            ShaderCanvas(animated: true) { size, time in
                ShaderLibrary.aura(
                    .float2(size.width, size.height),
                    .float(Float(time)),
                    .float(Float(min(max(progress, 0), 1))),
                    // The whole composition: the blooms AND the black they sit in. That contrast is
                    // the effect on a screen you're staring at for a minute.
                    .float(1),
                    .color(glow)
                )
            }
        }
    }
}

/// Mid-interval: what a phase looks like for most of the time you're staring at it. The swatch is
/// frozen here rather than at either end, where the glow is still building or already peaked.
private let SWATCH_PROGRESS: Float = 0.5

/// Kept in step with the Kotlin's SWATCH_ZOOM.
private let SWATCH_ZOOM: Float = 0.35

/// The timer's own aura, shrunk to a swatch. Deliberately the same shader the running workout draws,
/// so a theme previews as what GO actually shows instead of as a hand-tuned imitation of it that
/// drifts the first time the real one is touched.
///
/// Frozen, not animated: a grid of these would otherwise run a dozen shaders at 60fps to show drift
/// nobody is watching. Minimal mode is ignored on purpose — honouring it would render nine black
/// rectangles, which is accurate and useless for picking a theme.
///
/// `seed` picks WHICH frame of the drift each swatch is frozen at, fed straight to iTime. The four
/// blooms orbit on periods of roughly 17.5s, 21s, 26s and 32s, and those don't divide into each
/// other, so separated seeds give genuinely different compositions rather than the same picture
/// twice. Left at 0 every swatch is the identical frame, which is what made a grid of them read as
/// one image stamped out repeatedly.
///
/// `zoom` is how much of the composition to show. The default crops to the lit middle, which is what
/// a colour sample wants. Pass 1 where the swatch is big enough for the falloff to read as depth
/// rather than as darkness — the language tiles are five times the area of a theme stripe, and they
/// hold a numeral that the gradient sits behind.
struct AuraSwatch: View {
    let glow: Color
    let seed: Float
    var zoom: Float = SWATCH_ZOOM

    var body: some View {
        ShaderCanvas(animated: false) { size, _ in
            ShaderLibrary.aura(
                .float2(size.width, size.height),
                .float(seed),
                .float(SWATCH_PROGRESS),
                // Cropped to the middle of the composition. At full frame a 35×40 stripe puts its
                // corners out where the blooms have fallen away, and the darkest pixel measured
                // 2–9% of the brightest — a swatch that is mostly showing you black. On the timer
                // that contrast is the effect; here the job is to show a colour, so this takes the
                // lit middle and leaves the vignette to the screen that earns it.
                .float(zoom),
                .color(glow)
            )
        }
    }
}

/// Distant weaving aurora over AMOLED black, in the current palette's colours.
///
/// Deliberately NOT gated on `minimalBg`. Minimal is about the running timer — the screen you
/// actually stare at mid-set. Home, presets and settings keep their aurora; the only way to lose
/// colour here is to pick a palette that hasn't got any.
/// A theme swap repaints it between one frame and the next, so the three colours cross-fade. That is
/// the whole of the transition: the shader is a sum of three coloured curtains, so interpolating what
/// it is handed interpolates what it draws — no second layer, no blend pass, nothing to keep in sync.
///
/// 80ms, linear, matching the Kotlin. Enough to read as a fade rather than a cut without anyone
/// waiting for it; linear because a colour ramp with an ease on it arrives late and draws attention
/// to itself, which is the opposite of the point.
///
/// Done by hand rather than with `.animation`: these colours are shader arguments, not view
/// properties, so nothing interpolates them for us. `ShaderCanvas` already re-evaluates this closure
/// every frame, so the mix only needs a start time and the clock it is handed anyway.
struct HomeBackground: View {
    @ObservedObject private var settings = Settings.shared

    @State private var from: (work: Color, prep: Color, rest: Color)?
    @State private var fadeStart: TimeInterval = 0
    /// The clock `ShaderCanvas` hands the closure, kept so `onChange` can stamp a start from it.
    @State private var now: TimeInterval = 0

    private static let FADE: TimeInterval = 0.08

    var body: some View {
        ShaderCanvas(animated: true) { size, time in
            now = time
            let k = from == nil ? 1 : min(max((time - fadeStart) / Self.FADE, 0), 1)
            return ShaderLibrary.homeAurora(
                .float2(size.width, size.height),
                .float(Float(time)),
                .color(mix(from?.work, workColor, k)),
                .color(mix(from?.prep, prepColor, k)),
                .color(mix(from?.rest, restColor, k))
            )
        }
        .onChange(of: settings.palette) { old, _ in
            from = (old.work, old.prep, old.rest)
            fadeStart = now
        }
    }

    /// SwiftUI `Color` has no arithmetic, so the mix goes through the resolved components. sRGB, like
    /// everything else at this boundary — see the note in Shaders.metal about not transcoding.
    private func mix(_ a: Color?, _ b: Color, _ k: Double) -> Color {
        guard let a, k < 1 else { return b }
        let x = UIColor(a).cgColor.components ?? [0, 0, 0, 1]
        let y = UIColor(b).cgColor.components ?? [0, 0, 0, 1]
        func c(_ i: Int) -> Double { Double(x[min(i, x.count - 1)]) * (1 - k) + Double(y[min(i, y.count - 1)]) * k }
        return Color(.sRGB, red: c(0), green: c(1), blue: c(2), opacity: 1)
    }
}

/// A rectangle painted entirely by a shader, optionally re-rendered every frame.
///
/// One place that owns the clock, so the three shader views above don't each carry their own
/// `TimelineView` + start-date bookkeeping.
private struct ShaderCanvas: View {
    let animated: Bool
    let shader: (CGSize, TimeInterval) -> Shader

    @State private var start = Date()

    /// Reduce Motion falls into the same branch the swatches use: one frozen frame, not a blank
    /// screen. These shaders ARE the backgrounds — honouring the setting by drawing black would read
    /// as the app broken, and the timer's own aura would stop answering to `progress` with it.
    ///
    /// Every animated shader in the app comes through here, so this is the only place it's needed.
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            if animated && !reduceMotion {
                TimelineView(.animation) { ctx in
                    Rectangle()
                        .fill(.black)
                        .colorEffect(shader(geo.size, ctx.date.timeIntervalSince(start)))
                }
            } else {
                Rectangle()
                    .fill(.black)
                    .colorEffect(shader(geo.size, 0))
            }
        }
    }
}

/// Perimeter progress that originates at the left-middle and right-middle of the vertical sides and
/// splits outward: four bright tips run up/down the sides, around the corners, and along the top and
/// bottom edges toward their centres. `remaining` (1...0) is how far each arm has grown — the full
/// perimeter at the start, retreating to the two side mid-points as time runs out.
struct SplitProgress: View {
    let remaining: Double
    let color: Color

    var body: some View {
        ZStack {
            arm(horiz: -1)
            arm(horiz: 1)
        }
    }

    private func arm(horiz: CGFloat) -> some View {
        let half = min(max(remaining, 0), 1) / 2
        // Symmetric about the horizontal centre line, so the path's length-midpoint lands exactly on
        // that side's mid-point — which is what lets a plain trim grow the arm outward in both
        // directions at once.
        return SidePath(horiz: horiz)
            .trim(from: 0.5 - half, to: 0.5 + half)
            .stroke(color.opacity(0.20), style: StrokeStyle(lineWidth: 18, lineCap: .round))
            .overlay(
                SidePath(horiz: horiz)
                    .trim(from: 0.5 - half, to: 0.5 + half)
                    .stroke(color, style: StrokeStyle(lineWidth: 6, lineCap: .round))
            )
            .opacity(half <= 0 ? 0 : 1)
    }
}

/// The display's own corner radius, so the perimeter arms can run parallel to the glass rather than
/// cut across it. The Android build hard-codes 40dp and gets away with it because the Flip's corners
/// are tighter than that; every iPhone since the 14 Pro is rounder, and the arms were being clipped.
///
/// iOS has no public API for this — `_displayCornerRadius` is private, and shipping a private
/// selector is an App Store risk — so it is a table keyed on the screen's point size. Where two
/// devices share a size and differ (375x812 is 39 on an X and 44 on a 13 mini) the larger wins, and
/// an unrecognised size gets the largest radius currently shipping: overshooting only tucks the arm
/// further inside the glass, while undershooting runs it off the corner, which is the bug being
/// fixed. Home-button devices are genuinely square and get 0.
private let displayCornerRadius: CGFloat = {
    let b = UIScreen.main.bounds.size
    switch (min(b.width, b.height), max(b.width, b.height)) {
    case (320, 568), (375, 667), (414, 736): return 0       // SE 1-3, 6s-8, Plus
    case (375, 812): return 39                              // X, Xs, 11 Pro
    case (360, 780): return 44                              // 12 mini, 13 mini
    case (414, 896): return 41.5                            // Xr, Xs Max, 11, 11 Pro Max
    case (390, 844): return 55                              // 12-14 are 47.33; 16e/17e are 55
    case (393, 852): return 55                              // 14 Pro, 15, 15 Pro, 16
    case (428, 926): return 53.33                           // 12/13 Pro Max, 14 Plus
    case (430, 932): return 55                              // 14 Pro Max, 15 Plus/Pro Max, 16 Plus
    default: return 62                                      // 16/17 Pro, Air, Pro Max
    }
}()

/// One path per SIDE: top-edge centre, round the corner, down the whole side, round the bottom
/// corner, back to the bottom-edge centre.
///
/// Sliced by side rather than by half — which is the fix for the dot that used to sit at each side's
/// middle. Four arms sliced top/bottom put two segment ENDS on that point, and a round cap on each
/// drew two half-discs back to back: a permanent circle. One stroke per side runs straight through
/// the mid-point, so there is no cap there to draw.
private struct SidePath: Shape {
    /// -1 for the left side, 1 for the right.
    let horiz: CGFloat

    func path(in rect: CGRect) -> Path {
        // The 6pt inset under an 18pt glow is quoted as fact by the timer screen's layout maths, so
        // it stays fixed. The corner radius is the display's own less that inset, which is what makes
        // a rounded rect concentric with the one it sits inside: the line then runs exactly 6pt from
        // the glass the whole way round instead of only down the straight sides.
        let i: CGFloat = 6
        let r = max(displayCornerRadius - i, 0)
        let cx = rect.width / 2
        let top = i
        let bottom = rect.height - i
        let edgeX = horiz > 0 ? rect.width - i : i

        var p = Path()
        p.move(to: CGPoint(x: cx, y: top))
        p.addLine(to: CGPoint(x: edgeX - horiz * r, y: top))
        p.addQuadCurve(to: CGPoint(x: edgeX, y: top + r), control: CGPoint(x: edgeX, y: top))
        p.addLine(to: CGPoint(x: edgeX, y: bottom - r))
        p.addQuadCurve(to: CGPoint(x: edgeX - horiz * r, y: bottom), control: CGPoint(x: edgeX, y: bottom))
        p.addLine(to: CGPoint(x: cx, y: bottom))
        return p
    }
}

/// Draws `number` as a single Cistercian glyph, scaled to fill the view's bounds.
struct CistercianNumeral: View {
    let number: Int
    var strokeWidth: CGFloat = 4

    var body: some View {
        Canvas { ctx, size in
            // Glyph is 2 wide x 2 tall in unit space; inset by half a stroke so ends aren't clipped.
            let scale = min(size.width, size.height) / 2 - strokeWidth / 2
            let cx = size.width / 2
            let cy = size.height / 2
            var path = Path()
            for s in cistercian(number) {
                path.move(to: CGPoint(x: cx + s.x1 * scale, y: cy + s.y1 * scale))
                path.addLine(to: CGPoint(x: cx + s.x2 * scale, y: cy + s.y2 * scale))
            }
            ctx.stroke(path, with: .color(.white),
                       style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round))
        }
    }
}
