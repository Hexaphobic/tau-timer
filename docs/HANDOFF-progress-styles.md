# Handoff prompt — selectable countdown styles

> **PARKED 2026-08-11, mid-design. Nothing built, no code written.** The brief below still stands as
> background, but §4 and §5 have been overtaken — read this block first.
>
> ### Settled
>
> - **Two styles, not four.** **Split** (the shipping perimeter stroke) stays exactly as it is and
>   stays the default — explicitly not to be touched. **Drain** is the only addition.
> - **Bar and Ring are rejected.** Ring failed on a finding the original brief missed: the Aura
>   shader's heaviest bloom (`weight 0.9`) sits at roughly screen centre, which is precisely where a
>   ring around the numeral is drawn. Split escapes to the edge; Ring cannot. Bar was simply not
>   wanted. Iris, tide and horizon stay parked and unbuilt.
> - **§4 dissolves.** Drain no longer behaves differently on the two grounds. The emptied region
>   **paints its own black over the ground** instead of relying on the ground to be black: Drain is
>   two halves of one layer — phase colour below the level, a black scrim above it. On Minimal the
>   scrim lands on black and costs nothing; on Aura it is what makes the level read. One
>   implementation, no per-ground branch, **and no new shader uniforms** — §3 is respected.
> - **Phone + iOS only.** The watch keeps its flat, numbers-only timer.
> - **Its own `SettingsCard` under Theme**, not a row inside the Theme card — owner's words were
>   "an extra panel inside settings". Tiles are **live previews**, each running its own loop.
> - Enum as §6 describes: mirrors `Palette`, persisted by name,
>   `runCatching { valueOf() }.getOrDefault(SPLIT)`, setter `updateProgressStyle()`.
>
> ### Open — must be answered before building
>
> 1. **The aura only exists where the level has emptied.** Owner, on seeing it: *"the aura doesn't
>    exist, as it's lighting up. You reveal it essentially in the background."* With the scrim on,
>    the shader reads as something being uncovered rather than something that was already there.
>    This is the live objection and it has no answer yet.
> 2. **Two constants unset** — how black the emptied part goes, and the opacity of the colour still
>    standing. The tuning artifact carries both as dials; the numbers were never called.
> 3. **The numeral disappears in Mono.** `Palette.MONO` makes work, rest and prepare all
>    `Color.White`, so a white numeral vanishes the moment the level reaches it. Vesper (`#99FFE4`)
>    is pale enough to be close behind. Three candidates were built and shown, none chosen:
>    **Split** (white above the level, black below — reuses the two masks Drain already has),
>    **Hard switch** (whole numeral flips once the level passes its middle), and **Plain** (status
>    quo, i.e. the bug).
>
> ### Worth knowing before touching the numeral
>
> - The timer numeral has **no glow behind it** — it is plain white on the ground. A halo in an
>   early mock was the mock's own `text-shadow`, not the app's.
> - Whatever treats the numeral must handle four shapes, not one: Western digits, **stacked** CJK and
>   Korean (two lines, minutes over seconds), Devanagari and Arabic-Indic digits, and **Cistercian**,
>   which is a single drawn glyph with no baseline. And **word mode is on by default**, so under 60
>   seconds English, Spanish, French and Russian show a spelled word — a wide, short shape instead of
>   a tall one, and what most users will actually be looking at.
>
> ### Artifacts
>
> - Fourteen styles, the original survey — <https://claude.ai/code/artifact/e33d4352-faf5-4683-8309-41864633bcd1>
> - Four styles against both grounds — <https://claude.ai/code/artifact/bb10417c-bdcf-46fd-906f-09d23104e34d>
> - Drain: scrim mechanism, dials, all ten languages, the three numeral treatments —
>   <https://claude.ai/code/artifact/2dd21db6-8d3d-4c7a-8cbe-8737a936a9e2>

Copy everything below the line into a fresh chat. It is written to be self-contained.

---

I want to add **selectable countdown/progress styles** to my interval timer app. Right now there is
exactly one: a stroke on the screen edge. I want three more as options alongside it. Read this whole
brief before touching anything, and **do not start building until you have asked me the open
question in §4.**

## 1. The app

`/Users/cicero/Interval Timer` — "Interval Timer: Not Ugly", published by MidaMultiMedia.
Kotlin/Compose Android phone app + a standalone Wear OS module + a native SwiftUI iOS port
(~15,000 lines). AMOLED black, an AGSL/Metal aurora shader, liquid-glass controls, 8 colour themes,
a drag-to-reorder sequence editor, drift-free timing in a foreground service, 12-language countdown.
Android is submitted to Play and in review. iOS is at *Prepare for Submission* and not yet sent.

Useful background docs already in the repo: `docs/PUNCHLIST.md` (the running design/bug record),
`docs/GROWTH.md` (why these styles are being built — see §6), `docs/IOS_PORT.md`.

## 2. What exists today

The one progress indicator is **`SplitProgress`** — not a single loop. Two arms originate at the
left and right side-midpoints and split outward: four bright tips run up and down the sides, around
the corners, and along the top and bottom edges toward their centres. `remaining` goes 1 → 0 and the
arms retreat back to the two side midpoints.

- Android: `app/src/main/java/com/chrispoole/intervaltimer/ui/Aura.kt:311`
  `@Composable fun SplitProgress(remaining: Float, color: Color, modifier: Modifier = Modifier)`
- iOS: `ios/IntervalTimer/UI/Aura.swift:173` `struct SplitProgress: View { remaining, color }`
- Drawn by the timer screen: `MainActivity.kt:2230 TimerScreen(...)` and `ios/.../UI/TimerView.swift`

