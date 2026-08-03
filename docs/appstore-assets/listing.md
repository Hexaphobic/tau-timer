# App Store listing copy

Paste-ready. Draft — nothing typed into App Store Connect yet.

Apple indexes **App Name + Subtitle + Keywords as one pool**. A word already in the name is
already indexed, so repeating it elsewhere buys no extra reach. This is the main way the listing
differs from Play, which has no keyword field but does index the descriptions.

## App name (max 30)

Interval Timer: Not Ugly

24 chars. Same as Play — the marketing plan is TikTok/Instagram links, and the recall search
("not ugly interval timer") has to hit on both stores.

## Subtitle (max 30)

Customizable Interval Timer

27 chars. Owner's call, made after seeing the ASO cost: "Interval Timer" is already in the app
name, so 15 of these 27 characters add no new search terms. Accepted deliberately — the subtitle
is the line a human reads under the name, and reading well matters more here than squeezing the
index. The terms it gives up (HIIT, Tabata, EMOM) are recovered in the keyword field below, which
is where they belong anyway since keywords are invisible to users.

## Keywords (max 100)

hiit,tabata,emom,workout,gym,crossfit,boxing,circuit,rounds,stopwatch,exercise,fitness,sets,reps,wod

Exactly 100/100. Comma-separated, NO spaces after commas — a space costs a character and buys
nothing.

HIIT, Tabata and EMOM lead the list because the subtitle no longer carries them, and HIIT is the
highest-volume term in the category. Excluded: "interval", "timer", "customizable" — all already
indexed via the name and subtitle.

Apple stems plurals inconsistently, so "rounds"/"sets"/"reps" are left plural as typed.
Do NOT add competitor app names — Apple rejects for it.

If a term ever needs to be swapped in, "wod" is the cheapest to drop (3 chars, narrowest audience).

## Promotional text (max 170, optional)

Editable any time WITHOUT submitting a new build. Sits above the description. Good place for
seasonal or campaign copy later; leave empty at launch.

## Description (max 4000)

Apple does NOT index the description for search — unlike Play, keywords in here do nothing for
ranking. So this version is written purely to convert a reader who is already looking at the page.

Most interval timers are ugly. This one isn't.

Set your work interval, your rest interval, how many rounds. Tap GO. That is the whole setup.

Then put the phone down. The screen becomes one colour and one number, big enough to read from across the room, and the colour flips at every work/rest boundary — so you always know which side you are on without walking over to look.

Build your own sequences interval by interval: ladders, pyramids, EMOMs, warm-ups. Drag to reorder, tap to edit, save them as presets. Ladder, Pyramid, Tabata and EMOM 10 come built in.

Make it yours with eight colour themes, a Minimal mode, and twelve languages for the count.

- Runs in the background with the screen off
- Audio cues at every interval
- Get-ready countdown
- Hold to pause, tap to resume
- Ducks under your music
- Works offline

## What changed in the rewrite, and why

Rewritten to convert rather than to carry keywords, per the owner's direction.

- **The hook now leads.** "Most interval timers are ugly. This one isn't." was buried third in the
  Play version. It is the strongest line in the listing and the entire brand in eight words.
- **Added the payoff sentence** ("Then put the phone down...") — the Play copy described the colour
  change as a fact; this says what it is FOR. Conversion copy sells the benefit, not the mechanism.
- **"twelve languages" is now stated as a number** rather than "language options". Concrete numbers
  convert better than vague ones. NOTE: the twelve-language *list* was cut from the Play
  description on the owner's direction as over the top — this is the count only, not the list.
  Revert to "language options for the count" if even the number is too much.
- **"Companion Wear OS app for your watch" is CUT.** There is no watchOS app. An Android-watch
  claim in an App Store listing is both false and a rejection risk.

All feature claims were verified against source on 2026-08-03 for the Play listing and still hold:
8 themes (Palette in ui/Glass.kt), 12 word-mode languages (Language in model/Language.kt), ducking
(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, Beeper.kt:42), four built-in presets. "Audio cues" is
deliberate wording — the app beeps via SoundPool and has no TextToSpeech.

## URLs — both live, verified HTTP 200 on 2026-08-03

Support:        https://legal.midamultimedia.com/interval-timer/support/
Privacy policy: https://legal.midamultimedia.com/interval-timer/privacy-policy/

Source in `docs/site/`. Redeploy both with:

    npx netlify-cli deploy --dir docs/site --prod

The support page carries the support@midamultimedia.com address and six FAQs covering the
questions most likely to arrive as one-star reviews (background running, cues over music, how to
pause, word mode, building sequences, data collection).

## Age rating

Expect 4+. Same answers as the Play/IARC questionnaire: no violence, no sexuality, no language,
no controlled substances, no gambling, no horror, no user-generated content, no user-to-user
interaction, no purchases. Preset names are typed by the user but never leave the device.

## App Privacy (nutrition labels)

"Data Not Collected" — the whole form. Same basis as Play Data Safety: the app holds no INTERNET
permission at all, so nothing can leave the device.
