# Launch day — the exact steps, in order

Written the night before (2026-07-30). Everything below assumes the tree as it stands tonight.
"You" = things only you can do (passwords, accounts, taste). "Claude" = I run it, you watch.

---

## Phase 0 — before anything else (5 minutes)

1. **Commit.** Six sessions of work, ~32 changed files, zero restore points. Nothing else on this
   list is safe to attempt against an uncommitted tree.

   ```bash
   git checkout -b release-prep && git add -A && git commit -m "Home groups, rounds model, reorder rewrite, chrome, store prep"
   ```

2. ~~**One morning smoke pass, both apps.**~~ **Done** — 2026-07-31, PUNCHLIST §35. The Flip 7 was
   attached and everything that had shipped unseen (§31–33) was verified on it: built-in preset Edit
   opens prefilled, rounds count work sets (the counter read 二 / 八 in Chinese word-mode), rows are
   passes (four rows of two), and the current pip breathes at a measured 1.6s and freezes on pause.
   Nothing outstanding on either platform.

---

## Phase A — Google Play (shippable tomorrow)

3. **You: create the upload keystore** (~5 min, terminal). I never touch the passwords.

   ```bash
   keytool -genkeypair -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```

   Then copy `keystore.properties.template` → `keystore.properties` at the repo root and fill in
   the four fields. The file is gitignored; **back the .jks up somewhere that isn't this laptop**
   — losing it forfeits the app's identity on Play.

4. **Claude: build + verify the signed bundles.** `./gradlew :app:bundleRelease :wear:bundleRelease`,
   then verify both AABs are signed with the upload key, sizes sane (~3.2MB / ~2.4MB).

5. **Listing assets** (Claude drafts, you approve):
   - 512×512 icon PNG (export from the adaptive icon source)
   - 1024×500 feature graphic
   - 4–6 phone screenshots — captured off the Flip over adb: home, a running timer mid-Work,
     the theme grid, presets, the sequence editor
   - Short description (≤80 chars) and full description (≤4000) — drafts ready for your edit
   - Wear screenshots (round, from the watch over adb) for the Wear OS listing track

6. **You: put `PRIVACY.md` at a public URL** (~5 min). Easiest: make the GitHub repo public — or a
   gist — and use the raw link. Play rejects listings without a reachable privacy URL.

7. **You: Play Console** (~45 min, `docs/PLAY_SUBMISSION.md` open beside it — it has copy-paste
   answers for every form):
   1. Create app (org account → production available directly, no closed-testing gate).
   2. Upload `app-release.aab`; add the Wear track with `wear-release.aab`.
   3. Store listing: paste the assets and text from step 5.
   4. Data safety: **"No" to collection** — the whole form, §1 of PLAY_SUBMISSION.
   5. Content rating (IARC): timer, no ads, no UGC → Everyone.
   6. Foreground service declaration: subtype `interval_timer`, paste §2 **verbatim**.
   7. "Contains ads": No. Submit.
   - Expect days-to-two-weeks review — `specialUse` FGS gets a human. Nothing to do but wait.

---

### Status, 2026-08-02 afternoon — resume here

**The AABs were rebuilt on 2026-08-02 at 16:09** and supersede the 08-01 pair. The earlier bundles
predate the overscroll fix (`ba12464`, PUNCHLIST §52) — do not upload them. Re-verified after the
rebuild: both signed `CN=Christopher Poole, OU=MidaMultiMedia`, SHA-256
`3B:25:08:5F:0F:CD:27:73:02:BD:C3:9D:74:38:5B:E0:1C:8C:41:7D:16:4C:3D:4F:7E:4D:B1:0E:36:09:5A:D6`,
identical on both modules, `jarsigner -verify` clean. Phone 3.28MB / wear 2.42MB. versionCode 1 and
1000 — deliberately separate ranges so they can't collide under one listing; versionName 1.0.0 on
both. Nothing has been submitted, so versionCode 1 is still correct.

Done, needs nothing from you:

- Signed AABs built and verified against the upload key (phone 3.1MB, wear 2.3MB).
- Privacy policy live: **https://midamm-legal.netlify.app/interval-timer/privacy-policy/**
  (Netlify project `midamm-legal`, source in `docs/site/`, redeploy with
  `npx netlify-cli deploy --dir docs/site --prod`). Contact address is
  privacy@midamultimedia.com — **make sure that mailbox exists**, Play publishes it.
- Listing assets in `docs/play-assets/`: icon-512, feature-1024x500, 6 phone
  screenshots, 4 watch screenshots, and `listing.md` (short + full description).
- Play Console: app record **created** — Interval Timer, com.chrispoole.intervaltimer,
  App, Free, both declarations accepted. Account confirmed to be an *organization*
  account, so §Play-submission's "production directly, no closed-testing gate" holds.
- Verified against the merged release manifests: no `INTERNET` permission on either
  module (the wearable dependency injects none), FGS type `specialUse` with subtype
  `interval_timer`. Data Safety "No to collection" and the privacy page are both
  accurate for the artifacts that will actually be uploaded.

Left to do in the Console — all of it needs you, because the Play Console needs a
visible display and file uploads:

1. Store listing: paste short + full description from `docs/play-assets/listing.md`,
   upload icon / feature graphic / phone screenshots.
2. Production release: upload `app/build/outputs/bundle/release/app-release.aab` and
   `wear/build/outputs/bundle/release/wear-release.aab` (same release — one listing).
   The Wear screenshots slot only appears once the wear bundle is in.
3. App content: privacy policy URL (above), data safety, content rating (IARC),
   target audience, ads = No, foreground service declaration — §1 and §2 of
   `PLAY_SUBMISSION.md` are copy-paste for these.
4. Review and submit.

Nothing was submitted. The listing copy has still not been read by you — do that
before it goes public; one claim in it was wrong until it was checked against the
source (word mode is 12 languages, not 3).

---

## Phase B — App Store (start tomorrow; ships when device testing passes)

8. **You: plug the iPhone in once.** The sim cannot answer the three things that gate submission:
   - background residency with the screen locked (does the timer keep cueing?)
   - cues audible over Music with ducking
   - real-finger touch: drag-reorder, hold-to-pause, two-tap delete
   Claude drives the build to the device; you hold the phone and judge.

9. **Signing + first archive** (together): set your team on the target, first
   `xcodebuild archive`. The `.xcodeproj` is hand-written and has only ever built for the
   simulator with signing off — budget friction here.

10. ~~**Known iOS bug to fix before submission**: hold-to-repeat on − / + doesn't accumulate.~~
    **Not a bug — it was fixed in `f533a26` and the punchlist entry was stale for four commits.**
    Verified on an iPhone 17 simulator: one hold on Work's − walks 30s down to the 5s floor. See
    PUNCHLIST §27. Nothing to do here.

11. **You: App Store Connect**: bundle ID + app record, 1024 icon, per-size screenshots (Claude
    captures from the sim at required sizes), privacy nutrition labels (same honest "collects
    nothing"), age rating 4+. TestFlight to your phone first; submit after a day of real use.

---

## Noted for after launch (user's words, 2026-07-30 night)

- **Settings**: drop the copy explaining that colours correlate to work/rest — "goes against our
  ethos of simplicity. Intuition is king." (Confirm exactly which text is meant before deleting.)
- Grouped progress pips (PUNCHLIST §10) — open design question, unbuilt by choice.
