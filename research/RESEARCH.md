# Interval Timer — Build-Ready Research Report

**Target:** Native Android (Kotlin + Jetpack Compose), single device = Samsung Galaxy Z Flip 7 (2025). Sideloaded APK, no accounts, all data local.
**Platform:** Android 16 / One UI 8 = **API level 36**. Set `compileSdk = targetSdk = 36`; `minSdk 36` is fine for a single-device sideload (34 is the hard floor for the mandatory FGS-type APIs). Verified: Flip 7 shipped July 2025 on Android 16 / One UI 8 (first devices to do so). ([gsmarena.com](https://www.gsmarena.com/samsung_galaxy_z_flip7-13712.php), [en.wikipedia.org/wiki/Samsung_Galaxy_Z_Flip_7](https://en.wikipedia.org/wiki/Samsung_Galaxy_Z_Flip_7))

> **Correction applied:** One UI 8.5 was still in *beta* around May 2026 (stable rollout delayed) and is still Android-16-based, so it introduces **no new API surface** for this app. All APIs below are available on the shipped Android 16 base. ([sammobile.com](https://www.sammobile.com/news/third-one-ui-8-5-beta-update-galaxy-z-fold-7-flip-7-available/))

A note on the verified evidence base: the app-store review quotes cited in the UX research (Gymboss "stopped beeping when screen off", Seconds "fell behind after multiple intervals", etc.) come mostly from SEO roundups and review-scraper sites and could not be independently verified. The **underlying phenomena** — screen-off beep failure, quiet beeps under music, end-of-session drift, accidental-tap pausing — are real, cross-corroborated, and internally consistent. Treat specific quotes as illustrative, not as verified fact; the directional conclusions stand.

---

## 1. Interval-Timer UX & Glanceability

The category's two make-or-break failure modes, in order of damage:

1. **The timer keeps running but stops beeping when the screen is off / app is backgrounded.** This is the single most trust-destroying complaint and the direct reason the spec mandates a foreground service. Audio/beep scheduling must live in the service, decoupled from the Compose/Activity lifecycle, and must be tested with the screen *fully off*, the app backgrounded, and another app foregrounded. (See §5–6 for the wake-lock requirement that actually makes this work — the FGS alone is not sufficient.)
2. **Beeps too quiet over music, or ducking that misbehaves** — buried under gym bass, or ducking that fires when it shouldn't / never restores. (See §6.)

Design rules that follow the best apps in the market:

- **Phase IS the background color.** The most-praised glanceability technique is making the whole screen the current phase color so work-vs-rest is read from peripheral vision without parsing text. The animated green (work) / blue (rest) aura already delivers this. **Treat color as the primary phase signal; the "Work"/"Rest" word is redundant reinforcement.** This is exactly what lets the multilingual label be unreadable to a bystander without hurting the user — color already carries the meaning. ([intervaltimer.com/app](https://www.intervaltimer.com/app), [boxinginsider.com/timer](https://boxinginsider.com/timer))
- **One giant number dominates.** Target legibility from ~2 m with the phone flat on the floor (the real HIIT viewing distance). The countdown number should fill ~40–60% of vertical space, bold, **tabular/monospaced figures so digits don't reflow**, high contrast against the aura. Verify light digits clear **WCAG 4.5:1** contrast against the *brightest* point of the green gradient; add a subtle scrim/shadow behind the number if the aura washes it out. ([medium.com/@penguinchilli](https://medium.com/@penguinchilli/ui-design-time2rest-app-66321d75ff55), [yaytimer.com](https://yaytimer.com/))
- **Dual progress read.** Two questions one indicator can't answer: *how much of THIS interval is left* (the perimeter stroke, depleting and resetting each interval) and *where am I* (a small, always-visible discrete round counter like "3/8"). Don't make one element do both. ([medium.com/@penguinchilli](https://medium.com/@penguinchilli/ui-design-time2rest-app-66321d75ff55))
- **Tap-to-pause opens a MENU, never a one-tap end.** Accidental pausing is a documented, high-frustration failure mode (phone on the floor, reached over during a set). The spec's design — tap pauses + reveals a Resume / End menu — is correct precisely because the tap only opens a menu. **"End workout" must require a second deliberate action** and be positioned/styled so a stray floor-reach tap can't land on it. An accidental pause is recoverable (Resume is right there); End must be unmistakable. ([discussions.apple.com](https://discussions.apple.com/thread/254280768))
- **Timing drift shows up only over long sessions** — it won't appear in a 2-interval manual test. See §5 for the absolute-clock fix; leave a self-check that a synthetic N-interval run hits each boundary within a few ms.
- **Sequence editor (+ button):** the concrete failure to avoid is Seconds' inability to insert between two existing intervals (copy/paste only). Support from day one: **append, insert-between-any-two, tap-to-edit inline (no deep modal), drag-to-reorder, duplicate-a-row, and undoable delete.** Insert-between + duplicate fix the two things reviewers hate/love about Seconds' editor. Note: Jetpack Compose still has **no first-party drag-to-reorder for LazyColumn** — budget a small helper (`sh.calvin.reorderable`) or a manual `pointerInput`/`detectDragGestures` implementation. ([kendoesanalytics.github.io](https://kendoesanalytics.github.io/KenDoesData/secondspro.html), [yaytimer.com](https://yaytimer.com/))
- **Countdown cue convention validates the spec exactly.** A "prepare"/"get ready" lead-in with a 3-2-1-GO run-in is standard; boxing/round timers add an *earlier distinct warning* (the 10-s clapper) before a round ends, reinforced by turning the screen red. So: 5 s get-ready lead-in before the first interval and at every transition, a **distinct** warning tone at 5 s, silent 4 s, plain ticks at 3-2-1, and a **distinct** GO tone at 0. The 5 s tone and GO tone must be *timbrally distinct* from the plain ticks so they're identifiable without looking. Reinforce the final transition **visually** (aura brightening to peak green at GO) as the modern equivalent of the red-screen/bell — a non-audio channel for when music is loud. The "nothing at 4 s" gap reads cleanly as *early warning at 5, then run-in at 3-2-1* — just make the 5 s tone clearly distinct so the gap doesn't read as a dropped beep. ([forestfocustimer.com](https://www.forestfocustimer.com/interval-timer/), [boxinginsider.com/timer](https://boxinginsider.com/timer))
- **Screen-wake ergonomics:** because the FGS guarantees beeps regardless of screen state, keeping the screen on is optional polish, not correctness. If you keep it on for glanceability, dim after ~10 s and restore on touch (battery-respectful) — but disambiguate brighten-touch from the deliberate pause tap, or accept that waking also pauses (arguably fine, the user is now interacting). ([yaytimer.com](https://yaytimer.com/))
- **Cover screen: strip to two essentials** — the single giant number + the phase-color aura. Drop labels, next-interval peek, fine progress. Biggest-number-possible is the whole job on that display. (Geometry in §4.)

> **Correction applied — the "bystander can't read it" effect has a content gap.** The recommendation cannot lean on *digits* for intrigue across all 13 languages. **English, Spanish, French, German, Portuguese, Russian, Japanese, and Korean all render ordinary Western digits 0–9** for a countdown (Russian has no distinct modern digit glyphs; ja/ko use Western digits in real digital clocks). Only **5 scripts** have genuinely distinct digit glyphs: Han/Chinese, Devanagari/Hindi, Arabic-Indic, Bengali, Thai. So for ~8 of 13 languages the big number looks identical to English. **Drive per-language differentiation primarily off the translated Work/Rest label and word mode**, not digits. Make word mode prominent (or default) for the Latin-script + Russian + ja/ko locales. (Full per-language guidance in §7.)

---

## 2. Visual Design Language

**Aura gradient.** Deep → bright across each interval, green for work, blue for rest, slowly flowing and shifting. Drive it with a `0..1` interval-progress uniform (deep at interval start → bright at end) plus a slow time uniform for the flow. Cross-fade the green and blue palettes on work/rest transitions with `animateColorAsState`. Suggested ramps (tune on device against the WCAG contrast check for the overlaid digits):

- **Work (green):** deep `#052E16` → mid `#15803D` → bright `#22C55E` (peak, at GO). A cool-to-vivid green ramp reads as building intensity.
- **Rest (blue):** deep `#0C1E3A` → mid `#1E4E8C` → calm-bright `#3B82F6`. Keep rest visibly cooler and lower-energy than work so the phase switch is unmistakable in peripheral vision.

These are starting points — the load-bearing constraint is that the brightest work green still clears 4.5:1 against the countdown text (add a radial scrim behind the number if not).

**Liquid glass / frosted surfaces** (pause menu floating over the moving aura). This is the single most-requested visual and the one most often built wrong — see §3 for the critical `Modifier.blur` caveat. Design intent: a translucent surface with a hairline top-edge white gradient border, ~8–16% white tint, sitting over a blurred copy of the aura. Budget explicitly for either a backdrop-blur library (Haze) or the dependency-free re-drawn-shader approach; do **not** assume the stock blur modifier delivers it.

**Perimeter progress stroke.** A rounded-rectangle stroke tracing the display's *real* corners (not a circle), depleting over the current interval and resetting each interval. Add a soft glow. Route it around the camera cutout. Corner radius and cutout must be **read at runtime** (§3–4), never hardcoded — they differ between the inner display and the cover FlexWindow.

**Typography — a build blocker the generic "Oswald/Anton" advice misses.** A premium bold Latin display face contains **no** Devanagari, Bengali, Thai, Arabic-Indic, or CJK glyphs, so exotic numerals/words silently fall back to a mismatched system font and break the premium look. **Bundle Noto Sans/Serif per script** (Noto Sans Arabic / Devanagari / Bengali / Thai / CJK SC-JP-KR) as app assets and build a per-language `FontFamily`; verify each font actually carries the native *digit* glyphs (not all do). Non-Latin digits are **not** tabular/monospaced — stabilize the countdown layout with a fixed-size centered container, not `tabularFigures`. Handle RTL for Arabic labels via Compose `LayoutDirection` (§7).

---

## 3. Compose Implementation

All APIs are native on API 36; **skip the sub-API-33 gradient and sub-API-31 RenderScript fallbacks entirely** — they are unreachable on this device and are pure over-engineering for a single-device sideload.

### 3.1 Animated aura — AGSL `RuntimeShader` + `ShaderBrush`

`android.graphics.RuntimeShader` (**API 33**) wrapped in `androidx.compose.ui.graphics.ShaderBrush`. Create the shader **once** in `Modifier.drawWithCache`; push uniforms and `drawRect(brush)` in `onDrawBehind`. Drive time with `produceState` + `withInfiniteAnimationFrameMillis` (Compose frame clock, auto-pauses when not composed — good for battery), and **read the time/progress/color State inside `onDrawBehind`** so only the draw phase invalidates, never recomposition. ([developer.android.com/develop/ui/compose/graphics/draw/brush](https://developer.android.com/develop/ui/compose/graphics/draw/brush), [.../agsl/using-agsl](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl))

```kotlin
@Composable fun rememberShaderTime(): State<Float> = produceState(0f) {
  val start = withFrameMillis { it }
  while (true) { withInfiniteAnimationFrameMillis { value = (it - start) / 1000f } }
}

val t = rememberShaderTime()
Box(Modifier.fillMaxSize().drawWithCache {
  val shader = RuntimeShader(AURA_AGSL)
  shader.setFloatUniform("iResolution", size.width, size.height)
  val brush = ShaderBrush(shader)
  onDrawBehind {
    shader.setFloatUniform("iTime", t.value)
    shader.setFloatUniform("iProgress", progress)         // 0..1 across the interval
    shader.setColorUniform("deep",   Color.valueOf(/*…*/))
    shader.setColorUniform("bright", Color.valueOf(/*…*/))
    drawRect(brush)
  }
})
```

```agsl
uniform float2 iResolution; uniform float iTime; uniform float iProgress;
layout(color) uniform half4 deep; layout(color) uniform half4 bright;
half4 main(float2 fragCoord){
  float2 uv = fragCoord/iResolution;
  float t = iTime*0.15;
  float flow  = 0.5+0.5*sin(uv.x*3.0+t)*cos(uv.y*2.0-t*0.7);
  float flow2 = 0.5+0.5*sin((uv.x+uv.y)*2.5-t*1.3);
  half4 base = mix(deep, bright, flow);
  base = mix(base, bright, flow2*0.35);
  base = mix(deep, base, iProgress);   // deep at start -> bright at end
  return base;
}
```

Uniform names must match exactly; color uniforms need `Color.valueOf()` / `setColorUniform`. Keep per-pixel math cheap (a few sin/cos, no loops) — it runs on every pixel of a 2520×1080 panel at up to **120 Hz**. **Cheaper alternative that is usually sufficient:** animate the stops of a Compose `Brush.linearGradient`/`radialGradient` with an infinite transition — no shader code, easier to hold 120 Hz on two displays. Recommend starting with the shader for the "premium flow" look but keep the gradient-animation path as the fast fallback if thermals/battery suffer. ([developer.android.com/reference/android/graphics/RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader))

### 3.2 Frosted glass — the critical correctness point

**`Modifier.blur` and `graphicsLayer{ renderEffect = createBlurEffect(...) }` (both API 31+) blur the composable's OWN content, NOT the backdrop behind it in z-order.** A naive "frosted panel over the aura" with `Modifier.blur` will not blur the aura — the single most-requested visual silently fails. Two real ways to blur the backdrop:

- **(a) Haze** (`dev.chrisbanes.haze`, the de-facto choice). Mark the aura `Modifier.hazeSource(hazeState)` and the panel `Modifier.hazeEffect(hazeState){ … }`. Uses `RenderEffect.createBlurEffect` with CLAMP internally (API 31+). **Correction — dependency detail:** in Haze 2.x the blur API lives in a **separate gradle module**; you must depend on **both** `dev.chrisbanes.haze:haze` (core: `rememberHazeState`, `hazeSource`, `hazeEffect`) **and** `dev.chrisbanes.haze:haze-blur` (the `blurEffect{}` wrapper + tint, package `dev.chrisbanes.haze.blur`), or it won't compile. Pin the version; the API renamed across releases (pre-1.0 `haze`/`hazeChild` → 1.x `hazeSource`/`hazeEffect` → 2.x split blur module). ([github.com/chrisbanes/haze](https://github.com/chrisbanes/haze), [chrisbanes.me/posts/haze-2.0](https://chrisbanes.me/posts/haze-2.0/))
- **(b) Dependency-free, viable because you own the aura shader:** inside the panel's own `drawBehind`, re-draw the same `AURA_AGSL` `ShaderBrush` with a blur `renderEffect` on the panel layer, then a translucent tint + hairline gradient border on top. **Coordinate-space trap:** the re-drawn shader must be evaluated in **screen coordinates** (same `iResolution` = full display, `fragCoord` offset by the panel's on-screen top-left from `onGloballyPositioned`), or it renders a mismatched mini-gradient that doesn't line up with the aura behind it. Haze avoids this by capturing real source pixels.

Recommendation: start with **(b)** for the single full-screen aura backdrop (no dependency, you already draw the shader); adopt Haze only if you later layer glass over non-shader content. The cheapest "premium-enough" option is a static translucent dark tint + top-edge white gradient + hairline border with **no real blur** — reads as glass over the busy aura for a fraction of the GPU cost.

### 3.3 Perimeter stroke — rounded-rect Path + `PathMeasure.getSegment`

Build a closed `RoundRect` Path inset to the safe region with the device corner radius, measure with `androidx.compose.ui.graphics.PathMeasure`, carve the leading `0..progress` fraction with `getSegment`. Verified signatures: `setPath(path: Path?, forceClosed: Boolean)`, `getSegment(startDistance: Float, stopDistance: Float, destination: Path, startWithMoveTo: Boolean = true): Boolean`, `length`. ([developer.android.com/reference/kotlin/androidx/compose/ui/graphics/PathMeasure](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/PathMeasure))

```kotlin
Modifier.drawWithCache {
  val r = deviceCornerRadiusPx                 // from getRoundedCorner (below)
  val inset = safeInsetPx                       // from displayCutout + margin
  val path = Path().apply {
    addRoundRect(RoundRect(inset, inset, size.width-inset, size.height-inset, CornerRadius(r,r)))
  }
  val pm = PathMeasure().apply { setPath(path, /*forceClosed=*/true) }
  val total = pm.length; val dst = Path()
  onDrawBehind {
    dst.rewind()
    pm.getSegment(0f, total * progress, dst, true)   // progress = 0..1
    drawPath(dst, accent, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
  }
}
```

Glow: draw the stroke twice — a wide low-alpha stroke on a blurred `graphicsLayer` (hardware-accelerated) with the crisp stroke on top; avoid a CPU `BlurMaskFilter`. `rewind()` the destination each frame; `drawWithCache` re-caches on size change. (The "PathMeasure getSegment caching bug" cited in research is **unverified folklore** for the Compose PathMeasure — no tracker issue found — but rewinding each frame and re-running `setPath` on size change is harmless correct practice regardless.)

### 3.4 Corner radius + cutout queries

- **Corner radius:** `WindowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT/TOP_RIGHT/BOTTOM_LEFT/BOTTOM_RIGHT)` → `RoundedCorner.getRadius()` (px) / `getCenter()` (**API 31+**). Reach it via `LocalView.current.rootWindowInsets`. Returns **null** before the view is attached, on emulators, or when there's no rounded corner — **always keep a dp fallback** and read after first layout. The Flip 7's actual radius is **not published** — read at runtime. ([developer.android.com/.../rounded-corners](https://developer.android.com/develop/ui/views/layout/insets/rounded-corners))
- **Full display outline (best source for the perimeter path):** `DisplayShape` via `Display.getShape()` / `WindowInsets.getDisplayShape()` (**API 34+**) returns a `Path` of the actual physical display outline. ([developer.android.com/reference/android/view/RoundedCorner](https://developer.android.com/reference/android/view/RoundedCorner))
- **Camera cutout:** for padding, use Compose `WindowInsets.displayCutout` / `WindowInsets.safeDrawing` (inset region only). **Correction:** the Compose `WindowInsets.displayCutout` object has **no `cutoutPath`**. For the exact camera rectangle/outline to route the stroke around, use the *platform* `view.rootWindowInsets.displayCutout` → `DisplayCutout.getBoundingRects()` / `getSafeInsetTop()` / `getCutoutPath()` (**cutoutPath API 31**, `DisplayCutout` itself API 28). ([developer.android.com/reference/android/view/DisplayCutout](https://developer.android.com/reference/android/view/DisplayCutout))
- **Edge-to-edge:** required to draw the stroke to the physical edges. On **targetSdk 35+ edge-to-edge is enforced by default** (you get it free); `WindowCompat.setDecorFitsSystemWindows(window,false)` is deprecated — prefer `enableEdgeToEdge()`. Set `layoutInDisplayCutoutMode = shortEdges` (or `ALWAYS`, API 30) so you're allowed to draw into the cutout region and carve around it yourself. ([developer.android.com/.../edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge))

### 3.5 Performance rule

Keep every per-frame value **out of recomposition/relayout**: read frame-time/progress/color State inside the draw lambda only; create `RuntimeShader`/`Path`/`PathMeasure`/`Brush` once in `drawWithCache`; prefer `drawBehind` over a `Canvas` composable; confine any real blur to the smallest region. Profile with Layout Inspector recomposition counts (should stay flat while animating). **Note for §5:** `withInfiniteAnimationFrameMillis` stops when the screen is off — correct and desirable for the *visuals*, but it means the **timer/beep logic must NOT be driven by the animation clock**; the service owns a `SystemClock.elapsedRealtime()` clock.

---

## 4. Flip 7 Cover-Display Specifics

**Verified specs.** Cover FlexWindow: **4.1″ Super AMOLED, 1048×948 px** (Samsung/Wikipedia list portrait 1048×948; GSMArena writes 948×1048 — *same panel*, be explicit about orientation in code), up to **120 Hz**, 2600 nits peak (confirmed for the cover display, not just inner), Gorilla Glass Victus 2, ~345 ppi (calculated; density **not** officially published). Inner display: 6.9″, 2520×1080, 21:9, 1–120 Hz LTPO AMOLED 2X, 360×840 dp at density 3.0, centered top punch-hole. SoC Exynos 2500. ([gsmarena.com](https://www.gsmarena.com/samsung_galaxy_z_flip7-13712.php), [en.wikipedia.org/wiki/Samsung_Galaxy_Z_Flip_7](https://en.wikipedia.org/wiki/Samsung_Galaxy_Z_Flip_7))

**Camera safe-area — do NOT hardcode "bottom corner."** The FlexWindow wraps around the dual rear cameras (lower-left region when viewed upright, hinge at top); viewable area is ~95% of the rectangle. But Samsung publishes **no** corner radius or camera-cutout coordinates, and it's **unverified whether the cameras are exposed to apps as a real `DisplayCutout` at all** — the panel may present as a clean rounded rectangle with the cameras physically outside it (cutout queries return empty). **Query at runtime** (`getDisplayCutout()`, `getRoundedCorner()`, `getDisplayShape()`); if the cover returns an empty cutout, fall back to a **configurable, on-device-tuned** bottom-left keep-out rectangle (~bottom 15–20%), exposed as a calibration offset — not a magic number baked into the render path. ([androidauthority.com](https://www.androidauthority.com/galaxy-z-flip-7-cover-screen-camera-features-3579892/))

**Cover-vs-inner detection — correction.** There is **NO public `Display.FLAG_REAR`** (it's `@hide` in AOSP). The public Display flags are only `FLAG_PRESENTATION/PRIVATE/ROUND/SECURE/SUPPORTS_PROTECTED_BUFFERS`. Detect the surface from **live window metrics**, not a flag: branch on `WindowManager.getCurrentWindowMetrics().getBounds()` (cover is near-square ~948×1048; inner is tall 21:9 ~1080×2520), optionally cross-checked with `Display.getName()`. Drive the **entire layout off live `WindowMetrics` + `WindowInsets`** as one responsive Compose layout — this handles inner, cover, and whatever cropped rectangle MultiStar grants, and is less code than branching on a nonexistent flag. `FoldingFeature` posture alone does **not** distinguish cover-vs-inner when closed — combine with the size signal. ([learn.microsoft.com/.../android.views.display](https://learn.microsoft.com/en-us/dotnet/api/android.views.display?view=net-android-35.0), [developer.android.com/reference/android/hardware/display/DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager))

**MultiStar deployment reality (unofficial, Samsung-gated, not a platform API).** No official Android/Samsung path launches an arbitrary sideloaded app on the FlexWindow. Working method: install **Good Lock** → **MultiStar** module → "I ♥ Galaxy Foldable" → **Launcher Widget**, select the app; then Settings → Cover screen → Widgets → add the **Good Lock Launcher** widget, fold, swipe to it, tap the app. Caveats to design around: (1) MultiStar just launches your **normal Activity** on the panel — treat 1048×948 as a real configuration; it is not a purpose-built cover UI. (2) MultiStar (not your app) decides the window size and **can letterbox/clip** it — don't assume you own the full panel. (3) It's a user-installed plugin with regional availability that can change across One UI updates — a nice-to-have that depends on a third party. (4) Moving inner↔cover is a config/display change that can recreate the Activity — the **service-owned clock (§5) makes this safe**, but **verify on-device** that the FGS, wake lock, and audio ducking keep running across the switch and that `getRoundedCorner()`/`displayCutout` return useful (non-null) values in the MultiStar-hosted context. ([sammobile.com](https://www.sammobile.com/news/run-any-app-on-galaxy-z-flip-6-flip-7-fe-cover-screen/), [androidheadlines.com](https://www.androidheadlines.com/2025/07/run-any-app-samsung-galaxy-z-flip-7-cover-display-heres-how.html))

**Open items requiring the physical device:** exact usable pixel rectangle per MultiStar display mode; whether the cover reports cameras/corners via the cutout/shape APIs; whether beeps continue when the FlexWindow times off; cover density bucket/dp.

---

## 5. Runtime — Drift-Free Timing & Service Architecture

**Architecture: the foreground service owns the clock; Compose only collects state.** Run a started + bound FGS (`startForegroundService()` then `bindService()`). The service holds all authoritative state (interval index, phase, `endTargetElapsed`, `isPaused`, `pausedRemaining`), ticks on its own coroutine scope (~16–50 ms), and emits an immutable `TimerUiState` via `MutableStateFlow`. Compose collects with `collectAsStateWithLifecycle()`. Because every value is **derived from `elapsedRealtime()` against stored targets** — never from UI-thread accumulators — Activity recreation (fold/unfold, rotation, moving to the FlexWindow) never disturbs timing: the recreated Activity re-binds and repaints the current remaining value. Pause/Resume are pure state transitions.

> **Dependency note:** `collectAsStateWithLifecycle()` is **not** in core Compose — add `androidx.lifecycle:lifecycle-runtime-compose`.

**Drift-free timing.** Anchor to `SystemClock.elapsedRealtime()` (monotonic, **includes deep sleep**, the documented recommended basis for interval timing). Compute each interval's end as `endTarget = startElapsed + durationMs`; every tick derive `remaining = endTarget - elapsedRealtime()`. **Never accumulate `Handler.postDelayed`/`delay()` deltas.** On pause store `remaining = endTarget - now`; on resume set `endTarget = now + remaining`. ([developer.android.com/reference/android/os/SystemClock](https://developer.android.com/reference/android/os/SystemClock))

> **Correction on the clock nuance:** the research said `postDelayed`/`delay` "run on `uptimeMillis`." Precisely: `Handler.postDelayed` and `delay()` on `Dispatchers.Main` use `uptimeMillis`; `delay()` on background dispatchers uses `System.nanoTime()`. **Neither advances during deep sleep**, so the actionable rule is unchanged: always recompute `remaining` from `elapsedRealtime()`.

Precompute absolute cue timestamps (lead-in + each transition at 5/3/2/1/0 s before the boundary); loop = read `now`, `delay(nextCue - now)`, then on wake recompute and fire (fire immediately if the sleep overshot), so scheduler jitter never accumulates. Leave a self-check asserting a synthetic N-interval run lands each boundary within a few ms — **run it with the screen actually off**, not just a fast synthetic loop.

**Foreground service type (mandatory on API 34+).** `startForeground()` throws `MissingForegroundServiceTypeException` on Android 14+ unless a `foregroundServiceType` is declared with the matching permission. There is **no "timer" type**. Recommendation: **`specialUse`** — no runtime timeout, requires only `FOREGROUND_SERVICE_SPECIAL_USE` (no sensor permissions), and the free-form subtype string is only reviewed by Google Play (irrelevant for a sideloaded APK, though the `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` element must still be present — the platform reads it). `mediaPlayback` (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`) is a defensible alternative since the app emits cued audio, but it pulls in MediaSession expectations you don't need. **Avoid `dataSync`/`mediaProcessing`** (timeout-capped on API 35+). Either type satisfies the Android 15+ audio-focus rule below, since that requires *any* running FGS, not a specific type. Start the FGS from the user's **Start tap** (app visible) to sidestep background-start restrictions, and call `startForeground()` promptly to avoid an ANR. ([developer.android.com/.../fgs/service-types](https://developer.android.com/develop/background-work/services/fgs/service-types), [.../14/changes/fgs-types-required](https://developer.android.com/about/versions/14/changes/fgs-types-required))

**Keep-awake — the make-or-break fact.** **An FGS does NOT keep the CPU awake.** With the screen off, the CPU can suspend and the timing loop freezes, so beeps arrive late or not at all — the exact #1 bug. The service must hold a `PowerManager.PARTIAL_WAKE_LOCK` for the duration of a running workout (`WAKE_LOCK` permission, normal, no prompt): `newWakeLock(PARTIAL_WAKE_LOCK, tag)`, `acquire(safetyTimeoutMs)`, `release()` on pause/stop/`onDestroy`. **Set the safety timeout longer than the longest plausible single workout** (or re-acquire per interval), or it self-releases mid-session and beeps stop. `FLAG_KEEP_SCREEN_ON` does **not** substitute — it only holds while a visible Activity is foreground. ([developer.android.com/.../background-tasks/awake](https://developer.android.com/develop/background-work/background-tasks/awake), [.../awake/wakelock](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock))

**Keep the screen on in-foreground:** `window.addFlags(FLAG_KEEP_SCREEN_ON)` / `clearFlags(...)`, wired via a Compose `DisposableEffect` on the timer screen (add on enter, clear on dispose). No permission. Clean split: **`FLAG_KEEP_SCREEN_ON` = screen while looking at it; `PARTIAL_WAKE_LOCK` = beeps while screen off.** ([developer.android.com/.../awake/screen-on](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on))

**Doze is the one thing that beats the wake lock** — it explicitly *ignores* wake locks. During an active workout it's usually not reached (short intervals, phone handled/moving, FGS alive); the real risk is a long screen-off rest with the phone flat and still. Mitigations, best first: (a) **prompt once to exempt the app from battery optimization** — `PowerManager.isIgnoringBatteryOptimizations(pkg)` + `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission; Play-restricted but fine for a sideload). One UI adds aggressive "Sleeping apps" management on top of AOSP Doze, so **also** have the user add the app to Settings → Battery → **Never sleeping apps**. (b) belt-and-braces only: one `AlarmManager.setExactAndAllowWhileIdle()` ~6 s before the final beeps to wake and re-acquire the lock (capped once/9 min under Doze — can wake for the final countdown but can't drive per-second beeps). **You do NOT need AlarmManager for the normal flow**, and `SCHEDULE_EXACT_ALARM` is denied-by-default / `USE_EXACT_ALARM` is reserved for alarm-clock apps — another reason to avoid the alarm path. ([developer.android.com/training/monitoring-device-state/doze-standby](https://developer.android.com/training/monitoring-device-state/doze-standby), [.../14/changes/schedule-exact-alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms))

**Notification.** The FGS must post an ongoing notification (low-importance channel). `POST_NOTIFICATIONS` is a runtime permission (**API 33+**) — request it. Key behavior: you do **not** need it to launch the FGS, and if the user **denies** it the service **still runs and still beeps** — only the drawer notification is hidden (still in the FGS Task Manager). Don't treat denial as fatal, but warn the user their timer notification is hidden. Android 16's `Notification.ProgressStyle` (API 36) can promote the ongoing notification to prominent system surfaces; whether One UI 8's cover-screen "Now Bar" renders it on the FlexWindow is a Samsung-specific, **undocumented** coupling — verify on-device, don't rely on it. ([developer.android.com/.../notification-permission](https://developer.android.com/develop/ui/views/notifications/notification-permission), [.../16/features/progress-centric-notifications](https://developer.android.com/about/versions/16/features/progress-centric-notifications))

**Documented user setup steps that gate the #1 reliability risk** (not code): grant `POST_NOTIFICATIONS`; disable battery optimization; add to "Never sleeping apps". Without these, screen-off beeping can still fail regardless of a correct implementation.

---

## 6. Audio — Per-Beep Ducking & Tone Design

**Ducking.** Build one reusable `AudioFocusRequest` with `setFocusGain(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)` (**API 26+**); `requestAudioFocus(request)` / `abandonAudioFocusRequest(request)`. Holding MAY_DUCK focus makes the system **auto-duck** the current focus owner (Spotify dips) with no work on your side and **no callback to the music app**. On **Android 15+ (API 35)**, `requestAudioFocus` returns `AUDIOFOCUS_REQUEST_FAILED` unless the app is the top app **or running an FGS** — the workout FGS (any type) satisfies this. ([developer.android.com/media/optimize/audio-focus](https://developer.android.com/media/optimize/audio-focus), [.../15/behavior-changes-15](https://developer.android.com/about/versions/15/behavior-changes-15))

> **Spec deviation — flag to the user and get sign-off.** The spec says beeps duck music "ONLY while the beep sounds, then music returns." Cycling focus on each of the 5 beeps (5/3/2/1/0 s) makes the music audibly **pump** up-and-down every second, adds a focus round-trip before each beep, and some players **pause** rather than duck on focus change. The better-sounding default is to **hold one MAY_DUCK request across the whole ~5 s countdown cluster** and abandon once after the GO tone. But that keeps music ducked through the silent 4 s and the ~1 s gaps too — a **deliberate deviation from the literal spec**, not a match. Present the choice: per-cluster (recommended, smooth) vs literal per-beep (accept the flutter). Build the request with `setWillPauseWhenDucked(false)`. Note: MAY_DUCK will **not** duck a focus owner that set `CONTENT_TYPE_SPEECH` or `setWillPauseWhenDucked(true)` (podcasts/nav) — those pause or are unaffected; acceptable here, worth knowing.

**Tone production.** Use **`SoundPool` with 4 pre-rendered enveloped WAVs** in `res/raw` (least code, predictable, designer-tuned): 44.1 kHz / 16-bit mono, envelope baked in. Load all four at workout start and **gate Start on `OnLoadCompleteListener`** — SoundPool silently drops `play()` on a not-yet-loaded sample ("sample not ready"), so the very first beep won't sound otherwise. Alternative with zero bundled assets: synthesize enveloped sines into `AudioTrack` `MODE_STATIC` buffers, kept warm. **Reject `ToneGenerator`** (harsh DTMF-style tones, no envelope/timbre control). ([developer.android.com/reference/android/media/SoundPool](https://developer.android.com/reference/android/media/SoundPool), [.../SoundPool.OnLoadCompleteListener](https://developer.android.com/reference/android/media/SoundPool.OnLoadCompleteListener))

**Envelope (kills clicks/pops).** Start oscillator phase at 0; raised-cosine (Hann) fade-in ~5–10 ms, fade-out ~30–60 ms; end on a zero crossing. Per sample: `amp * envelope(n) * sin(2πf·n/sr)`. Add ~20% 2nd/3rd harmonic for warmth. Applies whether pre-rendered or synthesized.

**Concrete four-cue palette** (tune by ear on-device):
- **5 s warning** — E5 ~659 Hz, ~200 ms, moderate; rounder/lower so it reads as "get ready."
- **3-2-1 ticks** — identical A5 ~880 Hz blips, ~110–130 ms, 8 ms attack / 50 ms decay; short, crisp, repeated.
- **GO / transition** — brighter and slightly longer: upward chirp 880→~1319 Hz over ~300 ms, or a C6+E6 (~1047 + 1319 Hz) two-note chord ~350 ms at a hair higher level. Distinct 5 s (low/round) vs ticks (mid) vs GO (high/rich) = three unmistakable sounds.

**Audibility over loud music.** Give the beep player `AudioAttributes` **`usage = USAGE_ALARM`** + `CONTENT_TYPE_SONIFICATION` so it rides the **alarm** stream — independent of the media volume the user lowered for their music — while the auto-duck handles the phone's own music. Human hearing peaks ~2–4 kHz; the 880 Hz–1.3 kHz cues stay pleasant, add a harmonic partial in the 2–3 kHz band to help them pierce. Set player volume 1.0; never override the user's system volume. Do **not** rely on `FLAG_AUDIBILITY_ENFORCED` (legacy camera-shutter lever, unreliable for third-party apps). ([source.android.com/docs/core/audio/attributes](https://source.android.com/docs/core/audio/attributes))

> **Caveats for the real build:** (1) USAGE_ALARM depends on the **alarm volume slider**, which is **not** guaranteed near-max — surface an in-app check if `getStreamVolume(STREAM_ALARM)` is low (the app is told never to override system volume). (2) USAGE_ALARM plays through **Do Not Disturb** — desirable here. (3) **Verify on the physical Z Flip 7** that USAGE_ALARM + MAY_DUCK actually *ducks* Spotify (music quieter) rather than pausing it. (4) Open Google issue **375228130** reports audio-focus-from-FGS failures on some Android 15 setups (couldn't read the ticket — auth wall) — confirm focus is granted on-device before relying on ducking. If MODE_STATIC is chosen, add `setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)` (optional polish; latency is imperceptible for a timer).

**Manifest for audio:** `FOREGROUND_SERVICE`, the type permission (`FOREGROUND_SERVICE_SPECIAL_USE` per §5), `WAKE_LOCK`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Landing beeps on the second: schedule off `elapsedRealtimeNanos` absolute targets with a small latency lead (estimate from `PROPERTY_OUTPUT_FRAMES_PER_BUFFER` / `PROPERTY_OUTPUT_SAMPLE_RATE`); pre-warm by playing a cue at volume 0 so the HAL is hot on the first real beep.

---

## 7. Language Data (13 languages)

**Two shared pipelines, both from stdlib — do NOT hand-code number words.** Android bundles ICU as `android.icu.*` since **API 24**:
- **Native digit glyphs:** `android.icu.text.NumberFormat`/`DecimalFormat` with the locale's numbering system (or `DecimalFormatSymbols`) renders Arabic-Indic, Devanagari, Bengali, Thai, Han digits automatically.
- **Word mode:** `android.icu.text.RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT)`. CLDR/ICU ships SPELLOUT rulesets for bn, th, hi, ar, ru, ko, ja and the long-standing en/es/fr/de/pt/zh. Word mode only needs **cardinals 0–59** ("under a minute"), which every locale covers. **Fallback:** if any on-device ruleset is incomplete, a hardcoded 0–59 table for that language is ~60 words — trivial. **Flag:** verify **Mandarin (zh)** SPELLOUT on the actual device (the rbnf directory fetch didn't surface `zh.xml` explicitly); ru/ar have gender/case variants — the tables below give the correct standalone forms. ([developer.android.com/reference/android/icu/text/RuleBasedNumberFormat](https://developer.android.com/reference/android/icu/text/RuleBasedNumberFormat), [github.com/unicode-org/cldr/.../rbnf](https://github.com/unicode-org/cldr/tree/main/common/rbnf))

Given ICU coverage, the **safest lazy approach is: use ICU SPELLOUT for word mode, verified against the spot-checks below, and drop to a hardcoded 0–59 table only for any locale that fails on-device.** The tables below are also your verification oracle and your guaranteed fallback.

### 7.1 Per-language table

| Lang (code) | Script | Native digits? | Digit glyphs | Work / Rest | Big-clock recommendation |
|---|---|---|---|---|---|
| English (en) | Latin | No | 0–9 | **WORK / REST** | Western digits MM:SS. Reference/fallback. |
| Mandarin (zh) | Han | **Yes** | 〇一二三四五六七八九 | **运动 / 休息** (Trad. 運動/休息) | **Digit-by-digit Han** with **〇 (U+3007)** for zero, full-width colon **：** — fixed-width, aligns, maximally exotic. Place-value (三十四) only in word mode. |
| Japanese (ja) | Kanji | **Yes** | 〇一二三四五六七八九 | **運動 / 休憩** | Same as zh (shared glyphs). Digit-by-digit kanji + 〇 + ：. Reads as deliberately archaic → serves intrigue. |
| Korean (ko) | Hangul | **No** | 0–9 | **운동 / 휴식** | **Western digits** for the clock (Hangul has no numeral glyphs, real clocks use Western). Word mode = Sino-Korean spelled. |
| Russian (ru) | Cyrillic | **No** | 0–9 | **Работа / Отдых** | Western digits MM:SS. Intrigue comes from word mode + label. |
| Hindi (hi) | Devanagari | **Yes** | ०१२३४५६७८९ | **काम / आराम** | Native Devanagari digits ०–९, standard `:`. |
| Arabic (ar) | Arabic | **Yes** | ٠١٢٣٤٥٦٧٨٩ | **تمرين / راحة** | Native Arabic-Indic ٠–٩, minutes LEFT, plain colon; force LTR only if MM/:/SS are separate nodes (see below). |
| Spanish (es) | Latin | No | 0–9 | **Trabajo / Descanso** | Western digits MM:SS. |
| French (fr) | Latin | No | 0–9 | **Effort / Repos** | Western digits MM:SS. Use `:` not French `h`/`min`. |
| Japanese see zh | | | | | |
| German (de) | Latin | No | 0–9 | **Aktiv / Pause** | Western digits MM:SS. |
| Portuguese (pt) | Latin | No | 0–9 | **Trabalho / Descanso** | Western digits MM:SS (PT-BR default). |
| Bengali (bn) | Bengali | **Yes** | ০১২৩৪৫৬৭৮৯ | **কাজ / বিশ্রাম** | Native Bengali digits ০–৯, standard `:`. |
| Thai (th) | Thai | **Yes** | ๐๑๒๓๔๕๖๗๘๙ | **ทำงาน / พัก** | Native Thai digits ๐–๙, standard `:`. |

### 7.2 Number-word rules + spot checks (21 / 34 / 45 / 59)

All spot-checks below were verified against Wiktionary/Omniglot/Unicode; the four European CJK/Latin sets and Hindi/Bengali/Thai were confirmed CONFIRMED by verification, Korean/Arabic had only minor non-substantive corrections (noted).

- **English** — tens + hyphen + unit. **twenty-one / thirty-four / forty-five / fifty-nine.** (Watch "forty", no u.)
- **Mandarin (zh)** — Han place-value: [2–5]十[1–9]; exact tens drop the ones. Use 二 (not 两) for numeral 2. **二十一 / 三十四 / 四十五 / 五十九.** Number-word zero is 零; digit-slot zero is 〇.
- **Japanese (ja)** — Sino-Japanese on'yomi place-value, glyphs identical to zh. **二十一 / 三十四 / 四十五 / 五十九.** (Written glyph invariant across reading variants — irrelevant, no audio.)
- **Korean (ko)** — **Sino-Korean** syllable blocks, no spaces: [이/삼/사/오]+십+[일–구]. **이십일 / 삼십사 / 사십오 / 오십구.** **Critical:** minutes/seconds require Sino-Korean; native Korean (하나 둘 셋…) is for hours/reps and is grammatically wrong here. Zero = 영.
- **Russian (ru)** — tens + space + unit, two words. **двадцать один / тридцать четыре / сорок пять / пятьдесят девять.** Use masculine bare-count forms: 1=**один** (not одна), 2=**два** (not две) — correct for a noun-less countdown.
- **Hindi (hi)** — **NO algorithmic composition; irregular, ship a 0–59 table.** Only pattern: values ending in 9 use an "un-" prefix off the *next* ten. **इक्कीस / चौंतीस / पैंतालीस / उनसठ.** Preserve conjuncts and the chandrabindu (5 = पाँच). Work=काम, Rest=आराम (or formal विश्राम).
- **Bengali (bn)** — **NO composition; irregular, ship a 0–59 table.** 9-endings take the classical ঊন- prefix off the next ten. **একুশ / চৌত্রিশ / পঁয়তাল্লিশ / ঊনষাট.** Preserve chandrabindu ঁ and য়. Note ঊন (long) vs উন (short) variation. Work=কাজ, Rest=বিশ্রাম.
- **Thai (th)** — **fully regular, compose algorithmically:** [unit]+สิบ, then append unit 1–9. Two exceptions: 20 = **ยี่สิบ** (not สองสิบ); a trailing 1 becomes **เอ็ด**. **ยี่สิบเอ็ด / สามสิบสี่ / สี่สิบห้า / ห้าสิบเก้า.** No spaces between words. Store logical order (เ is a leading vowel — let the shaper reorder). Work=ทำงาน (or งาน), Rest=พัก.
- **Arabic (ar)** — [unit] + وَ(attached) + [tens], unit FIRST. **واحد وعشرون / أربعة وثلاثون / خمسة وأربعون / تسعة وخمسون.** Use nominative -ون / اثنان citation forms; numbers are noun-less so no gender/reverse-polarity agreement. Work=تمرين, Rest=راحة.
- **Spanish (es)** — 21–29 fused single words (**veintiuno**, veintidós…); 31–59 three words tens+y+unit. **veintiuno / treinta y cuatro / cuarenta y cinco / cincuenta y nueve.** Accents mandatory (dieciséis, veintidós), keep them even in ALL-CAPS.
- **French (fr)** — traditional "et un" for X1 (**vingt et un**), hyphens elsewhere (trente-quatre). **vingt et un / trente-quatre / quarante-cinq / cinquante-neuf.** Pick one of traditional vs 1990-reform (fully hyphenated) and stay consistent. Use `:` not `h`/`min`.
- **German (de)** — unit + und + tens as ONE reversed word. **einundzwanzig / vierunddreißig / fünfundvierzig / neunundfünfzig.** Traps: 1→ein (not eins) in compounds; 30=dreißig (ß); 6=sech-, 7=sieb-. In ALL-CAPS, ß→SS (or ẞ U+1E9E). Umlauts/ß essential — verify font ships them.
- **Portuguese (pt)** — tens + e + unit, three words in EVERY decade, never fused. **vinte e um / trinta e quatro / quarenta e cinco / cinquenta e nove.** Preserve circumflex on três. (PT-BR: dezesseis/dezessete/dezenove; PT-PT swaps to dezasseis/dezassete/dezanove.)

### 7.3 Clock-rendering guidance

- **Distinct-numeral scripts (zh, ja, hi, ar, bn, th):** render native digits on the big clock — that IS the intrigue. Han/kanji use **digit-by-digit with 〇 (U+3007) for zero** and a full-width colon **：** for aligned fixed-width fields; the Indic/Thai/Arabic digits are fixed-width with a standard `:`.
- **Western-digit scripts (en, ko, ru, es, fr, de, pt):** keep Western digits MM:SS on the clock; the "can't read it" effect must come from **word mode + the translated Work/Rest label**. For ko/ru specifically, a fully spelled clock would misalign and (Russian) mis-inflect — reserve spelling for the under-a-minute word display.
- **Word mode (all):** spell the seconds when under a minute (from ICU SPELLOUT / the fallback table), with the small numeric MM:SS as the fallback read beneath. Thai/Korean have no inter-word spaces; don't insert any.

> **Korean correction (non-substantive):** the recommendation to keep Western digits and spell Sino-Korean only in word mode is sound. The *justification* was wrong — Hangul syllables are East Asian Width **Wide (full-width, uniform per block)**, same class as CJK. A spelled MM:SS misaligns not because blocks vary in width but because the **number of syllable blocks varies** (오 = 1 block vs 삼십사 = 3). ([unicode.org/reports/tr11](https://www.unicode.org/reports/tr11/))

> **Arabic bidi correction (relaxes the requirement):** the claim that a MM:SS run "mirrors" in an RTL paragraph is **overstated for a single string**. Per UAX #9 rule W4, a single colon (bidi class CS) between two Arabic-Indic digits (class AN) is coerced to AN, so `٠٢:٤٥` resolves to one all-AN run that displays **LTR with minutes on the left and does not mirror** by default. Real mirroring occurs **only if MM, `:`, and SS are separate Compose `Text` nodes inside an RTL Row.** So: for a single-string clock no special handling is needed; the forced-LTR fix (explicit `LayoutDirection.Ltr` Row or `textDirection = TextDirection.Ltr`) is **strictly necessary only in the multi-node case** — keep it as harmless defensive insurance. Do NOT reverse digits within a group; do NOT hardcode Arabic Presentation Forms or manually reverse strings — Android/HarfBuzz shapes RTL + cursive joining automatically. Put each Arabic label in its own RTL text node so surrounding LTR English chrome doesn't drag it; center labels to sidestep edge-alignment. ([unicode.org/reports/tr9](https://www.unicode.org/reports/tr9/))

**Font coverage (all non-Latin):** bundle Noto Sans per script (Arabic, Devanagari, Bengali, Thai) and the **per-locale CJK variant** (Noto Sans CJK **SC/JP/KR**) for the word-form labels — Han unification means numerals are shared but zh/ja/ko word glyphs differ regionally. Verify each font carries the native **digit** glyphs. Don't rely on One UI's silent per-glyph fallback — it produces inconsistent weights and breaks the premium bar.

---

## 8. Recommended Architecture & Build Order

**Module shape (keep it flat — one app module).** No speculative abstractions; a single `TimerService` owns the clock, a `MutableStateFlow<TimerUiState>` is the one contract to the UI, Compose collects it.

```
data/      PresetStore (kotlinx.serialization -> filesDir JSON), built-in ladder/pyramid preset JSON
model/     Interval, WorkoutDef, TimerUiState (immutable), NumberFormatter (ICU digits + SPELLOUT + 0–59 fallback table)
service/   TimerService (FGS specialUse): elapsedRealtime clock, cue scheduler, wake lock, SoundPool, audio focus, notification
ui/        TimerScreen (aura shader, perimeter stroke, big number, round counter, pause menu/glass),
           EditorScreen (append/insert/inline-edit/drag/duplicate/delete), PresetsScreen, SettingsScreen (English)
```

**Presets:** named JSON in `filesDir` via `kotlinx.serialization`. No database, no accounts. Ladder/pyramid ship as bundled preset JSON — **do not build a generator** (spec already says presets, not a generator).

**Manifest essentials:** `compileSdk/targetSdk/minSdk 36`; permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; `<service android:foregroundServiceType="specialUse">` with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` `<property>` element. Extra deps beyond Compose BOM: `androidx.lifecycle:lifecycle-runtime-compose` (for `collectAsStateWithLifecycle`), a drag-reorder helper (`sh.calvin.reorderable`) or hand-rolled `detectDragGestures`, and — only if you choose the Haze glass path — `dev.chrisbanes.haze:haze` **and** `dev.chrisbanes.haze:haze-blur`.

**Build order (each step ends with something runnable and verifiable on the device):**

1. **Service-owned clock + drift self-check.** `TimerService` (`specialUse` FGS started from a Start tap), `elapsedRealtime`-anchored schedule, `StateFlow<TimerUiState>`, wake lock held while running. Bare Compose screen shows the number. **Verify: synthetic N-interval run lands each boundary within a few ms, screen OFF.** This is the correctness spine — do it first.
2. **Audio.** SoundPool + 4 enveloped WAVs (gate Start on load-complete), USAGE_ALARM, MAY_DUCK focus held per-cluster (get the user's sign-off on cluster-vs-per-beep). **Verify on-device: screen off / backgrounded / another app foreground — beeps fire; Spotify ducks not pauses; audio focus granted (issue 375228130 risk).**
3. **Base-mode UX + pause menu.** Round counter, tap-to-pause → Resume / End (End guarded), `FLAG_KEEP_SCREEN_ON` via DisposableEffect. Battery-optimization + "Never sleeping apps" prompts. **Verify: long screen-off rest keeps final beeps on time.**
4. **Visuals.** Aura shader (or gradient-animation fallback), perimeter stroke from `getDisplayShape()`/`getRoundedCorner()` with dp fallbacks + edge-to-edge + cutout carve-out, glass pause menu (dependency-free re-drawn-shader first). **Verify: recomposition counts flat while animating; digits clear 4.5:1 on brightest green; 120 Hz holds.**
5. **Language feature.** `NumberFormatter` (ICU digits + SPELLOUT, 0–59 fallback table as verification oracle and safety net), per-script bundled fonts, word mode with numeric fallback, Arabic single-string clock (no forced-LTR needed) + RTL label node. Settings/structural UI stay English. **Verify: spot-checks 21/34/45/59 in all 13; zh SPELLOUT on-device.**
6. **Sequence editor.** Append + insert-between + inline edit + drag-reorder + duplicate + undoable delete.
7. **Cover screen.** One responsive layout driven off live `WindowMetrics` (already the case if built responsively from step 3) + measured camera keep-out calibration constant. **Verify via the real MultiStar path: install Good Lock + MultiStar, add to Launcher Widget, record the exact usable rectangle and display mode, confirm FGS/wake-lock/audio survive the inner↔cover switch and that cutout/corner APIs return useful values.** This is the biggest deployment risk and is last because everything downstream depends on the measured rectangle.

**What this spec does NOT need (skip):** a ladder/pyramid generator (ship presets), a database (JSON files), any accounts/sync, AlarmManager for the normal flow, sub-API-33/31 fallbacks, `ToneGenerator`, and per-language hand-coded number words (ICU + a 60-word fallback table covers it).

**Load-bearing "verify on the physical device" list** (nothing here can be settled from docs): the FlexWindow's real corner radius and whether its cameras/corners report via cutout/shape APIs; the exact MultiStar usable rectangle per display mode; USAGE_ALARM+MAY_DUCK ducking (not pausing) Spotify on One UI 8; audio-focus-from-FGS reliability (issue 375228130); whether the partial wake lock alone holds beeps through a multi-minute screen-off rest or the battery-optimization exemption is mandatory; and the actual on-device error of SoundPool onset vs the scheduled second (calibrate the latency-lead constant).