# Punch list — 2026-07-30, from the first pass over both apps

---

## How to actually see an animation on Android

Screenshots of end states prove nothing, and mid-transition frames can't be caught at real speed —
the tool round-trip is about a second. **Don't edit durations in the source to slow things down**;
that was the old method and it needs a rebuild each way. Instead:

```bash
adb shell settings put global animator_duration_scale 10   # Compose honours this
adb shell screenrecord --time-limit 9 --display-id <id> --size 540x1260 /sdcard/a.mp4 &
```

Tap, pull the file, `ffmpeg -ss <t> -t <dur> -i a.mp4 -vf fps=25 f_%02d.png`, and montage the frames
with Pillow (no ImageMagick on this machine; `numpy` isn't installed either — `Image.resize((1,h))`
gives per-row averages if you need to measure). **Put the scale back to 1 afterwards.** Recording at
scale 1 and pulling 25fps also works and is what proves the final feel.

Two traps: the Flip 7 has two displays, so `screencap`/`screenrecord` need `--display-id`
(`dumpsys SurfaceFlinger --display-id`) or they prepend a warning to the PNG and corrupt it. And a
zsh glob with no matches aborts the whole `&&` chain silently.

---

Verdict on the port: *"Your port over seems good on both of them."*

**Items 1–9 are done and verified on real hardware** (Flip 7 over adb, iPhone 17 Pro simulator).
Item 10 is a design question and is deliberately not built. Nothing is committed.

---

## Done

### 1. Progress pips: always squares, rows split evenly — both platforms

One rule, one place: `model/Pips.kt` and `IntervalTimerCore/Pips.swift`, with mirrored tests.

- **Under 16** stays on one line, even past the cap — twelve in a row is still countable, and
  splitting it would waste a second line on six.
- **16–32** wraps into rows of at most 8, **split as evenly as possible**: 17 is 6/6/5, never 8/8/1.
- **Past 32** it degrades to a bar, as before.
- Wide pills are gone. Every count draws the same square, sized off the width a full row of eight
  needs, with short rows centred — so three rounds and one row of a twenty-four-round workout look
  the same.

Verified: 12 → one row of 12 on the Flip; 17 → 6/6/5 on the simulator. Tests are non-vacuous —
reinstating the old fill-to-cap rule fails 3 of 4 Kotlin tests and 23 Swift assertions.

### 2. Settings card "FUN" → "THEME" — both platforms

The picker's own "Theme" heading came out with it; two of those in a row read as a section inside a
section. The Minimal toggle stays on the card — it is a look, and it sits below the swatches.

### 3. Reorder: rows that move aside now animate — iOS

`ReorderState.offset(for:)` returned a raw ±pitch. Split into `dragOffset(for:)` (the finger, raw)
and `shift(for:)` (the step aside, sprung), applied by one `reorderOffset` modifier — two `.offset`s
rather than one sum, so the spring can't put the dragged card behind the finger. The spring is
Android's `animateItem` placement default (`StiffnessMediumLow`, no bounce) written in SwiftUI's
terms. `isFloating` also now keeps the dropped card on top for the length of its settle, instead of
dropping it behind its neighbour for the last 300ms of a drag.

### 4. iOS toggles flip on tap

Reproduced first: a synthetic tap dead on the knob left the value unchanged, while a palette swatch
in the same card flipped every time. `Toggle` is a UIKit control and the enclosing `ScrollView`
delays touches to its content long enough that a quick press never lands; a SwiftUI tap gesture is
not delayed. The switch is now an indicator (`allowsHitTesting(false)`) and the **whole row** owns
the tap, so the label is a target too. VoiceOver is handed a live `Toggle` via
`accessibilityRepresentation`. Covers all five toggles, not just the two named.

### 5. iOS theme colours matched to Android

This was **my bug, and the earlier diagnosis was backwards.** The shaders had an sRGB transcode at
the boundary on the theory that SwiftUI works in linear extended sRGB while AGSL works sRGB-encoded.
It doesn't — `.colorEffect` hands colours in and reads them back sRGB-encoded, exactly like AGSL. The
transcode double-encoded going in and double-decoded coming out, which is the whole of "everything
looks darker."

Settled by measurement, not argument: the theme swatches are the same frozen shader with the same
seed on both platforms, so the same input must give the same pixel. Default's prepare swatch —

| | value |
|---|---|
| predicted, no transcode | (147, 121, 181) |
| **Flip 7 over adb** | **(143, 121, 176)** |
| predicted, double-encoded | (99, 86, 119) |
| iOS *before* | (96, 83, 116) |
| **iOS after** | **(145, 119, 178)** |

