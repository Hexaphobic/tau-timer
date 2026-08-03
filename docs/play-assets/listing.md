# Store listing copy

Paste-ready. Play does NOT render markdown in these fields — no asterisks, no `#`.

## App name (max 30)

Interval Timer: Not Ugly

Owner's call: category first, brand second (I had argued brand-first). The marketing plan is TikTok/Instagram links, not Play browse — a
new app with no reviews will not rank for "interval timer" against apps with millions of installs,
so ASO is not what the title is for. What it IS for is the recall search: someone sees the video
Tuesday and searches "not ugly interval timer" on Friday. Both halves of that query live here, and
it costs 24 of the 30 characters to own it outright.

Avoid "Best"/"Top"/"#1" here: Play's Store Listing and Promotion policy bars unattributed
performance and ranking claims in the title, and a metadata rejection stacks on top of the slow
specialUse FGS review.

Not "minimalist", considered and rejected: the app is opinionated AND customizable (8 themes,
12 languages, a sequence editor). The word would draw people wanting fewer knobs and disappoint
them, and it collides with Minimal mode, which is an actual feature.

## Short description (max 80)

55 chars.

The interval timer that isn't ugly. HIIT, Tabata, EMOM.

Play's Console checker rejected "The best-looking interval timer for all your workout needs." with
"Your app may not be promoted on Google Play because your short description ... Should not use
keywords that indicate store performance or ranking". Not a publish blocker — it only drops the app
from Play's promotional surfaces (editorial features, recommendations), which barely matters given
the TikTok/Instagram link strategy. Changed anyway: the superlative bought nothing Play would
honour, and the replacement puts HIIT back, which had vanished from the whole listing and is the
highest-volume search term in the category. The "best-looking" claim still lives in the full
description, which is not policed the same way.

## Full description (max 4000)

~740 chars.

Set your work interval, your rest interval, how many rounds. Tap GO.

Most interval timers are ugly. This one isn't.

The colour changes on every work/rest boundary, so you can read your state from across the room.

Build your own sequences interval by interval - ladders, pyramids, EMOMs, warm-ups. Drag to reorder, tap to edit, save them as presets. Ladder, Pyramid, Tabata and EMOM 10 come built in.

Make it yours: eight colour themes, a Minimal mode, and language options for the count.

Companion Wear OS app for your watch.

- Runs in the background with the screen off
- Audio cues at every interval
- Get-ready countdown
- Hold to pause, tap to resume
- Ducks under your music
- Works offline

## Notes

- Cut from the first draft on the owner's direction: it was over the top. Gone are the
  "built for the moment you're not holding your phone" framing, the background-running
  paragraph, the twelve-language word-mode list, no-ads, no-account, progress pips,
  back-to-back rests and the permissions line. Background running survives as one bullet.
- "Tabata" is a registered trademark in some jurisdictions, used generically by most timer
  apps on Play. Kept deliberately; "20/10 intervals" is the swap if the risk is ever unwanted.
- "best-looking" is a subjective aesthetic claim, not a ranking claim, and sits in the
  description rather than the title — the far lower-risk place for it. If Play ever objects,
  "Most interval timers are ugly. This one isn't." carries the same idea without the superlative.
- Verified against source: 8 themes (Palette in ui/Glass.kt), 12 word-mode languages
  (Language in model/Language.kt). The description no longer states either count except themes.

## Live state, 2026-08-02

Typed into the Play Console default store listing but NOT saved — the owner reviews and saves.
App name 24/30, short 59/80, full 702/4000, no validation errors on any field.

The em dash in the sequences line was typed as a plain hyphen: keyboard emulation does not
reliably produce an em dash and a mangled glyph in a public listing is worse than a hyphen.

KEYWORD NOTE: "HIIT" now appears nowhere in the listing. It lived only in the old short
description; the full description names Tabata and EMOM but not HIIT. Play indexes both fields
and HIIT is the highest-volume term in the category. Flagged to the owner — the fix is to fold
it into the full description's sequences line, which is the sheltered place for keywords anyway.

The earlier "red field at 58/80" was a stale form, not the copy: the identical string typed into
a fresh load of the same page validates clean. The listing had never been saved at that point.

## Correction, 2026-08-03

"Calls every interval out loud" was FALSE and is now "Audio cues at every interval", fixed in the
Console and here. There is no TextToSpeech anywhere in either module: `Beeper.kt` loads three WAVs
(go, tick, warn5) into a SoundPool. The app beeps, it does not speak. The line was inherited from
the first draft and survived the rewrite unchecked, and it was live in the saved listing for a
while. Verified by grepping the source and both shipped DEX files.

Other claims in the description were checked against source at the same time and hold: 8 themes
(Palette has exactly 8 entries), ducks under music (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
Beeper.kt:42), works offline, background with screen off, get-ready countdown, hold to pause, and
the four built-in presets.
