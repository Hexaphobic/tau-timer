# iOS / iPhone port — assessment

**Date:** 2026-07-29 (revised same day) · **Verdict: nothing in this repo runs on iOS today, but a
real iPhone version is a normal port. Scope is the UI layer and the clock's plumbing, not the app's
premise.**

> **Revision note.** The first version of this document claimed background cues were
> "architecturally illegal" on iOS. **That was wrong.** iOS has a supported, long-standing mechanism
> for exactly this — background audio mode — and it is how every interval timer on the App Store
> works. The corrected analysis is in §"Background cues on iOS" below. The first version also framed
> the watch as a blocker; the watch is out of scope, so `:wear` simply stays an Android-only module
> and is not a porting concern at all.

Below is what's actually here, what the port costs, and the one part that's already portable.

## Background cues on iOS — how it actually works

**Sound with the screen off: fully supported.** Add `audio` to `UIBackgroundModes` and set the
session category to `AVAudioSession.Category.playback`. While the app holds an active audio session
it keeps running — audio continues when the screen locks, and `.playback` deliberately plays *through
the silent switch*, which is what an interval timer wants. Because the app stays alive, its own
in-process clock keeps ticking, so **the existing architecture ports almost directly**: the
`elapsedRealtime()`-anchored `Workout.progressAt()` design becomes a `CACurrentMediaTime()`-anchored
one, and the cue scheduler stays as it is. What has no iOS counterpart is the *implementation* —
`Service` + `foregroundServiceType=specialUse` + `PARTIAL_WAKE_LOCK` — not the capability.

**Do not try to do it with pre-scheduled local notifications.** iOS keeps only the **soonest-firing
64 pending notification requests per app and silently discards the rest** — a hard system limit since
iOS 3. This app schedules five cues per interval (5 s warning, ticks at 3/2/1, GO at the boundary),
so a 16-interval Tabata needs 80 and a 20-interval sequence needs 100. Notifications alone break on
ordinary workouts. They're a reasonable *backstop* for the final "done" alert; they cannot be the
engine.

**Background haptics are unavailable on iOS, and it does not matter here.** CoreHaptics calls its
`stoppedHandler` the moment the app backgrounds and `UIFeedbackGenerator` produces nothing outside the
foreground-active state — but **the phone is not supposed to buzz on either platform.** That is a
locked design decision, not an omission: the phone sits on the ground, so it signals with sound; the
watch is on your wrist, so it signals with haptics. Android already implements exactly this — `:app`
declares no `VIBRATE` permission and contains no `Vibrator` use at all, and every buzz lives in
`wear/.../timer/Vibrations.kt`. iOS inherits the same split, so the platform limitation costs nothing.

*(The phone does use four `performHapticFeedback` calls for drag-gesture confirmation in the preset
editor — `ui/DragReorder.kt` and `ui/PresetScreens.kt`. That is finger-on-glass feedback while you are
actively dragging a row, not a workout cue, and it goes through the system view-haptics path. Distinct
from "the phone buzzing at you mid-set".)*

**Mute needs no compensation.** Muted means the user is watching the screen, so no cue fallback is
required. That path is self-consistent on iOS: the timer screen already forces the display awake
(`keepScreenOn` on Android → `isIdleTimerDisabled` on iOS), so a muted workout is foreground with the
screen lit and the in-process clock runs without needing an audio session to stay resident.

**A useful property of the existing design:** `Workout.progressAt()` derives every reading from one
absolute anchor and never accumulates per-tick deltas. So even if iOS *does* suspend the app, the
reading is still correct the instant it resumes — suspension can cost you cues, never clock accuracy.
That is what makes this port low-risk rather than a rewrite of the timer.

Practical note: background audio mode is declared at App Review, and a timer that genuinely plays
audio cues is a well-trodden, routinely-approved case — but an app that only plays *silence* to stay
resident does draw scrutiny, so keep the real cues the reason the session exists.

## What the codebase is

5,837 lines of Kotlin in two Android Gradle modules (`:app`, `:wear`). Import census over
`app/src/main` + `wear/src/main`:

| Dependency | Import sites | iOS availability |
|---|---|---|
| `androidx.compose.*` | 296 | Only via Compose Multiplatform (whole-module KMP conversion) |
| `android.os.*` | 21 | None |
| `android.content.*` | 20 | None |
| `android.app.*` | 9 | None |
| `com.google.android.gms.wearable` | 8 | None — and see "watch" below |
| `androidx.wear.compose.*` | 6 | None (watchOS is a separate SDK) |
| `android.media.*` | 4 | Replaced by AVFoundation |
| `org.json` | 3 | Replaced by Foundation |
| `android.graphics.RuntimeShader` | 1 | None — AGSL is Android-only |

There is no Xcode project, no `iosMain` source set, no Kotlin Multiplatform or Compose Multiplatform
plugin, and no Swift. A search for `swift|xcode|UIKit|SwiftUI|kotlin-multiplatform|iosArm64|cocoapods|
CFBundle` across the whole repo returns zero hits.

## The actual work, largest first