Transcode deleted from both shaders. No brightness knob was added — the tuning was never wrong.

### 6. iOS perimeter arms no longer clipped at the corners

`SidePath` hard-coded `r = 40` (the Android value, which the Flip's tighter corners tolerate). Now
`displayCornerRadius - 6`, making the arm a rounded rect **concentric** with the display — it runs
exactly 6pt from the glass the whole way round, not just down the straight sides. iOS has no public
corner-radius API and `_displayCornerRadius` is an App Store risk, so it is a table keyed on screen
point size, taking the larger value where two devices share a size (overshoot tucks the arm in;
undershoot clips).

### 7. Word-mode description shortened — both platforms

> Thirty-two, not 32 (under 60s only). Languages with their own numerals keep them.

Leads with the example, ceiling in parentheses. **Shipped without a yes** — say the word and it
changes; it is one string in two files.

### 8. Android: dragged card no longer draws behind its neighbour

`zIndex` was on the card *inside* the item wrapper. `zIndex` only orders siblings of the layout it is
applied to, so down there it was ordering a single child against nothing and the lazy list went on
painting items in index order — a card dragged downward was painted under the one it was passing.
Moved onto the item's own root. Confirmed mid-drag on the Flip: both cards visible, lifted one on
top, through the swap.

### 9. Android: card edges no longer clipped when it grows

`animateContentSize` applies `clipToBounds` at the size it is animating, so it was cutting the 1.03
lift scale and the drop shadow off at the card's own edges. It now steps aside while a card is
floating — nothing resizes mid-drag anyway.

---

## Second pass — 2026-07-30, from the simulator

### 11. The language grid ignores Word mode — both platforms

*"everything looks like it's permanently stuck inside of word mode. Whether or not you turn the
slider on or off, they all look the same."*

The timer was always right; the grid was not. Its tiles spelled whatever the setting said, on the
grounds that English, Russian, Spanish and French otherwise print the same Western 9 and the tiles
would be indistinguishable. They aren't — the phase word up top is already Work / Работа / Trabajo /
Effort. Since the grid sits directly under the switch, the effect was that flipping it moved nothing.
It follows the switch now.

### 12. Back / Cancel row pinned on the scrolling screens — both platforms

Settings, Presets and the sequence editor kept their header inside the scroll view, so the only way
off the screen scrolled away. Now outside it: `ScreenHeader` on iOS, a plain Row above the scroll on
Android. The editor pins its Cancel / Start / Save row the same way; its name field and legend still
scroll. `FIRST_CARD` is unchanged — the LazyColumn header item still exists.

### 13. Real blur on a lifted card — iOS only

*"they still have zero blur and they have a frost effect, but that's not blur."* Correct: the lift
was a flat white wash.

**iOS** does the real thing: `liftedCard` fills with `.ultraThinMaterial`, which samples what is
actually behind the card, so the card underneath goes soft as this one passes over it.

**Android does not, by decision.** It has no backdrop blur — `Modifier.blur` (API 31+) blurs a
composable's *own* content, nothing public samples what is behind one, and the Compose BOM here
(2024.09.00) has no backdrop API. A depth-of-field stand-in was tried (blur every card except the one
in your hand) and **removed on sight**: it blurred the card being passed rather than the space behind
the dragged one, and it reintroduced the item-13 z-order artefact while scrolling.

**The trap that cost item 9 twice.** `Modifier.blur` compiles to a `graphicsLayer` with
`clip = edgeTreatment.shape != null` — and the default `BlurredEdgeTreatment.Rectangle` has a shape,
so **the layer clips at any radius, including zero.** Putting `.blur(0.dp)` on the floating card's
wrapper silently re-clipped its 1.03 lift scale and its shadow to the unscaled bounds, which is
exactly the "cut off on the top, left and right" that item 9 was supposed to have fixed. With the
blur gone the lifted card measures 961px against the resting card's 935px — the full 1.03, corners
and shadow intact.

If the two platforms must match, the only route is a third-party backdrop-blur library (haze). Not
taken — a dependency for one visual effect.

### 14. iOS drag had no vertical boundary

A card could be carried anywhere: off the top of the list, over the header, past the last slot. The
Kotlin has clamped since the beginning (`DragDropState.clampToRegion`) and the port went out without
it. `ReorderState.clamped(_:from:)` now holds the card inside the stack — it can reach the first and
last slot and no further. Simpler than the Kotlin because the list can't scroll under the finger here
(`scrollDisabled` for the length of the drag), so there is no viewport case and no auto-scroll.

It constrains position only; nothing is clipped or masked. Bounds verified against a three-row stack
of unequal heights, including the unmeasured-layout case (passes through rather than pinning at 0)
and the single-row case (cannot move).

---

## Third pass — 2026-07-30, a section becomes a sequence

### 15. `+ interval` inside every section — both platforms

> "It could be like work, work, rest, for example, which would have to have a very simple plus icon
> that's contained within the box." … "I think it needs to be the total: how much do I repeat this
> whole entire block? Not the individual components."

The editor has always worked exactly this way, so the home screen catches up to the model rather than
gaining one of its own: **`HomeBlock(workSec, restSec, rounds)` is deleted and a home section is now
the same `Block(items, repeat)` the editor uses.** The ×N keeps the only meaning it ever had — run
this whole section that many times.

- **The plain home is work, rest, rounds and nothing else.** No `+`, no ✕, no tap-to-flip — all of
  that lives behind "Add intervals", which is the door to the grouped view. (I first built it with a
  `+` on the plain home, on the strength of an up-front answer to that exact question; seen on screen
  it was clutter on the one screen that should have none, and the user reversed it. `tinted` /
  `compact` does double duty as "this is a card" and gates the lot.)
- `solo` is now "one section, holding work and optionally rest", not just "one section" — so a single
  section that grows a third interval folds into a card, and deleting back down to work + rest folds
  it out again. Both directions verified on device.
- **The +** appends alternating work/rest, because that is what nearly every section wants. **Tap a
  row's label** to flip it — that is how you get work, work, rest. Its own hit box, well clear of the
  steppers: the editor learned the hard way that a whole-row tap turns a near-miss on − into a phase
  change.
- **✕ per row, cards only.** A section of one interval is the floor, and the plain home stays bare —
  dialling a rest to 0 is still how you drop it there.
- **Rest may still be 0.** The interval stays in the section so the number is there to dial back up;
  `homePreset` drops zero-length intervals at playback. Work keeps its 5s floor.
- **The round counter** still reads "3 / 8" for the classic shape and only that: `Block.isBasic` is
  what decides whether GO builds a `baseWorkout` or a sequence. Two sections, or one holding
  work/work/rest, have no single round to count and fall to interval positions.

### 16. The whole home layout persists — both platforms

`Settings` stored `workSec`/`restSec`/`rounds`, which were read in exactly one place: seeding the
home. They are replaced by the layout itself, as JSON, in the same wire shape both platforms use
(`{"items":[{"phase","sec"}],"repeat"}`). An install carrying the old three values falls back to them
on first launch and writes the new form, so nothing is lost. Unreadable JSON falls back rather than
starting on an empty home, and a section that decodes to no intervals is dropped — a card with none
would be an empty box you couldn't delete.

Verified on both: build work/work/work ×3, kill the app, relaunch, and it comes back.

### 17. "Add intervals" is a box closing, not a crossfade — both platforms

> "make it so the current one that says 'Work and Rest' has a box appear around it, and then it floats
> and duplicates in there to the two … the same thing for the reverse animation when I delete it.
> When it goes back to main, it should be the box being freed."

The cause was structural and identical on both: the plain home and the card were two branches of an
`if`, so the framework threw away the rows you were looking at and built a fresh pair inside a card.
Nothing short of unifying them can fix that — no amount of animation tuning survives the subtree
being replaced.

Both are now **one** view whose chrome is driven by a single 0→1 number (`sectionChrome(boxed:)` on
iOS, `HomeSection(boxed:)` on Android). The work and rest rows are the same rows the whole way
through; what animates is the box arriving around them, the header unfolding from the top edge, the
Rounds stepper leaving toward the bottom, and the rows taking their tint. Reverse plays the same move
backwards — the box is let go and the rows stay.

The copy is a second beat, not simultaneous: its spring is delayed 0.14s on iOS and its fade 0.24s on
Android, so the box lands before the duplicate pushes out below it.

**Verified frame by frame**, which is the only way to check an animation: temporarily scaled the
durations ~8× on each platform, captured a burst (`simctl io` / `adb exec-out screencap`), picked the
frames, then restored the real timings. The mid-transition frames show the untinted Work/Rest rows
still in place with the box forming around them — i.e. continuity, not a dissolve.

#### 17a. …and on Android it was still tearing. Two clippers and a hole.

> "It looks good on iPhone … but the problem is that it's not clean on Android. On Android, it's
> buggy."

iOS was fine as shipped and is untouched. Android had three separate defects, all invisible in an
end-state screenshot and all obvious in a 10×-slowed screen recording:

1. **The card was sliced by a hard horizontal edge mid-transition**, cutting straight through the
   "Rounds" row. `animateContentSize` clips to the size it is animating, and it was chasing a height
   that had already jumped, so it drew the card shorter than its contents.
2. **The height jumped because the chrome was behind `if`s** — `if (box > 0.01f)` for the header,
   `if (box < 0.99f)` for the Rounds stepper. Each appeared or vanished at *full size in one frame*,
   so the section grew a whole row instantly at the start and dropped one instantly at the end.
   Alpha was animated; size never was.
3. **A section-sized hole opened below it.** A new lazy item claims its full space the instant it's
   added, but `fadeInSpec = tween(220, delayMillis = 240)` meant nothing was drawn there for a
   quarter second. The gap appeared, sat empty, then the card popped into it.

Fix: animate the *height*, not just the alpha. Both pieces of chrome are now
`AnimatedVisibility(… expandVertically / shrinkVertically)` — header from the top edge, Rounds toward
the bottom — so the section's own height is continuous frame to frame. That makes the lazy list
re-lay-out every frame, which carries everything below along for free, so `animateContentSize` could
go entirely (and with it the slicing). The fade delay went too; the new section now fades in as the
space opens instead of after it.

Measured before and after at 10× and at 1× (method above). Before: the whole layout reached its final
positions in a single capture interval, with the sliced card and the empty gap. After: a continuous
~280ms grow in both directions, no clip line, no hole. The reverse — the box being let go — is the
same move backwards.

Not changed, on the user's call: GO still travels to the bottom as the list grows. *"Go moves down to
the very bottom, which makes sense … just for simplicity, we should keep [it] the same."*

#### 17b. The `+` circles were ellipses on the plain home — Android only

> "the plus icon on … work and rest, when there are no additional intervals, is squished in a little
> bit. They're not perfectly round."

Measured, not eyeballed: **144 × 162 px** (48dp × 54dp) for Work's and Rest's `+`, against 162 × 162
for every other circle on the screen.

`Stepper` treats a non-null `tint` as "this row is a pill" and adds `padding(horizontal = 12.dp)`.
The plain home passes the phase colour **at alpha 0** so it can fade up when the box arrives — so the
rows were paying a pill's 24dp inset for a pill nobody can see. That left 288dp for a row needing
294dp, and `Modifier.size` coerces into whatever the parent leaves rather than overflowing, so the
last child in the row — the `+` — was silently squashed by 6dp and drew as an ellipse. Same reason
Work and Rest sat 12dp in from Rounds.

Fix is one local: `val visibleTint = tint?.takeIf { it.alpha > 0.01f }`, used for the chrome branch
and the glow clock. After: all six circles 162 × 162, ratio 1.000, and all three rows share a left
edge. The card state was never affected (70 + 40 + 72 + 40 + 2 + 32 = 256dp inside 260dp) and
measures round as well.

**iOS never had this** — it passes `tint: tinted ? colour : nil`, a real nil, and already keys its
`.padding(.horizontal, tint == nil ? 0 : 12)` off that. The divergence is that Android fades the
tint in and iOS switches it.

### 18. Watch pause menu is glyphs, not words

Two stacked word pills — "Resume" over "End" — ate most of a round screen and read as a sentence
where the phone shows a thing to hit. Now the same pair the phone's pause screen uses: a green play
triangle and a red ✕, side by side, 60dp each (the phone's are 96dp; two plus the gap is 138dp, which
clears the usable width of a 480×480 face with room either side).

Drawn on a Canvas rather than typed, for the same reason the phone does it: no font is guaranteed to
carry ▶, and a missing-glyph box on the control that gets you moving again is not a risk worth taking.
The play triangle's vertices are chosen so its *centroid* lands on centre — a box-centred one always
looks shifted left.

Verified on the watch over adb: long-press pauses, play resumes into Work 1/8, ✕ ends and returns
home. The finish screen's "Done" pill is untouched — different state, and it wasn't part of the ask.

---

## Fourth pass — 2026-07-30, grouping and chrome

### 19. The outer ×N is a box, not a sentence

> "I wanted to visually show that, like highlighting all of the sections in its own section, and that
> section has a plus or minus. … We don't have to explain everything. We're trying to be minimal.
> Explanations are stupid. Intuition is king."

First attempt was the preset editor's `RepeatAllCard` — a titled row reading "Repeat everything /
Plays through once / × 1" — dropped on the user's call. What shipped instead: **one rounded frame
drawn around the ×N header and every section under it**, with the `+` and GO outside it. The
hierarchy now reads structurally — outer box (×N) contains section boxes (×N each) contain interval
rows — with no words anywhere in it.

The frame is painted from the list's own layout on Android (`drawBehind` reading
`listState.layoutInfo`), not composed around the items. The sections have to stay separate lazy
items for drag-reorder to work, and a card carried out of the stack must not take a slice of the
frame with it. On iOS the sections are already one `VStack`, so it is a plain background.

