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
- **The round counter** reads "3 / 8" for the classic shape: `Block.isBasic` is what decides whether
  GO builds a `baseWorkout` or a sequence. Two sections, or one holding work/work/rest, fall to the
  sequence path — which counted *interval positions* until §32 taught it to count work sets too.

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

**Confirmed but NOT mine — since fixed:**

- **iOS hold-to-repeat doesn't accumulate.** `GlassCircle`'s auto-repeat ran from a
  `Timer.scheduledTimer` closure that captured `self` **by value** at press time, so every repeat
  recomputed from the value the gesture started on. Hold − on a 30s Work row: Android walked
  30→25→20→15…, iOS pinned at 25. Affected the editor as well as the home, and it meant the two
  builds landed on different durations from the same gesture. Pre-dated this session.
  **Fixed in `f533a26`** — every step now routes through a `StepSink` reference box that `body`
  re-points at the freshest `onStep` on each render, so the frozen struct copy in the timer closure
  still reaches the current closure. This entry sat marked "not fixed" for four commits after the
  fix landed; it was flagged again during launch prep on that stale reading.

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

## Fifth pass — 2026-07-31, what the pips are counting

### 32. A pip is a work set, and a row is a pass — all three targets

> three blocks, the first repeated twice, all of it twice · "you just show up 15. You should only
> show eight… it should just be two rows of four" · "I think it's counting rest blocks as a block"

The diagnosis was right, and it was one line. `Preset.toWorkout` numbered `Interval.round` by
**position** in the played sequence, so every rest was a round of its own: four work sets a pass ×
two passes = 8 work + 8 rest = 16, less the closing rest the timer drops = **15**. The counter said
"1 / 15" and the grid wrapped fifteen squares into rows of 6/5/4 — a shape that answers no question
anyone was asking.

Rounds now count work. A rest carries the round of the work it follows; anything before the first
work is 0. That is exactly how `baseWorkout` has numbered the plain home since the beginning, so the
two paths finally count the same thing and "3 / 8" means the same wherever you see it. Mirrored into
Wear's own `Timer.kt` — presets sync, and a watch counting to 15 beside a phone counting to 8 is the
worst possible version of this bug.

**This is item 10 below, settled by the user picking option 3.** Rows are the workout's own shape:
`Workout.roundsPerPass` carries the work-sets-in-one-pass through `TimerUiState` to the grid, and
`Pips.rows(total, perRow)` draws one row per pass instead of wrapping by count. The worry recorded
in item 10 — that per-group rows fight the even split from item 1 — is handled by falling back to
the wrap whenever the shape wouldn't draw: one pass (no shape to show), one pip per row (a column,
not a shape), a row past `SINGLE_ROW_MAX` (a wall), or more rows than `MAX_ROWS` (taller than the
timer screen reserves). So the grid is never taller than it was, and `OverallProgress`'s existing
two-term cell arithmetic already sized rows wider than eight — the single-row case needed it.

Third piece, asked for in the same breath: **the home now shows the whole workout's time above GO**.
The section headers each show their own share and none of them adds up to the total. It is measured
off `homePreset(blocks, repeatAll).playbackIntervals()` — the exact sequence GO builds — rather than
re-derived from the blocks, so it cannot drift from what runs: same zero-length drop, same trailing
rest, same outer ×N. The prepare lead-in is deliberately excluded, or the total would disagree with
the section times stacked directly above it. Always M:SS, because a bare "45" reads as a count.

**What the adversarial review of §32 caught.** Twenty-four agents over the diff; every finding they
raised was refuted on inspection except one, which a probe found by sweeping the whole state space
instead of guessing cases. The total was measured off `homePreset`, but GO branches: a single basic
section takes `baseWorkout` instead. The two disagree exactly once — a single basic section of
**Work 0 / Rest 15 at one round**, where `baseWorkout` plays a lone zero-length work and nothing
else, while the sequence builder drops the empty work, is left holding one rest, and keeps it
because a lone interval is the whole sequence. Label said 15s over a button that ran 0s.

Reachable, which is what makes it real: Work's floor is 5s so you cannot dial it to zero, but you
*can* dial a Rest to 0 and tap its label to make it a Work — the flip doesn't re-apply the floor.

Fixed by deleting the second builder rather than patching the arithmetic. `homeWorkout(blocks,
repeatAll, prepareMs)` now lives in the model on both platforms, GO calls it, and `homeSeconds` is
its `totalMs` — so the number and the button are the same object and cannot describe different
workouts. `theTotalOverGoIsTheWorkoutGoRuns` sweeps every one- and two-section home built from
{work, rest} × {0s, 5s, 30s} × {×1, ×2} at every repeatAll 1–3, mirrored in both suites. Confirmed
it bites: restoring the old formula reddens it on `[Work 0, Rest 5] ×1`, expected 0 but was 5000.

Verified on the iOS simulator, the user's exact case: three sections (first ×2), Rounds 2 →
home total **6:25** = 3:20 + 1:40 + 1:25, and GO gives **"1 / 8"** over **two rows of four**.
Both platforms' unit tests pin the case, including the 15-interval playback list unchanged and the
`[1,1,2,2,3,3,4,4,5,5,6,6,7,7,8]` numbering. Android builds green with identical model code but the
phone was not attached — **eyeball it in the morning smoke pass** (LAUNCH.md step 2), same as §31.

### 33. The set you're on breathes — both platforms

> "similar to how agents are displayed inside Claude… the squares that are active are kind of like
> flashing, glowing in and out… subtle, just enough to know, 'Oh, that's actively being done'"

The grid said how far; it didn't say *where*. The current pip now pulses between 0.45 and 1.0 on a
1.6s cycle — the trough deliberately sits above the unlit 0.22, so a live pip never reads as one you
haven't reached, and the peak goes brighter than the flat 0.85 of the ones behind it. Only the
current pip moves. It stops on pause and on finish: a pip still pulsing on a paused timer is the
screen claiming something is running when nothing is.

The two platforms need genuinely different mechanisms, and the iOS one is the interesting half.

