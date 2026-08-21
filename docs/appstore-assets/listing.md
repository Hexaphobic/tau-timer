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

The support page carries the support@midamultimedia.com address and eight FAQs covering the
questions most likely to arrive as one-star reviews (background running, cues over music, how to
pause, word mode, building sequences, data collection, a lost unlock, and refunds). The last three
name both stores, because this same page is the App Store's support URL.

## Age rating

Expect 4+. Same answers as the Play/IARC questionnaire: no violence, no sexuality, no language,
no controlled substances, no gambling, no horror, no user-generated content, no user-to-user
interaction. Preset names are typed by the user but never leave the device.

One answer changed on 2026-08-21: the app now carries a single non-consumable in-app purchase, so
the purchases question is **yes**. It does not move the rating — a $2.99 one-time unlock for colour
themes is not gambling, loot boxes or a simulated-gambling mechanic, and there is nothing random
about what it gives you.

## App Privacy (nutrition labels)

"Data Not Collected" — the whole form. The basis is stronger than a policy promise: the binary
links no networking framework at all (verified with `otool -L` and `nm -u` on the built product),
so there is nothing in it that could send anything anywhere.

The in-app purchase does not change this answer. Apple runs the transaction; the app is told
whether this Apple Account owns `unlock` and nothing else — no name, no email, no payment details
ever reach it. Payment data you never see is not data you collect.

`PrivacyInfo.xcprivacy` ships in the bundle declaring `NSPrivacyTracking = false`, no collected
data types, and the one required-reason API the app touches (UserDefaults, reason CA92.1, for its
own settings). Without that file the upload fails with ITMS-91053 before review ever starts.

## In-app purchase

One non-consumable, created in App Store Connect before the build is submitted — a first
non-consumable has to ship attached to a version, and adding it afterwards costs a whole extra
review cycle.

- **Reference name:** Unlock everything
- **Product ID:** `unlock` — the same string as the Play product, and unchangeable once created.
  It must match `UNLOCK_ID` in `ios/IntervalTimer/Billing.swift` character for character.
- **Price:** Tier for $2.99 USD, Apple auto-converting the rest.
- **Display name:** Unlock everything
- **Description:** Six more colour themes and unlimited saved sequences. One payment, no
  subscription.
- **Review screenshot:** the paywall itself — Settings › tap any locked theme.
- **Review notes:** "Tap Settings, then any theme other than Default or Mono. The purchase sheet
  is the only thing behind the unlock; the timer, the audio cues, background running and all 12
  languages are free and need no purchase to review."

The unlock must be submitted **with** the 1.0.0 build (attach it in the version's In-App Purchases
section), not afterwards. `ios/IntervalTimer/Unlock.storekit` is the local StoreKit test
configuration — running the app from Xcode's IntervalTimer scheme exercises the whole purchase and
restore flow with no App Store Connect round trip and no money.

## Archive and upload

The code is ready — the app builds clean for a real device (`Release`, arm64) and the unlock works;
both gates were exercised in the simulator on 2026-08-21. What is not ready is signing, and it
needs you for about two minutes.

**The blocker, verified three ways.** `xcodebuild archive` fails with *"Your team has no devices
from which to generate a provisioning profile."* Automatic signing signs the archive for
development first and re-signs for distribution at export, so it wants an iOS App Development
profile — and the team (GTF5NXSC6V) has zero registered devices, so no such profile can be minted.
Forcing manual signing with the App Store profile already on this Mac fails too: that profile is
Xcode-managed, and manual signing refuses managed profiles. Setting `CODE_SIGN_IDENTITY` to
Apple Distribution under automatic signing fails as a conflicting-settings error. There is no
command-line way around it.

**Clearing it — either one works:**
- Plug an iPhone into this Mac and let Xcode register it (Window › Devices and Simulators). This is
  the one to prefer, because the device tests in `docs/IOS_PORT.md` have never been run either and
  the phone needs to be attached for those anyway.
- Or add any device ID at developer.apple.com › Certificates, Identifiers & Profiles › Devices.

**Then it is two commands:**

```
cd ios
xcodebuild -project IntervalTimer.xcodeproj -scheme IntervalTimer \
  -destination 'generic/platform=iOS' -configuration Release \
  -archivePath build/IntervalTimer.xcarchive archive -allowProvisioningUpdates

xcodebuild -exportArchive -archivePath build/IntervalTimer.xcarchive \
  -exportOptionsPlist ExportOptions.plist -exportPath build/export \
  -allowProvisioningUpdates
```

`ios/ExportOptions.plist` is already written for App Store Connect distribution with symbols.

**Build number is 2, not 1.** Build 1.0.0 (1) was uploaded to App Store Connect on 2026-08-03 and a
build number can never be reused. `ios/IntervalTimer/Info.plist` carries the literal — the
`CURRENT_PROJECT_VERSION` build setting never reaches a hand-written Info.plist, so that line is the
one that governs.

**Order at submission time:** create the `unlock` in-app purchase first, attach it to the 1.0.0
version, then submit. A first non-consumable must ship with a version; adding it afterwards costs a
full extra review cycle. And file the App Launch featuring nomination **before** pressing Submit —
it closes permanently at submission, one shot per app, ever.