Scrolled past either end, that end runs off screen rather than clamping — clamping to the last
*visible* item would draw a rounded corner in the middle of the stack.

Width had to be found for it: the frame's 8dp inset plus the card's own padding re-squeezed the
carded rows into the same ellipse as §17b. Reclaimed 16dp — list gutter 24→20, card padding 14→12,
compact label 70→66 and value 72→68 — leaving 8dp of slack. Measured: every circle 120×120, ratio
1.000.

`repeatAll` is stored under its own key (`homeRepeatAll`) rather than inside the home JSON — it
belongs to the screen, not to any section — and is applied only while the cards are showing, so a
leftover value can't silently double a plain single-section workout.

### 20. The back header is a floating pill with nothing written on it

> "get rid of that. The back arrow should just be tiny in the top-left corner. It should be just a
> floating pill that has a blur behind it, and it should not cut off the things that are scrolling
> underneath it."

`ScreenHeader` (back + a 24pt title, pinned above the scroll) is gone from both platforms, replaced
by `BackPill`: a 44pt disc in the top-left, overlaid on the scroll rather than stacked above it, so
content passes underneath instead of stopping at a hard edge. Applied to Settings and Presets on
both. The scroll content carries 60pt of top padding so nothing is hidden at rest.

**The blur is real on iOS only.** `.ultraThinMaterial` samples what is behind it, so the cards go
soft as they pass under the pill — verified in a scrolled screenshot. Android has no backdrop blur:
nothing public samples what is behind a composable, which is the same wall §12 hit. It gets the
app's glass instead — translucent enough to see content pass under it, with the standard 1dp
gradient edge. Not a stand-in for blur, just the honest Android version.