**1. All 3,700-odd lines of UI are Jetpack Compose.** This is the bulk of the port and the one
decision everything else hangs off: rewrite the UI in SwiftUI, or convert to Compose Multiplatform
and keep one UI codebase. `:wear` stays Android-only either way and does not participate.

**2. The clock's plumbing gets re-hosted.** `service/TimerService.kt` — the FGS, the wake lock, the
notification — is replaced by an `AVAudioSession` in background audio mode (see above). The *logic*
inside it survives: the anchored clock, the cue list, the stale-snapshot guards. Budget this as a
rewrite of the hosting layer, not of the timer.

**3. The visual identity is AGSL.** `ui/Aura.kt` carries two hand-written AGSL shaders — the timer
bloom and the home aurora — driven through `android.graphics.RuntimeShader`. AGSL is Android-only, so
either port both to Metal/SkSL or ship iOS on the gradient fallbacks that already exist in that file
for pre-API-33 devices.

**4. Platform-adjacent swaps, all mechanical:** `SharedPreferences` → `UserDefaults`; `SoundPool` +
`AudioFocusRequest(MAY_DUCK)` → `AVAudioPlayer` + `AVAudioSession(.duckOthers)`; `org.json` +
`filesDir` → `Codable` + app container; `keepScreenOn` → `isIdleTimerDisabled`; Android resources,
adaptive icons and `themes.xml` → asset catalogs. No haptics layer to port — the phone doesn't buzz.

**5. Samsung-specific work simply doesn't come along.** The `compact` layout (`COMPACT_ASPECT`,
`COMPACT_COUNTER_WIDTH_DP`, `COMPACT_EDGE_DP` and the compact branches in
`TimerScreen`/`TimerContent`) exists to lay the timer out around the Z Flip 7 cover display's camera
housing. Harmless to leave Android-only; no iPhone analogue.

**6. Distribution changes shape.** This app is sideloaded as an APK. iOS has no sideloading: an
iPhone build needs an Apple Developer account, Xcode, provisioning profiles, and TestFlight or the
App Store. Get the account started early — it is the one item with a waiting period.

## What *is* already portable

Six files — **584 lines** — have zero `android`/`androidx`/`com.google`/`org.json`/coroutine imports
and would compile on any Kotlin target as-is:

| File | Lines |
|---|---|
| `app/.../model/Numbers.kt` | 156 |
| `app/.../model/Preset.kt` | 142 |
| `wear/.../timer/Timer.kt` | 106 *(a deliberate duplicate of the phone model)* |
| `app/.../model/Workout.kt` | 89 |
| `app/.../model/Language.kt` | 57 |
| `app/.../model/TimerUiState.kt` | 34 |

`ui/Cistercian.kt` is a near-miss: its `cistercian()` / `cistercianSeconds()` logic is pure and only
`CistercianNumeral` needs Compose, so it splits cleanly if it ever has to.

That's the drift-free clock, the preset model, the numeral/word rendering for all 11 languages, and
the Cistercian cipher — the genuinely interesting logic — plus the existing JVM test suite that
covers it. Any future iOS or KMP effort starts from here, and starts intact.

## What I did not do, and why

**I did not write a speculative Swift/SwiftUI port.** An untested several-thousand-line rewrite,
produced with no device and no Xcode project to check it against, is a liability rather than a
deliverable.

**I did not extract the 584 portable lines into a `:shared` KMP module.** At the time of writing there
was no iOS consumer, which made it scaffolding. **That reasoning no longer applies** — the port is now
a declared, funded intention, so a `:shared` module is step 2 below rather than something to skip.

## DECISION (2026-07-30): native SwiftUI, no Kotlin sharing

**Chosen: a standalone SwiftUI app.** Compose Multiplatform was considered and rejected — the user
wants a native-feeling iPhone app and is treating this as a one-and-done build rather than a codebase
to co-evolve with Android.

**The consequence to be clear-eyed about: there is no `:shared` module, so nothing is reused.** The
584 "already portable" lines are only portable *to another Kotlin target*. A pure-Swift app reports
them by hand:

| Ported to Swift | Lines |
|---|---|
| UI (all Compose screens, glass/drag/aura) | ~3,700 |
| Model (`Numbers`, `Preset`, `Workout`, `Language`, `TimerUiState`, Cistercian logic) | ~540 |
| Clock host (`TimerService` → `AVAudioSession` background audio) | ~300 |
| Tests (37 JUnit → XCTest) | ~370 |

So the realistic scope is **~5,000 lines rewritten**, not "the UI plus some glue". `:wear` stays
Android-only and is untouched. The Android app is also untouched — this is an additive second app,
not a migration, so nothing already shipped is put at risk.

## BUILT (2026-07-30) — `ios/`

The port is done and building. `xcodebuild -scheme IntervalTimer` produces a signed-on-demand
`Interval Timer.app` that launches, runs a workout and renders every screen. The Android app and
`:wear` are untouched — this is an additive second app, not a migration.