- **Android** — `rememberInfiniteTransition` with `RepeatMode.Reverse`, created only when the
  workout is live, the same bargain the `Stepper` glow makes. The alpha is *computed* from a
  continuously-animating float, so whichever pip is current reads whatever the wave is doing at that
  moment. Read inside `drawBehind`, not passed to `background()`: a state read at draw time
  invalidates the drawing only, where reading it in composition would rebuild all thirty-two boxes
  sixty times a second in order to change one of them.
- **iOS** — `TimelineView(.animation(paused:))` sampling a cosine off the clock. The obvious SwiftUI
  spelling — flip a `@State` Bool inside `withAnimation(.repeatForever)` — is wrong here in a way
  that only shows up on round 2: the animation attaches at the instant the flag changes, so exactly
  one pip ever pulses, the one that happened to be current when the view appeared, and every later
  round inherits a settled value and sits still. Sampling time has no edge to miss. (Same family as
  the §28–30 drop bug: do not build visual continuity out of something that only happens at a
  transaction boundary.)

Measured from a 6s screen recording at 10fps, mid-workout: the current pip's grey walks
189 → 252 → 189 with troughs 16 frames apart — **1.6s**, as specified — while the completed pips
hold flat at 232 and the unlit ones at 162. At the round boundary the old pip locks to 232 in one
frame and the next pip picks the wave up **mid-cycle** (enters at 253, descends to 173, climbs
again), which is the exact behaviour the Bool-flag version would have failed.

Not pulsed: the >32-round bar fallback. It is already the degraded case and has no "current" square
to point at.

### 34. What each number on the home is *about* — both platforms

> "I want just the total, not the total rounds. I want that time to be the total of the block." ·
> "for the total time of all blocks combined, including how many rounds total, that should not be at
> the bottom. That should be at the top, just below where it says 'Save as preset'."

Three corrections to §32, all of them the same correction: a number has to describe the thing it
sits next to.

- **The block header time is now the block, full stop** — its intervals, run its own × N. It used to
  be the block's *share of the workout*: multiplied by the home's Rounds as well, and with the last
  block docking the closing rest the timer won't play. Both were defensible and both were wrong in
  that row, because the number sits beside the block's own "× 2" and has to mean what that means.
  Multiplying by the outer rounds made a block's length change when you touched a control somewhere
  else entirely; docking the rest made two identical blocks read as different lengths depending on
  which one happened to be last.
- **The summary moved to the top**, under "Save as preset", and carries both halves: `8 sets · 6:05`.
  It was a bare time sitting on the GO button, which is where you look last.
- **M:SS everywhere on this screen.** Block times are block-sized now rather than workout-sized, so
  most are under a minute, and `formatMs` renders those bare — a lone "50" in a column under "1:30"
  reads as anything but fifty seconds.