### 21. Release build smoke-tested on hardware

R8 + resource shrink had never been *run*, only built. Installed `app-release.apk` on the Flip 7:
launches, GO starts a workout, the prepare screen renders its Cistercian numeral and 12 pips, and
`logcat` shows no `AndroidRuntime`/`ClassNotFound`/`NoSuchMethod` from the app. Bundles build for
both modules (`app-release.aab` 3.2MB, `wear-release.aab` 2.4MB) — **debug-signed**, because
`keystore.properties` is absent.

### 22. iOS drag: the ScrollView was eating the gesture

> "if I drag in, what I let go, it doesn't even matter where I let go. They just shoot to the top and
> bottom of the screen."

Not a reorder bug at all — the reorder maths was right the whole time. Recorded the gesture with
`simctl io recordVideo` and stepped the frames: **the cards never lift. The page scrolls, and letting
go hands the ScrollView a fling.** That is the shooting to the top and bottom.

`DragGesture(minimumDistance: 4)` on the handle loses the race to the ScrollView's pan recogniser,
and `.scrollDisabled(reorder.isDragging)` can only flip once the drag has already recognised — too
late, the scroll owns the touch. It was a *race*, which is why it sometimes reordered correctly and
sometimes flung: an earlier full-length test drag reordered fine, and the very next recording
scrolled instead.