```
ios/
  IntervalTimerCore/          SwiftPM package — pure logic, no UIKit/SwiftUI. `swift test` runs it
                              with no simulator, no signing, no Xcode project. 38 tests, green.
  IntervalTimer/              the app: Settings, PresetStore, TimerEngine, Beeper + UI/
  IntervalTimer.xcodeproj/    iOS 17+, portrait, Swift 5 mode, bundle id com.chrispoole.intervaltimer
```

**The clock host.** `Beeper` holds a `.playback` + `.mixWithOthers` session and a running
`AVAudioEngine`; `TimerEngine` ticks at 33ms off `CLOCK_MONOTONIC` and feeds the same
`Workout.progressAt()` the Android service does. Two things worth knowing:

- **`CLOCK_MONOTONIC`, not `CACurrentMediaTime()`.** The latter (and `CLOCK_UPTIME_RAW`) stops while
  the system sleeps, which would quietly shorten any workout run with the screen off.
  `CLOCK_MONOTONIC` on Darwin keeps counting through sleep — that is the real counterpart of
  `elapsedRealtime()`.
- **A looping silent buffer rides alongside the cues.** The engine's output unit renders
  continuously, but a graph with nothing scheduled on it is something iOS may treat as "not
  playing", and a suspended app is a stopped workout. Four lines to remove the doubt.

Ducking re-applies the category with and without `.duckOthers` around the final-three cluster: iOS
has no per-sound duck, and deactivating the session would end background execution mid-workout. It
fails soft — a throw there costs the duck, not the cue.

**The shaders came along.** Both AGSL programs are now Metal, driven by SwiftUI's `.colorEffect`, so
the timer bloom and the home aurora are the real thing rather than the gradient fallback. Two
transcription traps, both found by measuring the rendered pixels rather than by eye:

- AGSL accepts `smoothstep(hi, lo, x)` as a descending ramp; **Metal does not** — it is undefined for
  `edge0 >= edge1` and with fast math returns 1 everywhere, which turned both shaders into a flat
  wash. Written as `1 - smoothstep(lo, hi, x)`.
- **SwiftUI runs shaders in the same sRGB-encoded space AGSL does**, so no colour transcode belongs
  at the boundary. An earlier version of this file said the opposite and the shaders encoded going in
  and decoded coming out; that is what made every theme read visibly darker than Android. Settled by
  A/B against the device rather than by argument — the theme swatches are the same frozen shader with
  the same seed on both platforms, so the same input must give the same pixel. Default's prepare
  swatch: **(143, 121, 176)** on a Flip 7 over adb, **(147, 121, 181)** predicted by the untouched
  maths, **(96, 83, 116)** on iOS with the transcode in and (99, 86, 119) predicted for that
  double-encoded path. Transcode removed; iOS now measures (145, 119, 178).

**One real bug fixed, in shared logic.** `widestClockLines` pinned the clock's font size to the
value an interval *starts* at. Composed numerals don't shrink with the number — a 30s interval opens
on 三十, two glyphs, and a second later shows 二十九, which is three — so the Chinese clock ran off
both edges of the screen. It now takes the widest value the count actually passes through.
**This bug is still live in the Kotlin** (`model/Numbers.kt`), which has the identical logic.
`testWidestLineCoversEveryValueTheCountPassesThrough` guards it: 27 failures with the old behaviour
reinstated, 0 with the fix.

**Dropped on purpose:** the Z Flip cover-screen `compact` layout. No iPhone has that geometry.

**Debug affordance:** `simctl launch <id> -startScreen presets|settings|editor|timer` opens straight
onto a screen. `#if DEBUG` only. Note it always takes the *sequence* path (`homePreset(...)
.toWorkout`), so a workout of N rounds arrives at the timer as 2N-1 interval positions — which is
what the round counter shows. The home screen's own GO uses `baseWorkout` for a single section and
counts rounds.

**Display corner radius** (`UI/Aura.swift`) is a table keyed on screen point size, because iOS has no
public API for it and `_displayCornerRadius` is an App Store risk. Ambiguous sizes take the larger
value: overshooting tucks the perimeter arm further inside the glass, undershooting runs it off the
corner. The simulator's framebuffer is an unmasked rectangle, so this cannot be measured from a
`simctl` screenshot — only seen in the simulator window's own bezel.

## Still to do, and what has not been verified

Everything below needs a real iPhone or an account; none of it is a code gap.

1. **Apple Developer account.** Still the only item with a queue, and now the only thing between this
   and TestFlight. Nothing else is blocked on it.
2. **A screen-locked workout, end to end, on a device.** The simulator does not model suspension
   honestly, so residency is the one claim resting on the mechanism being right rather than on
   having been watched. The engine starts and `AURemoteIO` runs — that much is in the log.
3. **Audio against real music**: that the cues are audible over a track and that the duck engages for
   the final three and releases at GO. The category toggles fire at the right instants in the log;
   what they sound like is untested.
4. **Every touch interaction** — drag-to-reorder in both editors, hold-to-pause, the two-tap delete.
   They are written, they compile, and nothing has ever tapped them.

Nothing here is a product decision left open. Phone signals with sound, watch signals with haptics,
mute means you're looking at the screen — all settled.