**Sets, not rounds**, deliberately, though the ask said rounds. "Rounds" is already the label on the
control directly below, where it means the outer ×N — 2, in the example. This is the other number, 8,
the one the timer counts you through and the pips draw. Two meanings for one word on one screen is
worse than borrowing the word the user themselves used first ("there are four total sets and the
rounds go over twice"). `homeSets` reads it off the built workout, the same `maxOf { round }` the
service computes, so the home cannot advertise a count the timer won't show.

Verified on both: iPhone `[35,10]×2 → 1:30`, two `[35,15]×1 → 0:50`, Rounds 2, top line
**8 sets · 6:05** (190 × 2 − 15). Flip 7 `[20,45]×1 → 1:05`, `[30,45]×1 → 1:15`, Rounds 4, top line
**8 sets · 8:35** (140 × 4 − 45).

### 35. §32–33 on real Android hardware

The Flip 7 was folded for §31–33, so all of it shipped compiled-and-tested but unseen. Now seen, on
a phone that happens to be set to Chinese word-mode, which exercised the localisation path for free:

- **Rounds count work sets**: the counter reads **二 / 八** — 2 of 8 — where it would have said 15.
- **Rows are passes**: two sets a round over four rounds draws **four rows of two**, not a wrapped
  line of eight.
- **The pulse**, measured off `screenrecord` at 10fps: the current pip walks 178 → 253 → 178 with
  troughs 16 frames apart — **1.6s**, the same period measured on the simulator, so the two platforms'
  entirely different mechanisms (`rememberInfiniteTransition` vs `TimelineView`) agree to the frame.
  The other seven pips are flat; their ±9 drift over six seconds is the background aura rotating.
- **Pause freezes it**: both lit pips flat at 236, swing ≤ 0.4.
- **Built-in preset Edit** (the §31 leftover): Tabata opens the editor prefilled — Work 20s, Rest 10s,
  Repeat this group × 8, "1 group · 15 intervals · 3:50". Cancelled without saving; presets untouched.

Nothing on the Android smoke-pass list is outstanding.

### 36. The home was the last screen walling off the camera cutout

Reported on the Flip: scroll the home up and cards were cut off short of the top. `SetupScreen`'s
container carried `safeDrawingPadding()`, which shrinks the `LazyColumn`'s **viewport**, not just its
content — so the list's own clipping edge sat below the punch-hole and a card sliding up vanished at
a line the screen gives no reason for. Presets and Settings were moved off that pattern earlier (§11);
the home never was.

Fixed the same way they were: container full-bleed, the top and bottom insets moved into the list's
`contentPadding` (safe inset + 56.dp, so at rest nothing moved a pixel), and the three things floating
over the scroll — Presets, Settings, Δτ — and the saved-notice pill now carry their own
`safeDrawingPadding()`. Verified on the Flip: at full scroll "Save as preset" reaches the top edge of
the display intact, passing behind the chrome instead of being cut against it.

### 37. The top chrome leaves with the page

Presets, Settings and Δτ sat over the scroll, so a card sliding past them overlapped three bits of
text that were pinned in place. Both platforms now: **Δτ is the list's own first item** — it is a
mark, not a control, so it belongs to the page and leaves by scrolling like everything else — and
**Presets and Settings slide out the side each one already sits on** and fade. The height the chrome
row used to hold open moved into the mark, so nothing below it shifted by a pixel.

**Presets and Settings are list items too** — the header is one row at the top of the content, and
it scrolls away because it is *in* the thing that scrolls. That is the whole mechanism.

Getting there took three wrong turns, all of them variations on keeping the buttons pinned over the
scroll and animating them out to fake what a list item does for free:

1. **A threshold and a 220ms tween.** A fixed duration can only ever run at one speed, so a quick
   flick left both of them still crossing the page long after the content had gone past them.
2. **A 0 → 1 fraction of the absolute scroll offset**, plus a commit to 0 or 1 when the scroll stops.
   Speed solved, but mapping off the *absolute* offset made every committed state a lie: settled back
   IN with the page still 45 down, the next touch snapped them to where the offset said they should
   be — a jump to the sides for a one-pixel drag.
3. **Accumulated scroll deltas**, with an in-bias near the top and both iOS rubber-band ends clamped.
   It worked. It was also a `NestedScrollConnection`, an `Animatable`, a settle watcher, two geometry
   probes and a custom `PreferenceKey` — to make two buttons do what the Δτ beside them already did
   by being an item in the list.

The user called it: *"I think it should have just been attached the same way the delta tau is
attached to the rest of the scroll."* Deleting all three took ~90 lines and seven imports off Android
and a whole preference key off iOS, and every bug in the list above stopped being expressible. There
is no fade, no slide, no commit, no threshold, and nothing to tune. Worth remembering the shape of
the mistake: **the fix for "this pinned thing should behave like it scrolls" is to stop pinning it.**

The only fiddle left is cosmetic — the row hangs 16 (Android) / 20 (iOS) outside the content gutter
so the labels still land 18 from the screen edge, where they were as an overlay, instead of 34–38 in.

Two things cost time on the iOS side, both worth remembering:

- **`.coordinateSpace(name:)` on a `ScrollView` names the scrolling content**, not the container, so
  content measured against it never moves and the probe read a flat 0. It has to go on an ancestor.
- **A `PreferenceKey` whose `reduce` is `value = nextValue()` will be overwritten by its siblings'
  defaults.** `reduce` runs across every sibling subtree, and the ones with no probe hand up
  `defaultValue` — so the one real measurement was clobbered and the value read 0 whatever the page
  did. The key is optional now and the first value wins. (Android needs none of this: `LazyListState`
  already publishes `firstVisibleItemIndex`/`ScrollOffset`.)

Verified on the simulator and on the Flip: at rest all three sit exactly where they always did;
scrolled, the chrome is gone and the list runs clean past the camera; scrolled back, it returns, and
Presets still opens, on both. The case that killed every animated version — creep to within a few
points of where the old barrier sat, lift, then touch again — now does nothing at all, because
there is no state to disagree with the scroll position.

### 38. The header floated down the page on a home that doesn't scroll

Fallout from §37, caught on the Flip. The home centres its content vertically, and once the header
became part of that content it centred along with it — so a plain one-section home, short enough not
to scroll, left Presets / Δτ / Settings hovering a third of the way down an empty screen instead of
sitting at the top of it.

The header needs a floor: it can travel **up** with the page and never down below its resting place.
The content should still centre. Both platforms now do exactly that, by different routes because the
layout systems differ:

- **Android** offsets the row up by the leading space `Arrangement.Center` inserted, clamped with
  `coerceAtLeast(0)`. That clamp is the whole rule — the lead is only ever positive, so this can
  cancel a downward push and can never become one. Scrolled, the lead is 0 and nothing happens.
  `Modifier.offset { }` (the lambda form) so it re-places without recomposing, and it reads item 0's
  own slot position, which the lazy list reports pre-offset, so there is no feedback loop.
- **iOS** moves the `minHeight` off the whole stack and onto everything *below* the header, at
  `viewport − 112` (the header's 56 plus the bottom padding). The header keeps its 56 at the top; the
  page centres in what is left; long content grows past it and scrolls as before.

Verified both cases on both: solo home — header at the top, content centred; multi-section — header
scrolls up and off exactly as it did.

### 39. Add/remove interval: three rough edges in one transition (Android)

All reported together off the device, all in the solo↔grouped crossing:

1. **The new card arrived overlapped** with the rows still animating out of its slot, then they
   spread apart. A new lazy item takes its full space instantly, and everything around it spends the
   next quarter second moving through that space. Fix: the new item's fade-in now waits out the
   shuffle — `animateItem(fadeInSpec = tween(260, delayMillis = 240))`. Everything gets out of the
   way first, then the card fades into the gap. (An earlier, shorter delayed fade read as a pop and
   was documented as a dead end; the delay wasn't the mistake, the pop-length fade was.)
2. **The header wobbled during add/remove** even though it shouldn't react to anything but scroll.
   §38's Android route was the culprit: `Modifier.offset { }` reading `listState.layoutInfo`, which
   is published *after* measure — so while the transition re-centred the page every frame, the
   header was placed from the previous frame's layout, one frame behind, and jittered. Replaced with
   `CenterUnderHeader`, a custom `Arrangement.Vertical`: centre everything, then pin item 0 to the
   top, all in the same layout pass. Lazy lists only consult the arrangement when content fits the
   viewport, so a scrollable home stacks normally and the header scrolls away as before. The offset
   hack is deleted.
3. **The − / + circles and digits snapped** at the end of a remove while the card melted around
   them. `Stepper`'s geometry was discrete `if (compact)` sizes, and `compact` flips at the *start*
   of the transition. The sizes now ride the same `box` fraction the card's own chrome does —
   `lerp(54.dp, 40.dp, t)` and friends, threaded `HomeSection → IntervalStack → Stepper`. `compact`
   stays boolean for behaviour (tap-to-flip, the ✕); only geometry interpolates.

**None of that fixed it** — the user reported all three still wrong. See §40.

### 40. The transition was dropping two vsyncs out of three (Android)

§39 was three guesses at a symptom whose cause is one thing, found by measuring the device instead
of reading the code. Everything below is `dumpsys gfxinfo` on the Flip 7, reset immediately before
the gesture and dumped one second later.

**Before:** 90th percentile frame **32ms**, 95th **34ms**, **13 missed vsyncs**, 22% janky. The
panel runs at **120Hz**, so the budget is **8.33ms**, not 16.7 — the app was rendering at 20–35fps
through the crossing and the IntendedVsync trace shows it plainly:

    8 8 8 8 8 8 8 8 8 8 | 42 17 17 17 25 17 25 17 17 17 17 17 17 25 17 | 8 8 8 …
    <-- idle, 120Hz ---> <---------------- the transition ------------> <- recovered ->

Irregular cadence like that reads as stepping, which is what "about 5 frames per second" and "two
discrete steps" were describing. It was never a *motion design* problem.

**It is recomposition, not drawing.** `atrace view gfx` put the Choreographer's `animation` phase —
where Compose ticks animations and then recomposes — at 25ms on its worst frame, while measure,
layout and draw together never passed 16ms and the GPU sat flat at 3ms. Making `box` and `g`
instant dropped the 90th percentile to 8ms and missed vsyncs to 2, which is what proved it.

The cause: `Modifier.padding(lerp(0.dp, 8.dp, box))` and friends read the animated float **during
composition**, so every frame of the 260ms crossing invalidated the whole section — its header, both
stepper rows, every glass circle — and rebuilt it. Same for `HomeRounds`, whose animated Row
*weights* are composition-time arguments by construction.

**The fix is to keep every animation and change only what it invalidates.** `box` and `g` stay
`animateFloatAsState` but are held as `State` and never unwrapped with `by`; every read moved inside
a layout or draw lambda:

- `paddingBy(h, v, bottom) { box.value }` — a `Modifier.layout` that reads the fraction at layout
  time. Replaces four `padding(lerp(...))` calls.
- `.drawBehind { }` for the card's fill and edge. `background()` and `border()` take a colour as an
  argument, so an alpha riding `box` had to be computed in composition; drawn, the same alpha is a
  per-frame read that rebuilds nothing.
- `scaledBy { }` — `graphicsLayer` scale plus a `layout` reporting the scaled size, so a stepper row
  is composed once at its resting size and the crossing is a GPU transform. The five ratios are all
  within 3% of one number (15/20, 17/24, 40/54, 66/90, 68/96 → **0.728**), which is what makes one
  scale able to stand in for all of them.
- `HomeRounds` became a hand-written `Layout` that places the label and the −/N/+ cluster from `g`
  read in the layout pass. The weights are gone; the sizes snap on `grouped` (a 2sp label and a 4dp
  circle, changing while the whole control is in motion).
- The pill's gradient stops carry full alpha and the fade is applied at `drawRect(alpha = …)`. The
  stops used to carry the animating alpha, so `drawWithCache` — whose entire job is to build the
  native shader once — was invalidated every frame of the fade.

**After:** 90th percentile **13ms**, **1 missed vsync**, 9.9% janky. Remove is clean; add still
misses a handful (a lazy insert, the save pill arriving and `animateContentSize` all land together).

**Two wrong turns worth keeping.** I first read gfxinfo over a 2-second window and got "1.16% janky,
7ms median — not a performance problem", because ~180 idle frames diluted the 18 bad ones and
Android scores "janky" against a ~16.7ms heuristic while this panel wants 8.33ms. Then I rewrote the
stepper geometry as a GPU scale on the theory that animating `fontSize` was re-measuring text — it
changed the numbers not at all, because recomposition dominated. **Measure the window that contains
the gesture, and confirm the cause by removing it, before writing the fix.**

Also caught here: a custom `Layout` must measure children with `constraints.copy(minWidth = 0)`.
`fillMaxWidth()` makes the width *tight*, and passing that straight down forced both children to
fill the row, drove the centred origin negative and put "Rounds" off the left edge with the + hanging
off the right. Caught on a screenshot, not by the compiler.

### 41. Size snapped while position animated (Android)

What was left once the judder was gone, and both halves of it are the same mistake: something
changes size in one frame and then spends 260ms travelling, when it should do both at once.

1. **The stepper rows "teleported to the left, then shrank."** The ✕ that removes an interval was
   gated on `compact`, which flips the instant the crossing starts — so it appeared at full width
   immediately. The cluster is placed flush right, so its width decides where the − begins, and a
   ✕ arriving out of nowhere made it ~30dp wider in a single frame and shoved the whole row left
   before any of it had begun to move. It is now composed whenever the section has more than one
   interval and scaled by the crossing fraction, so it grows in and shrinks away on the card's own
   clock. The arithmetic then comes out exact: (40+68+40+2) × 1/0.728 = 206dp against the plain
   row's 54+96+54+2 = 206dp — the cluster starts precisely where it already was.
2. **Rounds got big, then walked to the centre.** Its sizes were snapped on `grouped` while its
   position rode `g`. Every size now rides `g` too, through a scale rather than a font size, so the
   number grows *on the way* rather than before setting off. The number is the one place a single
   scale can't say it: the digits grow 24→30 while the box around them shrinks 96→78, so the box
   gets an interpolated width (`widthBy`) and the text scales inside it.

**`scaledBy` must anchor at the top-left, and that cost a round trip.** A `graphicsLayer` pivot is a
fraction of the *node's* size, and this node reports `s ×` its content — so a centred pivot sits at
`s·W/2` while the content's own centre is at `W/2`, and the drawing lands `(s-1)·W/2` off. The
Rounds + circle came out 7px short of where it had always been. At (0,0) the content spans 0..W,
scales to 0..sW, and matches the reported size exactly; centring is the parent's job, done with the
size this reports.

Verified by masking the screenshots to their bright pixels — the aurora background drifts over tens
of minutes, so a raw image diff is useless — and comparing the x-extent of each row against a
screenshot taken before any of this work: Work, Rest and Rounds all match to the pixel on the plain
home, and the grouped Rounds row matches at [158..926].

### 42. Home drag-reorder was pointing at the wrong two items (Android)

> "I can't slide them around on Android. If I tap it, it instantly floats to the very, very top."

An off-by-two, and both symptoms fall out of it. `DragDropState` works in **list** indices, so the
home tells it which ones may be reordered: `firstCard until firstCard + rows.size`. `firstCard` was
written as "0 solo, 1 grouped" when the save pill was the only thing above the cards. Two more
items have gone in above them since — the header row and the summary line, both in `54b0514`, the
commit that moved the header into the list — and the count was never updated. The range therefore
named the save pill and the summary rather than any card at all.

- **The jump to the top.** `clampToRegion` reads the top of the region's first item and the bottom
  of its last. Pointed at two items that sit *above* every card, `hi` came out far less than `lo`,
  and `hi.coerceAtLeast(lo)` collapsed the whole allowed span to a single line — the top of the save
  pill. The card was clamped there on the first frame of the gesture, which is the "instantly floats
  to the very top".
- **Nothing ever swapped.** `neighbourToSwapWith` only considers a neighbour whose index is `in
  range`. The real cards were at 3 and 4 against a range of 1..2, so no candidate ever qualified.

Fixed by counting what is actually above the cards: header + summary always, plus the save pill when
the cards are showing → `if (solo) 2 else 3`. The sequence editor's own `FIRST_CARD` was right all
along; it has exactly the one header item it counts.

Verified on the phone with `input motionevent`, holding mid-gesture: the lifted card sits under the
finger 205px down rather than at the top, the swap commits while still held, and it survives the
release. The screen was left exactly as it was found (two ×1 sections, Work 20s / Rest 15s,
Rounds 6).

**Not the review's doing** — the audit commit only touched the settle carry-over and the 120Hz
autoscroll. It landed on top of a range that was already stale.

### 43. Deleting mid-drag: the case that isn't worth carrying (Android)

> "if I wanted to press X while something was being moved, it would still work. That's overkill."

Agreed, and it was paying for itself twice: `detectDragGestures` fires neither `onDragEnd` nor
`onDragCancel` when its node is torn down mid-gesture, so both drag systems carried a
`DisposableEffect` to notice the handle disappearing and clean up after it.

The scenario is now closed instead of handled: while a drag is live, the deletes that could pull a
handle out from under it do nothing — the section ✕ on the home, the group ✕ and the row ✕ in the
sequence editor. Both `DisposableEffect`s are gone. The index bounds checks stay: they are three
tokens each, they also cover the accessibility move actions, and one of them is what keeps a
`coerceIn` honest on an empty list.

### 44. A hard-edged rectangle inside a lifted card (Android)

> "a new rectangle that spans the width of the rest to the ✕, and is tall too. Only when it's
> currently selected. When I let go, it goes away."

**A platform elevation shadow, seen through the glass it was supposed to be hidden behind.** Nothing
in the app draws it, which is what made it hard to find — and pre-existing, not new: it has been
there as long as the lift has, and the home drag has been broken for two commits (§42), so this was
the first chance to see it.

Measured off the device, because guessing at it went nowhere. The rectangle sits inset 85px left,
86px right, 128px top and 46px bottom from the card's outline — horizontally symmetric, vertically
not. That asymmetry is the tell: a uniform ~29dp inset plus a 41px shove *downward*, which is
exactly what a shadow cast by a light above the screen's centre looks like. HWUI tessellates a
shadow as an umbra with a penumbra fading outward, and it skips filling the umbra when it decides
the caster is opaque and would hide it. This caster is glass at α 0.16, so the ring it did draw
shows straight through the card, and the umbra boundary — a 28dp rounded rect inset by 29dp, which
eats the corners entirely — is the hard-edged rectangle. Its top lands below the header because the
shadow is shoved down; its width is the pill content's because that is where the inset happens to
land.

Confirmed by building with `elevation` forced to 0: the hard edges at x=155 and x=922 vanish
completely (they show up in 300–460 of the card's 640 rows with the shadow on, and in none of them
with it off), and the brightness step across them drops from +5.8 levels to +0.6, which is noise.

Fixed by dropping the shadow from both lifts — the home section and the editor's group card. The
comment beside each already said a drop shadow "all but disappears" on this near-black background:
it was contributing nothing but this artifact, and the lift still reads clearly off the brightened
glass, the lit edge and the 1.03 scale. If a shadow is ever wanted back here it has to be drawn by
hand, outside the card's own outline — `Modifier.shadow` cannot do it behind anything translucent.

### 45. Ladder opened as nine groups, and the editor didn't look like the home

> "Group 1 is just work. Group 2 is just rest. They're both 20 seconds. That obviously should be its
> own group." · "it's more square. There's an extra bubble around work and rest, and there's an
> extra line on the far left side."

**The grouping.** `groupIntervals` recovers ×N blocks from a flat list by looking for a repeated
pattern at each position. Ladder climbs 20/30/40/50/60 and repeats nothing, so every position fell
through to the fallback — a block of one interval — and a nine-interval preset reopened as nine
groups with "work 20" and "rest 20" sitting in separate boxes.

The fallback now keeps the rest that follows a work: same block. A work and its recovery are one
thing you do, which is exactly the shape the home builds its sections from, so the two screens now
agree about what a group is. Ladder comes back as five: (20 + 20), (30 + 20), (40 + 20), (50 + 20),
and the closing 60.

It absorbs **only rests**, which is what keeps it from eating the start of a pattern — a repeat
always begins at the interval after the last rest, so the scan at the next position still sees it
whole. There's a test for exactly that (`pairingLeavesALaterRepeatIntact`), because it is the one
way this change could quietly cost a ×N. Tabata still groups to one ×8 pair and EMOM to one ×10.

**The look.** Three differences from a home section, all of them deliberate once and none of them
worth keeping now that the same object appears on both screens:

1. **Corner radius 16 → 28.** The home's number. 16 read as a different kind of surface.
2. **The chip inside the pill.** Each row is already a coloured pill that means work or rest, and it
   held a second coloured pill saying the same word. The word now sits directly on the tint, exactly
   as a home row says it, and is still the only part of the row that flips the phase — the row used
   to swap on any tap, and a near-miss on a stepper flipped work to rest instead of nudging the
   clock. It still greys when the rule won't allow a rest there, and still answers a tap so the tap
   can surface the reason. `PhaseChip` is deleted on both platforms.
3. **The bracket rail.** A 3dp line down the left of the rows, holding a group together visually.
   The card already does that, and the home draws no such line around the same intervals. Gone, and
   the card's lopsided 4/14 padding — which existed to make room for it — is even 12dp now.
4. **"Repeat everything / Plays through once", in a glass card at the bottom.** The same number the
   home calls Rounds, explained in a sentence because it had nothing else to say it with. The home
   says it by *position*: the word, a big count between two circles, sitting directly under the
   stack of cards it governs. So that is what this is now, and the sentence and the card are gone.
   The add button moved below it to keep the home's order — it is being under the whole stack that
   makes it read as governing the whole stack, so nothing may come between them.
5. **"Repeat this group", and the header words with it.** The same argument one level down. The card
   header is now the home section's header control for control — grip, ×N, how long the group runs,
   ✕ — and the row at the bottom is gone. "Group 3" went too: the grip beside it already says
   "Reorder group 3" to a screen reader, and the number was only there to be counted off against a
   heading nobody needed. "Delete" became the ✕, the same button in one glyph. What's left in a card
   is Work, Rest and "+ interval" — the same words the home uses.

   The ×N was originally kept away from the grip in case a reorder control beside a counter read as
   driving it. The home has put those two side by side since it grew sections and nobody has read it
   that way, so that caution is retired rather than carried. The sentences survive where they are
   still needed: `stepperSemantics` still speaks "Repeat group 3", "3 times".

Row corners went to a full pill with the same change, since 12dp corners were the other half of
"more square".

The Rounds row copies the home's resting numbers (18sp label, 12dp gap, 50dp circles, a 30sp count
in a 78dp box) rather than sharing the composable: the home's is a hand-written `Layout` that walks
the row from left-aligned to centred as the group box forms, and none of that clock exists here.
Screenshotted side by side at 1:1 — label, circles and number land on the same pixels.

Verified on the phone: Ladder now opens as five groups, tapping Work/Rest flips the phase both ways,
the greying still appears on the row above a rest, the Rounds stepper counts up and back down, and
in the card header the ×N steps (the duration readout follows it, 0:40 → 1:20) and the ✕ deletes the
group. Cancelled out each time, so the built-in is untouched at 9 intervals · 4:40. iOS carries the
same model change (its core tests pass) and the same five visual edits; it compiles, but has not
been looked at on a simulator.

---

### 46. Theme swatches too small to show a theme (both platforms)

> "they don't very well represent what they're going to be. I think we need to make them just a
> little bit bigger."

Three cells across a 280dp card leave each stripe 23dp wide, and at a 44dp row height that made them
23×36 — squat chips that showed a colour but not the aura it belongs to. The swatch is the real
shader, so there was nothing wrong with it except that at that size there was no room for the bloom
to fall off; every theme reduced to a flat rectangle, and a theme has to be picked on what the
screen is going to look like.

**First attempt: taller.** 62dp, on the reasoning that 23×54 is 0.43 — the proportions of the phone
itself, the same argument the language tile's `aspectRatio(0.82f)` makes. Wrong:

> "that much verticality just looks weird. I think increasing the width was really what we needed."

Right, and the phone-proportions argument was the trap. The stripe is not a picture of the screen,
it is the aura seen through a **slot**, and a tall thin slot shows less of a bloom than a wide one,
not more — while stretching a swatch the long way reads as distortion rather than as more of
anything. Height was also the only dimension available without touching the grid, which is what made
it look like the answer.

**Two across instead of three**, height left at 44. Each stripe goes 23dp → 39dp wide, and 39×36 is
close to square. Areas across all three versions: 828dp² before, 1242 taller, **1404 now** — the
widened one is the biggest as well as the only one that doesn't look stretched. Costs five rows
instead of three on a page that already scrolls.

Mirrored to iOS. Its `rowsOfThree` is shared with the language grid, which stays three across, so it
grew an `across:` parameter rather than a second copy.

**Then 10% back off the width**, 6dp of padding each side:

> "you see too much of the black vignetting on certain corners"

Mechanical, not taste. The shader corrects for aspect — `p.x *= iResolution.x / iResolution.y` — so a
box wider than it is tall reaches further out into the falloff horizontally, and the corners go
black. At 39×36 (aspect 1.09) the blooms sat inside a visible vignette; at ~35×36 (0.98) the stripe
is square enough to sit inside the bloom instead. Which is also why the original 23dp stripes never
showed it: they were narrow enough to be cropped to the middle of the frame.

**Then 10% back onto the height**, 44dp → 48. Final stripe: 35×40, from 23×36. Height was the wrong
lever to reach for *first* — alone it just makes slivers — but once the width is settled it costs
nothing, and by the same aspect maths it can only help: a taller box reaches *less* far into the
falloff, so the corners keep what the narrowing won back.

### 47. The swatch was still mostly showing black (both platforms)

> "On the actual screen it looks cool because it's the animation that's live, but for these I want to
> get rid of that, because it's really just trying to show off the colours."

Sizing had run out of road: the vignette is the shader's own falloff, and a 35×40 box puts its
corners where the blooms have gone. Modelled over all 27 stripes actually drawn (9 palettes × 3
phases, at their real seeds), the darkest pixel in a stripe was **2–15% of the brightest, mean 9%** —
a colour sample that is mostly a picture of the dark.

So the shader takes an `iZoom`: how much of the frame the box shows. 1 is the whole composition,
blooms and the black between them, which is what the timer wants and what it passes. The swatch
passes **0.35**, cropping to the lit middle, which brings the same measure to **47–77%, mean 66%** —
a gradient with grain over it rather than a vignette. The alternative, a second hand-tuned gradient
for previews, is exactly what the original comment on `AuraSwatch` was written to prevent: it would
drift the first time the real aura was touched. One uniform keeps it the same shader.

Both call sites set it explicitly on both platforms — an unset AGSL uniform is 0, which would
collapse `p` to a point and paint one flat colour. The Metal twin takes the same parameter in the
same position, so the documented cross-platform pixel check (same seed, same input, same output)
still holds.

**The language tiles keep the full frame.** They share `AuraSwatch`, so the first build flattened
them too — they are five times the area of a theme stripe and hold a numeral the gradient sits
behind, where the falloff reads as depth rather than as darkness. `zoom` is a parameter with the
swatch value as its default and `1f` passed at that one call site; the tiles are pixel-identical to
before the change (0 differing pixels of 307,100).

### 48. Language tiles rounded to bubbles (both platforms)

> "for the Word Mode with the languages, can you reduce the rounding of the corners a little bit?
> Reduce them by about half?"

`RoundedCornerShape(percent = 38)` → 19, and `min(w, h) * 0.38` → `* 0.19` on iOS. At 38% an 85×104dp
tile has a 32dp radius, which rounds the corners nearly to a stadium and eats the space a numeral
needs — "三" and "구" were sitting in an oval. The comment defending it (organic, matching the
gradients inside) still holds at 19%: it is a percentage radius, so it still scales with the tile.

### 49. The aurora cut to the new theme in one frame (both platforms)

> "could you make it so there's just a subtle fade, super fast, maybe five frames"

The three colour uniforms cross-fade, and that is the whole transition — the shader is a sum of three
coloured curtains, so interpolating what it is handed interpolates what it draws. No second layer, no
blend pass, nothing to keep in sync.

**80ms, linear.** Five frames at 60Hz and ten at 120: a duration in ms is the only frame-rate
independent way to say "five frames", and on a 120Hz panel a literal five would be 42ms, which is
under the point where a fade reads as a fade rather than a stutter if anything drops. Linear because
a colour ramp with an ease on it arrives late and draws attention to itself.

Android holds the three as `State` read inside the draw lambda, never unwrapped with `by` — same rule
as `HomeSection`'s `box` (§40). Read in composition the whole background would rebuild every frame of
the fade for three values only the shader ever sees.

iOS does it by hand: these are shader arguments, not view properties, so `.animation` has nothing to
interpolate. `ShaderCanvas` already re-evaluates its closure every frame under `TimelineView`, so the
mix needs only a start stamp and the clock it is handed anyway.

### 50. Theme card: eight themes, a chosen order, and Minimal on the title line

> "getting rid of the grape theme … reorder … make the minimal button just to the right of the theme
> … cut that second sentence"

- **Grape is gone**, leaving eight — which is exactly four rows of two in the new grid. Both
  platforms already stored the palette by name and degraded an unknown one to Default, so anyone
  holding Grape lands on Default rather than on a crash; that comment was written for this and is
  now doing its job. The doc comment above the enum claimed Joker had been cut for duplicating
  Grape's lime-and-purple, which is no longer a reason for anything — reworded, not deleted, because
  the pairing is free again and someone will wonder.
- **Order is the owner's**: Default, Mono, Spidey, Miami, Trance, Laser, Vesper, Tron. Declaration
  order *is* picker order on both platforms, so the enum is the single place it lives. It also moves
  every swatch's frozen frame, since the seed is `ordinal * 3 + i` — no matter, they only have to
  differ from each other.
- **Minimal moved onto the THEME title line.** It is orthogonal to the palette (Minimal + Vesper is a
  black timer with Vesper on the edge), so it belongs to the card rather than hanging under the row
  of swatches as if it were one more theme. `SettingsCard` grew a `trailing` slot; on iOS that needs
  a second `init` for the empty case, because a generic parameter can't be defaulted in place.
- **Word mode lost its second sentence.** "Languages with their own numerals keep them" explained a
  rule you can see the moment you flip it — the Chinese tile keeps 九 either way — and it was the
  longest string on the screen to say it.

**Not verified on device:** the phone disconnected between building and installing, so §50 and the
iOS half of §49 are compiled and tested but unseen. The Android crossfade in §49 was checked on the
phone before that.

### 51. The stepper glyphs were never centred, and the animation only made it visible

> "the minus button and the plus button do not stay perfectly centered in the circles as it moves."

**Two wrong theories died to measurement before the right one turned up**, and both were mine.

The first was that `.font(.system(size:))` isn't animatable while `.frame` is, so the glyph's size
snaps on frame 1 while the circle interpolates. Refuted: on iOS 26.5 SwiftUI re-resolves the font
every frame. The minus bar's ink width tracks the interpolated diameter continuously — 28.0px at
40pt, 30.0 at 44, 31.6 at 47, 33.0 at 50, 35.1 at 54 — where a frame-1 snap would pin it at 28 for
the whole shrink. Shrinking and growing agree to within 0.05px at every size. No pop, no lag.

The second was a missing `.geometryGroup()`, the standard remedy for children not moving in lockstep
with an animating parent. Refuted harder: a rig translating a constant-size circle 240pt using
HomeView's own mechanism (animated flexible spacers) drifted 0.016px vertically and 0.021px
horizontally. Noise. Pixel-grid snapping during motion died the same way.

**The actual cause is static and has shipped since the control was written.** `.frame(width:height:)`
centres the Text's *line box*; the ink of − and + sits below that box's centre, so the glyph has
always sat ~4.8px (1.6pt) low at size 54 — animating or not, in every circle in the app. What made
it *look* like a motion bug is that SF Pro's optical-size axis moves the ink by a different fraction
at each point size: 2.965% / 3.409% / 2.572% of the diameter at 54 / 50 / 40, vertically, which is
not even monotonic. Animating `size` therefore walks the glyph along that curve — a small arc, which
reads as wandering rather than resizing.

And in the real app it is far worse than the isolated rig showed. The rig (circles pinned on flat
black, only the diameter animating) put the peak relative motion at 1.80px. In the actual
home → grouped transition, where the row *travels* as well as resizes, the glyph lags the row and
falls **45.9px — 15.3pt — to the bottom rim of its own circle** at the midpoint. That is the thing
that was actually visible.

**The fix** (`Glass.swift`): pin the font at `BASE_GLYPH * 0.44` and carry the diameter on
`.scaleEffect` instead, with the ink correction as an `.offset` inside the scale. Pinning is the
load-bearing part — it makes the miscentring one fixed fraction of the box, so a single constant
cancels it everywhere, instead of a curve no constant can follow.

Result, measured at every reachable size (36, 40, 44, 50, 54): vertical offset from +3.06…+4.82px
down to −0.35…0.00px, which is the measurement's own noise floor. In flight, the 54↔40 interval rows
hold within a 0.45px span against 45.9px before.

**It costs no sharpness**, which was the obvious thing to fear. Only the *metrics* are pinned; the
outline is still resolved at the effective size. Proof: the 50%-ink width at 40 and 36 runs wider
than a uniform scale of the 54pt glyph predicts (27.37 vs 26.43, 24.64 vs 23.79), which a scaled
raster could not do.

**Two limits, both from the same mechanism — a Text's `.offset` is rounded to a whole device pixel.**
The −1.601pt nudge is delivered as −5px rather than −4.803px, leaving 0.2px of over-correction;
every value from about −1.500 to −1.833 delivers the same −5px, so re-tuning it does nothing. And
the horizontal term was **inert** — 0.473px rounds to zero, so a first version of this fix carried an
x constant that did literally nothing while a comment described it as measured and exact. It was
caught by an A/B against a rebuilt pre-fix control and removed. − and + want different horizontal
corrections anyway (+0.46 vs +0.23px), so no single constant nulls both. Going sub-pixel would mean
moving the correction off the Text onto the Circle, which isn't snapped. 0.2px is 1/15th of a point;
not worth it.

**Android has the same latent miscentring** — `Glass.kt:199-203` is the identical construction, a
Text sized proportionally and centred by its layout box. Not fixed, not measured, and the user has
not reported it. Worth a look if the Compose home ever animates a circle's diameter.

### 52. The group frame stood still while the cards bent around it

> "everything reacts to that except for the background layer behind the part that contains all of
> the blocks. That stays stationary and needs to stretch with the rest of everything."

Android 12's stretch overscroll is a `RenderEffect` applied by a draw node that `LazyColumn` keeps
**inside itself**: `LazyList` builds `modifier.then(…).scrollingContainer(…)`, and
`scrollingContainer` is `clipScrollableContainer → overscroll → scrollable`. The caller's modifier
is the outermost thing in that chain. The group frame was painted by a `drawBehind` on exactly that
caller modifier, so it sat outside the layer being stretched — the cards bent at the end of a scroll
and the frame behind them didn't move. Verified against the real 1.7.0 sources, not assumed.

**Three approaches were built and compiled before choosing**, because the obvious one is not the
best one:

1. *Hoist the effect.* Make an effect with `ScrollableDefaults.overscrollEffect()`, apply
   `Modifier.overscroll()` ahead of the `drawBehind` so both land in one stretched node, and feed it
   from a `nestedScroll` connection — starving the list's internal effect by claiming the leftover
   delta rather than nulling `LocalOverscrollConfiguration` (which would have forced a ~250-line
   re-indent for a two-line change). Correct, and it compiles. Rejected for two reasons: it depends
   on foundation internals staying put — `LocalOverscrollConfiguration` is already deprecated in 1.8
   for `LocalOverscrollFactory` — and it carries a permanent subtle regression, because
   `ScrollingLogic.shouldScrollImmediately()` reads the *internal* effect's `isInProgress`, which is
   now always false, so touching the screen during spring-back waits for touch slop instead of
   catching the list.
2. *Mirror the stretch.* A second `EdgeEffect` fed from reconstructed pointer deltas. Rejected: it
   under-stretches for any non-pointer scroll (wheel, rotary, keyboard), and a frame that stretches
   by a visibly different amount than the cards is a worse bug than one that doesn't stretch at all.
3. **Chosen: draw the frame from inside the list.** A `stickyHeader` spacer draws it. Sticky because
   a lazy item is only composed while its own slot is on screen, and this one has to keep drawing
   while the stack it frames scrolls past it. Being an item, it is inside the layer the stretch is
   already applied to, so frame and cards are recorded into the *same* render node and transformed
   by the *same* shader. Not "two effects kept in sync" — one effect, one image. Desync isn't
   unlikely, it's unavailable. And it touches no overscroll API at all, so the 1.8 churn can't reach
   it.

The spacer's 14.dp is the summary's old `padding(bottom = 14.dp)` — the same empty space with a job,
so nothing moved to make room for it. `zIndex(-1f)` is the one load-bearing line: sticky headers are
placed *last* on purpose, because a real one overlays the rows sliding under it, and this one is the
opposite — the surface everything sits on. Placed last, its fill tinted every card.

**`firstCard` moved 2/3 → 3/4.** That is the count §42 broke on, so it now has exactly one home and
the frame's own range derives from it rather than a second hard-coded 3.

**Not verified on a screen.** No Android device was connected and this machine has no emulator and
no system image, so this is compiled, unit-tested and reasoned from decompiled 1.7.0 bytecode —
nobody has watched it. Every way it can fail is loud and visible at rest, which is why this approach
was preferred over the subtler one: the frame tinting the cards (zIndex lost), the frame not drawing
at all (a 14.dp item paints hundreds of dp outside its own bounds — lazy items place with
`clip = false`, so it should, but that is the untested assumption), or drag-reorder mis-indexed.
Check in that order.

---

## Settled — was a design question

### 10. Grouping the pips into repeat "layers"

> front splits left leg · right leg · Kazakhs · middle splits — **four things, all of it three times
> over**

**Built as option 3 — see §32.** The user chose it in their own words: "it should just be two rows
of four." The original note is kept below because the reasoning about the trade-off is what the
fallback rules in `Pips.rows` are made of.

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
   split from item 1, and a 3 × 8 routine gives three long rows. ← **chosen**
4. **A second, thinner row of group-level pips** under the round pips: twelve small squares, three
   large ones. Says both things at once; two rows of dots is the least minimal option here.

My recommendation was **1**, falling back to the current flat rows when the groups don't fit — it
adds no new marks to a screen whose whole argument is that it has almost nothing on it. The user
went straight to 3, and it is the right call: 1 says "there is a boundary here", 3 says "you are
doing this whole thing again", and the second is the sentence the screen was missing.

---

## Standing context

- Nothing is committed. Do not commit or push without asking.
- The phone never buzzes; the watch does. Drag-gesture haptics in the editor are approved and stay.
- The debug build is installed on the Flip 7 (it replaced whatever was there).
- iOS verification gaps unchanged (see `IOS_PORT.md`): screen-locked residency, audio over music and
  ducking still need a real device.