Fix: `minimumDistance: 0`, so the gesture is claimed on touch-down and the scroll is disabled before
there is any movement to steal. The pick-up haptic is held back until the finger has actually moved
4pt, because touch-down now counts as the gesture starting and a press that goes nowhere is not a
drag.

Re-recorded after the change: the layout is pinned across every frame of the drag — no scroll, no
fling. Android never had this; its handle consumes from the first pointer-down.

### 23. The group ×N moved to the foot, centred and large

> "I want that 1x to be at the bottom and to be centered and big so it's clear that it's trying to
> identify the entire thing."

Was a small right-aligned row at the top of the group box, which read as one more section header.
Now the last thing inside the frame, centred, 48pt circles and a 26pt number — the bottom line of the
group rather than a heading on it. Both platforms.

On Android that moved the item from index 1 to index `rows.size + 1`, so `firstCard` goes back to 1;
the frame's drawn range is unchanged (`1..rows.size + 1`) because it still spans the sections plus
the ×N, just from the other end.

### 24. The camera cutout no longer walls off the top

> "if there's a camera cutout, it's blocking all of the settings menu … I wanted to just ignore that
> and just go past it."

Settings and Presets dropped `safeDrawingPadding()` from their containers: the reserved cutout band
cost the menus that much height for a hole the content only overlaps in the middle. The scroll now
uses the whole panel and runs straight past the cutout. **Only the back pill keeps an inset**
(`Modifier.safeDrawingPadding()` on the pill itself), so the one control up there never lands under
the lens, and the scroll's top padding is `safeDrawing.top + 60dp` so nothing is hidden at rest.

