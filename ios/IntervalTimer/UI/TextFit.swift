import CoreText
import SwiftUI
import UIKit

/// Largest point size at which `text` fits on one line inside `maxW` x `maxH`.
///
/// Measured with the real font rather than estimated per-glyph: guessing widths under-sizes
/// CJK/Hangul, which pushes the text past the edge and lets it stack on itself. Text width scales
/// linearly with point size, so one measurement at a reference size gives the exact ratio.
///
/// Both dimensions come from the INK, not the line box. A line box carries the font's full ascent
/// and descent, and fallback faces for mark-stacking scripts like Arabic reserve enormous vertical
/// room for those marks — so ٥ measures nearly as tall as 五 while drawing a third of the ink, and a
/// height budget applied to the box throttles it to a speck. Ink is what the eye judges as "fills
/// the box". Width has to come from the ink too, not the advance: mixing the two lets a Han clock
/// fit on advance width while its glyphs draw past the screen edge.
func fittedFontSize(
    _ text: String,
    weight: UIFont.Weight,
    monospaced: Bool = false,
    maxW: CGFloat,
    maxH: CGFloat,
    minPt: CGFloat,
    maxPt: CGFloat
) -> CGFloat {
    guard !text.isEmpty, maxW > 0, maxH > 0 else { return minPt }
    let ref: CGFloat = 100
    let font = monospaced
        ? UIFont.monospacedDigitSystemFont(ofSize: ref, weight: weight)
        : UIFont.systemFont(ofSize: ref, weight: weight)
    // CTLine resolves font fallback the same way the renderer will, so scripts the system font
    // doesn't cover are measured in the face that actually draws them.
    let line = CTLineCreateWithAttributedString(
        NSAttributedString(string: text, attributes: [.font: font])
    )
    let ink = CTLineGetBoundsWithOptions(line, .useOpticalBounds)
    guard ink.width > 0, ink.height > 0 else { return minPt }
    let ratio = Swift.min(maxW / ink.width, maxH / ink.height)
    return Swift.min(Swift.max(ref * ratio, minPt), maxPt)
}