The timer background has **two modes** and they are not changing (see §3):
`AuraBackground()` (the shader) and Minimal (pure black), switched by the `Settings.minimalBg`
boolean at `Settings.kt:43`.

## 3. Explicitly out of scope — do not reopen

- **Do not touch the background/ground.** I explored making a third "glow" ground between Aura and
  Minimal, with the shader turned down via new `iSpread`/`iGain`/`iGrain` uniforms. **I rejected
  it** — it read as "something trying too hard to look cool". `minimalBg` stays a boolean, the
  shader constants stay as they are, and there are two ground modes, not three. If you find yourself
  proposing shader uniforms, you have misread this brief.
- **Do not add billing, paywalls, or lock UI.** These styles are *intended* to become paid-unlock
  items later, but that is a separate job. Build them all as freely selectable for now. Just don't
  design the enum in a way that makes adding a locked flag later painful.

## 4. The open design question — ask me this before writing code

The three new styles were all judged by me against a **black** ground. Two of the ground modes
exist, and on the Aura ground the phase colour currently saturates most of the screen. **Drain in
particular has nothing to push against on a screen that is already solid colour** — draining it just
reveals more of the same.

So: do the new styles apply in both grounds, only in Minimal, or do they adapt (e.g. render in
white/black on the Aura ground instead of the phase colour)? **I have not decided. Ask me, show me
the options, and let me choose.** Do not pick one for me.

## 5. The four styles

Keep **Split** (existing) as the default. Add:

- **Bar** — a single horizontal line near the bottom edge, draining left→right. The plainest one,
  and the natural partner to Minimal mode.
- **Drain** — the colour empties downward and black takes the screen from the top, like a level
  falling. Subject to §4.
- **Ring** — a thick arc orbiting the numeral. **Watch the geometry**: the clock is pinned to the
  widest value the count passes through, so two digits fill roughly 77% of the screen width. A ring
  enclosing them needs almost the full width — clearance is only a few percent a side. Verify on
  device, and check it separately on the Flip 7's cover screen, which is far squarer than 9:19.5.

**Rejected, do not build:** dissolve, corner arms, segments, rise/fill, numeral fill, fade, breath.
**Unresolved, ask before building:** iris (I was leaning no), tide, horizon.

Visual references (same maths, but note the Perimeter tile in the first one draws a single loop and
is therefore *not* faithful to `SplitProgress`):
- https://claude.ai/code/artifact/e33d4352-faf5-4683-8309-41864633bcd1

## 6. Architecture notes

- **Settings**: a new enum, persisted by name, mirroring how `Palette` already does it —
  `runCatching { Enum.valueOf(...) }.getOrDefault(DEFAULT)` so an unknown value from a later
  version degrades instead of throwing on launch (`Settings.kt:97`). Setter named `update*()`, not
  `set*()` — `set*` clashes with the `mutableStateOf` delegate.
- **Where the picker goes**: the Theme card in the settings screen (`MainActivity.kt` ~1819) is the
  obvious home, since this is orthogonal to the palette in the same way Minimal is. Propose the
  placement, don't just pick.
- **All platforms in one change.** Phone/watch/iOS drift is this project's confirmed recurring bug
  class — a fix landing on one side only. `wear/` still has **no test sourceset**, so nothing
  guards the pair mechanically. Decide with me whether the watch gets styles at all; its timer
  screen is deliberately flat, English, numbers-only.
- The shipping `SplitProgress` should become one case of whatever abstraction you introduce, not a
  special case beside it. But don't build a plugin framework for four shapes.

## 7. House rules that will get you in trouble if you miss them

- **I direct all aesthetics. Do not self-tune taste.** Show me options and let me choose. This has
  been stated firmly before and it still holds.
- **"We don't have to explain everything. We're trying to be minimal. Explanations are stupid.
  Intuition is king."** Prefer a visual or structural affordance over a titled row with a subtitle.
- **Never show a scrollbar** in any UI. Absolute rule.
- **Ask before you commit or push.** Every logical change is its own commit so it can be reverted.
- Android has no backdrop blur — don't try to fake one. iOS gets the real thing via
  `.ultraThinMaterial`.

## 8. Verifying animation — read this or you will report a false pass

- **End-state screenshots cannot verify an animation.** They have passed while cards streaked across
  the screen. Record video and diff frames.
- Android: `adb shell settings put global animator_duration_scale 10` (Compose honours it) +
  `adb shell screenrecord --display-id <id>`, then `ffmpeg -vf fps=25`. **Set the scale back to 1
  afterwards** and record once at 1 to prove the real feel.
- **The Flip 7 has TWO displays**, so `screencap`/`screenrecord` need `--display-id`
  (`dumpsys SurfaceFlinger --display-id`) or a warning line gets prepended and corrupts the PNG.
- iOS: `xcrun simctl io booted recordVideo --force out.mp4`, drive the gesture, `kill -INT`.
- This Mac has **Pillow only** — no ImageMagick, no numpy. In zsh, a glob with no matches aborts the
  whole `&&` chain.
- The device is not always attached. Check `adb devices` first; the phone connects over USB
  (serial `R5CY70EP2GE`) or wireless adb, and I have to be around for either.

## 9. What I want back

Start by reading `Aura.kt`, `TimerView.swift` and `TimerScreen` in `MainActivity.kt`, then come back
with the §4 question answered as options for me to choose from, plus a short plan of the enum shape
and where the picker lives. **Then** build, one platform at a time, with a device check between.