The home screen and the sequence editor still reserve the band — not reported, not changed.


### 25. Rounds is one control, and it belongs to the home

> "If there is only one block, you don't need the extra 1x … when I do add an extra interval … say
> it says 4. I hit Add Interval, and that 4 holds for all the others for how many blocks for
> repeating the total … maybe the words 'rounds' should still be there."

Two bugs in one. A single section got a redundant outer ×1 governing one thing, and crossing from
one section to two left the old Rounds on *each* section while the group started at 1 — so a home
reading "4 rounds" quietly became 4 × 4 = **sixteen**.

The fix splits two ideas that had been sharing a flag:

- `solo` is about **chrome** — one plain work/rest section wears no card.
- `grouped` = `rows.count > 1` is about **repeats** — the outer ×N and the group box only exist once
  there is more than one section to wrap. A single section holding work/work/rest gets a card but no
  group, because its own repeat already *is* the total.

And there is now exactly **one** Rounds control, owned by the home rather than by a section. With one
section it reads and writes that section's repeat; with two or more it reads and writes `repeatAll`.
Crossing the boundary moves the number so it never changes under you:

| | before | after |
|---|---|---|
| add, 1 → 2 | blocks ×4 and ×4, group ×1 → 16 rounds | group ×4, blocks ×1 → **4 rounds** |
| remove, 2 → 1 | survivor keeps ×1, group ×4 discarded | survivor ×(own × group) → **4 rounds** |

Verified on both: 4 → add → still 4, both sections ×1 → delete → still 4. Lossless round trip.

The per-section header (grip, per-section ×N, section time, ✕) now arrives with the *second* section
— every one of those controls is meaningless when there is only one.

### 26. …and the animation is that control moving, not two controls swapping

Keeping Rounds as one composable is the whole animation. It used to collapse away while a separate
unlabelled ×N appeared at the foot of the box; now the control you were already using walks from the
plain home's left-aligned row — lined up with Work and Rest — to centred and larger under the group,
carrying its number with it. The word "Rounds" stays, both platforms.

Neither framework animates between two alignments, so both animate the *space either side* instead,
which is continuous and comes to the same thing: weighted spacers on Android (`outer` 0→1 against
`inner` 1→0, floored above zero because a weight of 0 is illegal), animated `maxWidth` caps on
flexible spacers in SwiftUI. Two traps found by looking: the collapsing inner gap butts the label
against the − (fixed 12pt sliver holds them apart), and SwiftUI will squeeze a `Text` before a
`Spacer` gives up width, which folded the label into "Roun / ds" until `.fixedSize()`. The iOS number
grows by `scaleEffect`, not by font size — a font-size change re-renders the glyph and snaps.

### 27. What the adversarial review of §25–26 caught

Twenty agents over five dimensions, every claim then handed to an independent skeptic told to refute
it. **15 raised, 9 refuted, 6 confirmed.** Four were mine and are fixed; two are older and are not.

**Fixed:**

1. **(high) The Rounds double-tap reset wrote to the wrong side of the boundary — Android only.**
   `Modifier.pointerInput(Unit)` launches its gesture loop once, on the first touch, and reads the
   handler it had *at that moment*; a constant key means it is never restarted. The item is keyed in
   the lazy list so it survives the solo↔grouped crossing, and a lambda captured on the plain home
   went on writing the *section's* repeat long after Rounds had become the group's. Verified from the
   disassembled `compose-ui` 1.7.0 `SuspendingPointerInputModifierNodeImpl.update` — the handler
   field is swapped, the running coroutine is not. Fix is `rememberUpdatedState`, the same guard
   `GlassCircle` already carries. iOS was never affected: SwiftUI rebuilds the closure every body
   pass. **Verified on the Flip:** grouped Rounds 11 → delete a section → double-tap → 8. Before the
   fix that double-tap moved nothing.
2. **(medium) The section time label ignored `repeatAll`.** Moving the rounds out of each section is
   exactly what broke it — the header is the only time reading on the home screen and it started
   advertising one pass of four. Two sections of work 5 / rest 15 at Rounds 4 read `20` and `5`
   against a workout of 2:25. Now `each × repeat × repeatAll`, closing rest subtracted **once**
   rather than per pass, reading **1:20 + 1:05 = 2:25** — exactly the 145s the timer runs.
3. **(medium) A stale doc comment** on iOS `repeatAll` still described the pre-change contract and
   would have talked a maintainer into deleting the fold in `remove()`.
