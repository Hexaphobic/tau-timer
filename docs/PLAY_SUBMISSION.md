# Play Console submission notes

Copy-paste answers for the Console forms, so you're not re-deriving them at 11pm.

Account is an **organisation** account, so the closed-testing gate (N testers for 14 continuous
days) that applies to personal accounts created after Nov 2023 does **not** apply. Production is
available directly.

---

## 1. Data safety form

The app has no `INTERNET` permission, no analytics SDK, no ads SDK and no crash reporting. Every
answer below is verifiable from the manifest and the dependency list.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A (no data collected) |
| Do you provide a way for users to request that their data is deleted? | N/A (no data collected) |

That is the entire form. Because you answer "No" to collection, Play skips every data-type section.

**Watch sync is not "collection" or "sharing."** Play defines both in terms of data leaving the
device *to you or a third party*. Preset data going to the user's own paired watch over the
Wearable Data Layer is device-to-device transfer for the user's own benefit, which Play's own
guidance excludes. Nothing is transmitted to the developer.

**This changes the moment you add AdMob.** The Google Mobile Ads SDK collects the Advertising ID
and device/diagnostic data. You would then answer "Yes" to collection, declare "Device or other
IDs" (purpose: Advertising or marketing), and update `PRIVACY.md` *before* shipping that build. A
Data Safety form that doesn't match the SDKs in the bundle is one of the most common causes of
enforcement action.

---

## 2. Foreground service justification

Your reasoning — a background timer needs to run in the background — is true but circular, and
that's the shape of justification reviewers reject. What they're actually asking is a narrower
question: *why does no standard `foregroundServiceType` fit?* Answer that specifically.

### Text to paste

> Interval Timer runs athletic interval workouts — for example 30 seconds work, 15 seconds rest,
> repeated 8 times. When the user starts a workout, a foreground service keeps the interval clock
> running and plays audio cues at each interval boundary while the screen is off or the user is in
> another app. The user is exercising and is not holding the phone, so an interval boundary missed
> or deferred by even a second is a failure of the app's core function.
>
> The service is always user-initiated: it starts only when the user taps GO or starts a saved
> preset, it posts an ongoing notification for its entire lifetime, and it stops when the workout
> ends or the user ends it. It performs no work at any other time and never starts on its own.
>
> No other foreground service type applies:
>
> - **shortService** — a workout routinely runs 10–45 minutes, far beyond this type's limit.
> - **dataSync** — the service transfers no data; it keeps time and plays cue tones.
> - **mediaPlayback** — the app is not a media player. It plays short generated cue tones, not
>   user-selected media, and exposes no MediaSession, playback queue or transport controls.
> - **health** — the app records no health or fitness data and reads no sensors.
> - **location, camera, microphone, phoneCall, connectedDevice, mediaProjection, remoteMessaging,
>   systemExempted** — none describe a countdown timer.
>
> Declared subtype: `interval_timer`.

### One option worth considering first

`specialUse` gets **manual review**; the standard types generally don't. `FOREGROUND_SERVICE_TYPE_HEALTH`
covers apps supporting "exercise" use cases, and a HIIT timer is arguably exactly that — switching
would be a two-line manifest change and would likely skip the manual-review step.

The risk cuts both ways: if a reviewer decides a timer that records nothing isn't a health app,
you get rejected on a type you had no need to claim. `specialUse` with the justification above is
the defensible, honest answer. Mentioning it because the review-time difference is real, not
because I'd change it — I wouldn't, on a first submission.

---

## 3. Listing assets still to make

- App icon, 512×512 PNG
- Feature graphic, 1024×500 (shown at the top of the listing — required)
- At least 2 phone screenshots; 4–6 is better. Good candidates: home screen, a running timer
  mid-interval, the theme grid, the preset list expanded
- Short description, max 80 characters
- Full description, max 4000 characters
- Privacy policy URL — see `PRIVACY.md`, needs hosting at a public URL
- Content rating: IARC questionnaire. A timer with no ads, no UGC and no data collection rates
  Everyone / PEGI 3 on every question
- "Contains ads" declaration: **No** for this version

A screen recording is not used by Play for the listing itself — the video slot takes a **YouTube
URL**, not an uploaded file. Worth knowing before you spend time editing one.

---

## 4. Version

Both modules are at `versionName "1.0.0"` — the phone on `versionCode 1`, the watch on `versionCode
1000` so the two never collide under one listing. `versionCode` must increase by 1 for every
subsequent upload; Play rejects a re-used code.

---

## 5. The unlock (v1.1.0, `versionCode 3`)

The code is in. What is left is Play Console work, and one ordering rule that will waste a day if
you get it wrong.

**Order matters: the in-app product cannot be created until a build containing the Billing library
has been uploaded to a track.** Play only offers the "In-app products" screen once it has seen
`com.android.billingclient` in an uploaded APK/AAB. So: upload to internal testing first, then
create the product, then test.

1. **Upload** the v1.1.0 bundle to **Internal testing**. Nothing is buyable yet; that is expected.
2. **Monetize → Products → In-app products → Create product.**
   - Product ID: **`unlock`** — must match `UNLOCK_SKU` in `Billing.kt` exactly, and it can never be
     changed or reused once created.
   - Name: *Unlock everything*. Description: the six themes and unlimited saved sequences.
   - Price: **$2.99** USD, let Play auto-convert the other currencies.
   - Set the product **Active**. An inactive product returns no `ProductDetails`, and the app then
     shows the button with no price and does nothing on tap.
3. **Setup → License testing** — add your own Google account. Licence testers buy with a test card,
   see the real Play sheet, and are never charged. Without this you will be spending $2.99 to test.
4. **Test on a device signed into that account:** locked theme → sheet → buy → all eight themes and
   the preset cap gone; then clear the app's data and confirm it comes back unlocked (that is
   `queryPurchasesAsync` on launch doing its job, not a cached receipt).
5. **Store listing:** the "Contains ads" declaration stays **No**. The listing gains "In-app
   purchases" automatically — you do not declare that yourself.
6. **Data safety: no change.** The app still collects nothing. The purchase is Google's transaction,
   not app data collection. `PRIVACY.md` has been updated for the network permission and the
   purchase itself, and needs re-uploading wherever it is hosted.

New permissions the Billing library merges in, none of them user-visible or prompted:
`INTERNET`, `ACCESS_NETWORK_STATE`, `com.android.vending.BILLING`.

**Grandfathering.** On the first launch of v1.1.0 the app checks `firstInstallTime <
lastUpdateTime` and, if this install predates the paywall, marks it permanently unlocked. Existing
1.0.1 users therefore keep all eight themes and every preset they have already saved. This is
decided once and written to prefs, so later updates do not hand the unlock to everyone who updates.
