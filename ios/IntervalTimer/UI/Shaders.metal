#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>

using namespace metal;

// Ported from the Android build's AGSL (ui/Aura.kt). AGSL and SwiftUI's Metal shaders are the same
// shape of thing — position in, colour out — so this is a transcription, not a redesign. The
// constants are the tuned ones and should stay in step with the Kotlin if either is touched.

static float hash(float2 p) {
    p = fract(p * float2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

// AGSL/SkSL accepts `smoothstep(hi, lo, x)` as a descending ramp. Metal does NOT: `smoothstep` is
// documented as undefined when edge0 >= edge1, and with fast math it returns 1 everywhere — which
// turned both shaders into a flat wash of colour instead of blooms over black. This is the same
// curve written the way Metal defines it.
static float falloff(float edge, float x) {
    return 1.0 - smoothstep(0.0, edge, x);
}

// No colour-space transcode at the boundary, and that is deliberate — an earlier version of this
// file encoded going in and decoded coming out, on the theory that SwiftUI works in linear extended
// sRGB while AGSL works sRGB-encoded. It doesn't: `.colorEffect` hands a shader its colours
// sRGB-encoded and reads back what it returns the same way, exactly as AGSL does.
//
// Measured, not reasoned: the theme swatches are the same frozen shader with the same seed on both
// platforms, so the same input must give the same pixel. The Default palette's prepare swatch is
// (143, 121, 176) on a Flip 7 over adb, against (147, 121, 181) predicted by running these numbers
// through untouched. With the transcode in, iOS put (96, 83, 116) on screen against (99, 86, 119)
// predicted for the double-encoded path — which is the whole of the "everything looks darker".
// Timer: near-black canvas with soft, drifting, phase-coloured glow blooms + animated film grain.
// Deep blacks (pow), wide/blurry blooms, movement. The colour IS light, not a fill.
[[ stitchable ]] half4 aura(float2 fragCoord, half4 _unused,
                            float2 iResolution, float iTime, float iProgress, half4 glow) {
    float2 uv = fragCoord / iResolution;
    float2 p = uv - 0.5;
    p.x *= iResolution.x / iResolution.y;
    float t = iTime * 0.6;
    float prog = 0.62 + 0.38 * iProgress;   // starts bright; the glow still builds toward the boundary

    float a = 0.0;
    a += falloff(0.95, length(p - float2(0.15 * sin(t * 0.5), 0.05 + 0.06 * cos(t * 0.4)))) * 0.9;
    a += falloff(0.85, length(p - float2(0.30 * sin(t * 0.33), -0.34))) * 0.5;
    a += falloff(0.90, length(p - float2(-0.30 + 0.10 * cos(t * 0.5), 0.33))) * 0.5;
    a += falloff(0.85, length(p - float2(0.33, 0.36 + 0.05 * sin(t * 0.6)))) * 0.4;
    a = pow(a, 1.4) * prog;                 // pow deepens the black between blooms

    float3 col = float3(glow.rgb) * a;
    col = col / (col + 0.65);               // tone-map: keep colour, don't blow to white

    float g = hash(fragCoord + fract(iTime) * 100.0) - 0.5;
    col += g * 0.05;                        // analog grain
    return half4(half3(max(col, 0.0)), 1.0);
}

// Home: AMOLED black with a distant, blurred aurora — soft colour curtains that weave and drift,
// mostly black. Not a full gradient fill.
static float curtain(float2 uv, float iTime, float base, float amp, float freq, float speed) {
    float x = base + amp * sin(uv.y * freq + iTime * speed)
                   + amp * 0.5 * cos(uv.y * freq * 2.1 - iTime * speed * 0.6);
    float body = falloff(0.34, abs(uv.x - x));                    // wide + very soft/blurred
    float vert = 1.0 - smoothstep(0.05, 1.05, uv.y);              // brighter toward the top (distant sky)
    return body * vert;
}

[[ stitchable ]] half4 homeAurora(float2 fragCoord, half4 _unused,
                                  float2 iResolution, float iTime,
                                  half4 cWork, half4 cPrep, half4 cRest) {
    float2 uv = fragCoord / iResolution;
    float3 col = float3(0.0);   // AMOLED black
    // Three faint, blurred curtains — a subtle background feature, not the subject. They're the
    // palette's own three phase colours, so the home screen always previews the theme you picked.
    col += float3(cWork.rgb) * (curtain(uv, iTime, 0.30, 0.16, 4.5, 0.40) * 0.32);
    col += float3(cPrep.rgb) * (curtain(uv, iTime, 0.60, 0.18, 4.0, 0.30) * 0.28);
    col += float3(cRest.rgb) * (curtain(uv, iTime, 0.82, 0.14, 5.0, 0.50) * 0.24);
    col *= 1.2;                             // 20% brighter than the original curtains
    col = col / (col + 1.6);                // much dimmer, deep black base
    float g = hash(fragCoord + fract(iTime) * 100.0) - 0.5;
    col += g * 0.012;
    return half4(half3(max(col, 0.0)), 1.0);
}