4. **(low) A section whittled down to a lone Work stranded you.** Delete a Rest row inside a card,
   then delete the other section: `isBasic` is true for a one-item work block, so you fell back to
   the plain home — which has no `+ interval`, so nothing on screen could put the Rest back. `solo`
   now also requires `items.count == 2`.

**Confirmed but NOT mine and NOT fixed — worth a look later:**

- **iOS hold-to-repeat doesn't accumulate.** `GlassCircle`'s auto-repeat runs from a
  `Timer.scheduledTimer` closure that captures `self` **by value** at press time, so every repeat
  recomputes from the value the gesture started on. Hold − on a 30s Work row: Android walks
  30→25→20→15…, iOS pins at 25. Affects the editor as well as the home, and it means the two builds
  land on different durations from the same gesture. Pre-dates this session.

**Refuted, recorded so nobody re-raises them:** drag state leaking through the header's exit
animation (`clampToRegion` zeroes it next frame); the accessibility move actions being unguarded
(guards are in the quoted function); the 2→1 fold not being atomic (the claimed interleaving isn't
reachable); `effectiveRepeatAll == 1` being dead in the GO guard (it is read at click time from a
snapshot list, at a different instant); and three KDoc/import nits with no runtime surface.

### 28. The iOS drag glitch, actually fixed: the drop was four animation systems in one frame

> "if I drag in … it doesn't even matter where I let go. They just shoot to the top and bottom of
> the screen. It's fascinating."

Two wrong diagnoses preceded the right one, and the instrument that settled it was **logging, not
frames**. An os_log tap on the drag state machine (`category == "reorder"`, DEBUG only, still in the
code) showed that at the exact moment the cards flew, the machine was *right*: `heights=2 (221pt
each)`, `dragged=212`, `target=1`, `moved=true`, residual settle **−9pt**. The reorder always
committed. The violence was entirely in how the drop rendered.

The old drop deliberately did the array rewrite *unanimated* ("animating it too would play the same
move twice") and hand-stitched visual continuity with a computed `settleOffset`. That put four
animation systems in the same frame: the instant layout swap (±221pt), the raw offset swap, a
`withAnimation` spring on the settle, and the neighbours' `.animation(value: shift)` retargets — with
`@Published` coalescing deciding **arbitrarily** which transaction each delta rode, because an
ObservableObject's writes all collapse into one view update with no per-change transaction tracking.
The 221pt layout jumps kept landing inside an animated transaction they were never meant to be in,
so cards streaked to the screen edges and sprang back. End state correct every time — which is why
end-state checks kept passing while the user watched cards fly.

Fix: **the whole drop is one `withAnimation` transaction** — commit *and* state reset inside a single
block. Nothing left to stitch: the layout move and the offset release share a spring, so the dragged
card's net glide is `dragged − slotDelta` (those −9pt) *by construction*. `settleOffset` and
`slotDelta` are deleted outright — `settlingIndex` survives only to keep the landing card drawn on
top. Net −25 lines.

Verified on the simulator with recorded video either side: before, cards streak past the Rounds row
and return; after, a clean crossing where no card ever leaves the stack. Logged evidence for every
path: fast swap down (212 → moved), refused short drag (52 → no move, glides back), upward drag
clamped to exactly one slot (finger travelled 373pt, `dragged=-221`) → moved. Labels recompute
correctly after each swap (10:00/8:45 ↔ 9:00/9:45).

The earlier `minimumDistance: 0` change stays — it did fix the touch-claim race (the gesture engaged
in every test tonight, including 16ms-step drags); it just wasn't this bug.

### 29. …and one spring was still bouncy

> "If I let go towards the middle, they bounce towards each other."

Correct observation, real physics. The drop transaction ran on `dampingFraction: 0.8` (inherited from
the old settle, where travel was ~9pt and the bounce read as a soft landing) while the step-aside
spring is damping 1. On a mid-release each card has up to half a slot still to travel; 0.8 overshoots
~8% of that, and the overshoot-and-return of the two landing cards points them **at each other**.

Measured frame-by-frame (green-row tracking, ~1.7pt resolution): before, the neighbour touched its
slot and dipped ~5pt past before returning; after `.spring(response: 0.31, dampingFraction: 1)` —
now byte-identical to the reorderOffset spring — both trajectories are strictly monotonic into their
slots. The "magnetic" feel is the damping: cards decelerate into the slot and stop dead.

### 30. The drop, final architecture: glide first, commit at rest, land with animations off

> "Drag the bottom card up … past the top, then release. This is where it fails: it tries to bounce
> back down and then go into place. It's trying to really relive the animation."

§28's one-transaction drop fixed the mid-release but still failed the clamp and the flick — the
user's tests 3 and 4, both releases with a lot of travel left. Tracked frame-by-frame: the neighbour
reached 10pt from home, then **both cards snapped back most of a slot and replayed the move** —
"relive the animation", verbatim what it was.

The structural lesson, learned three times tonight: any scheme where visual continuity at the array
rewrite depends on two large opposing deltas (a card's layout jump vs its offset/shift release)
cancelling **choreographically** loses, because SwiftUI gives no simultaneity guarantee between
@State-driven layout and ObservableObject-driven modifier state — and the neighbours'
`.animation(value: shift)` modifiers catch their shift unwinding *whatever* transaction the commit
runs in.

Final shape, in `Reorder.swift`:

1. **Release animates exactly one thing**: `dragged` glides to `slotDelta(from, to)`, carrying the
   card pixel-exact onto its destination slot. `draggingIndex` stays set through the glide, so the
   neighbours hold their step-aside, the card keeps its lift and z-order, and the scroll stays off.
2. **Commit only at rest**, in the animation's completion — guarded by a drop generation so a
   re-grab can never have a stale commit fire underneath it.
3. **Land inside `Transaction(disablesAnimations: true)`**: the swap and the state-clear apply in
   the same update with implicit animations silenced, so every delta cancels by arithmetic. A
   clamped release, where the glide would be zero-length, lands immediately rather than trusting a
   zero animation's completion timing.

Measured on the previously-failing gestures: the clamp release walks 1273 → 1464 monotonically with
the commit invisible in the trajectory; the flick glides 1496 → 1464 while the neighbour rises
1185 → 801, no reversal anywhere. Refused moves glide home on the same spring and touch no layout.

### 31. Night-cap pair: equidistant home rows (iOS) and editable built-ins (both)

- **iOS plain home spacing**: Work↔Rest was 16pt but Rest↔Rounds was 48 (the section's leftover
  32pt solo bottom-padding plus the Rounds row's 16). Section bottom is now 0 when solo, so all
  three rows sit 16pt apart. Measured equidistant. Android was already even.
- **Built-ins are editable**: Edit on a pre-made preset opens the same editor, prefilled. Saving
  adds the copy to saved presets and hides the original by name — the exact mechanism deleting a
  built-in already used — so the list ends up holding the edited version once. Wired through both
  navigation layers (`editBuiltin` state beside `editIndex`). Verified end-to-end on iOS (Tabata →
  editor shows Work 20/Rest 10 ×8, 3:50); Android compiled + tests green but the phone was folded —
  eyeball it in the morning smoke pass (LAUNCH.md step 2).

Deferred on the user's call, noted in LAUNCH.md: removing the settings copy that explains the
work/rest colour correlation.

---

## Not built — design question

### 10. Grouping the pips into repeat "layers"

> front splits left leg · right leg · Kazakhs · middle splits — **four things, all of it three times
> over**

Twelve squares should read as 4 × 3. Worth knowing before deciding: the structure already exists in
the editor (`Block(items, repeatCount)` and the outer `repeatAll`), but `toWorkout` flattens it into
a numbered list before the timer ever sees it. Showing it means carrying group information through
`Workout` — a data change, not a restyle. It also interacts with item 1: a group size of 4 and an
even split into rows of ≤8 want different things from the same row.

Four ways to draw it, cheapest first:

1. **A gap between groups.** One row per group where they fit; a wider gutter where they don't.
   Nothing new drawn, and the rhythm does the work. Falls apart past ~4 groups of 4.
2. **A brighter square at each boundary.** One row as now, first pip of each group at full opacity
   even when unfilled. Very cheap, survives any count, but reads as a tick rather than a grouping.
3. **One row per group.** Literal and unmistakable — 4 × 3 is three rows of four. Breaks the even
   split from item 1, and a 3 × 8 routine gives three long rows.
4. **A second, thinner row of group-level pips** under the round pips: twelve small squares, three
   large ones. Says both things at once; two rows of dots is the least minimal option here.

My recommendation is **1**, falling back to the current flat rows when the groups don't fit — it adds
no new marks to a screen whose whole argument is that it has almost nothing on it.

---

## Standing context

- Nothing is committed. Do not commit or push without asking.
- The phone never buzzes; the watch does. Drag-gesture haptics in the editor are approved and stay.
- The debug build is installed on the Flip 7 (it replaced whatever was there).
- iOS verification gaps unchanged (see `IOS_PORT.md`): screen-locked residency, audio over music and
  ducking still need a real device.
