# Growth — first 1,000 downloads, first $1,000

Researched overnight 2026-08-07 by ten agents against live store data, ~2,900 competitor reviews,
the HN archive, and a real `assembleRelease` of this repo. Every number below has a source or is
marked as an estimate. Where the evidence is thin I say so rather than rounding it into confidence.

Read §0 and §1 first. Everything else is downstream of them.

---

## 0. Status, and the decision it already made for you

**Submitted.** Chris submitted the Play release on 2026-08-07. (The repo docs are stale on this —
`docs/LAUNCH.md` still reads "Nothing was submitted"; it was written 08-02 and the submit came
after the last commit. This file is the current record.) Play review on a newer account commonly
runs 7–14 days, and health-adjacent categories 14–21, so expect a wait.

**This forecloses paid-up-front on Android, permanently.** The Play app record was created as
**Free** (`LAUNCH.md:179`). Google's rule: *once your app has been offered for free, the app can't
be changed to paid* — the only route is a new app with a new package name, forfeiting this listing,
its reviews, and the `specialUse` FGS review currently in flight.

So §2's model question is answered by fact rather than preference: **free + a one-time unlock.**
§7's counterargument is now historical — read it to understand the trade you made, not as an open
option.

Nothing about this is bad. It was the recommended sequencing anyway: ship Android free with zero
monetization code, land the unlock in v1.1. You are exactly on plan.

**iOS is NOT submitted** — confirmed 2026-08-08, App Store Connect reads *Prepare for Submission*.
Any earlier build activity was TestFlight; Beta App Review is not App Review. Everything
time-critical is still available, and the order matters:

1. **Attach the $4.99 unlock to the 1.0 build before submitting.** Apple requires the first
   non-consumable to ship *with a new app version*. Free now; a permanent extra review cycle later.
2. **Enrol in the Small Business Program now** — the 15% rate starts 15 days after the fiscal month
   of approval, so enrolling after launch bills the best sales week at 30%.
3. **Submit with "Manually release this version."** This decouples review from launch: the app parks
   at *Pending Developer Release* while you file the App Launch nomination against a release date
   3+ weeks out, bank the video content, and then release deliberately — rather than having the app
   appear unannounced mid-shoot. It also lets the iOS launch line up with Android's v1.1 unlock.

---

## 1. Your premise is half right, and the half that's wrong matters

You said: *"this is the interval timer that's not ugly. All the other ones are ugly."*

I went looking for evidence and found the opposite.

- Across **~2,900 Google Play reviews** pulled from the ten largest interval timers, **zero** call
  any of those apps ugly. The only "ugly" verdict in the whole corpus was one Timer Plus user
  insulting a *different* app.
- Design praise is common and unprompted: SmartWOD gets "looks slick with a clean layout",
  "sleek and simple"; machy1979 gets "very beautiful interface"; mdev gets "Minimalist, flawless".
- The ratings back it: **SmartWOD Timer holds 4.94 across 54,035 iOS ratings** — the highest-rated
  app in the category. Float Tech's Interval Timer holds 4.84 across 85,723. dreamspark and
  Tabata Timer both sit at 4.9 on Play with 269K and 227K reviews.
- **Dark mode / AMOLED is a 0.07% ask** — 2 mentions across ~2,900 reviews.
- Six minimal-dark-timer apps launched in the last 12 months. They sit at 0–1 ratings.

So: the category is not ugly, it is *merely competent*. Nobody ships a genuinely art-directed
aesthetic, which is a real gap — but almost nobody is asking for one, which means **beauty alone
will not move installs.** A design-led claim of "they're all ugly" gets contradicted by the first
competitor screenshot a prospect sees, and by 54,000 people who already rated one 4.94.

### What is actually uncopyable

Every competitor can hire a designer this quarter. Not one of them can ship this:

```
FOREGROUND_SERVICE · FOREGROUND_SERVICE_SPECIAL_USE · WAKE_LOCK · POST_NOTIFICATIONS
```

That is the complete permission list of the release build. **No INTERNET.** The app is physically
incapable of phoning home, and that is checkable on camera by anyone in ten seconds.

The closest competitor to your positioning — `com.mdev.intervaltimer`, free, ad-free, 4.9 stars,
363,381 installs — already has a review saying it is *"the only one (to my knowledge) with no data
collected and no ads."* Someone is already being rewarded for this ground. It is winnable and it is
worth more than the aesthetic claim.

**Keep "Not Ugly" as the name.** It is a recall handle, not a competitive claim, and it works:
`(Not Boring) Timer` runs the identical joke, has 2× Apple Design Awards and 10× App of the Day.
Just don't build the *argument* on it. Lead with what's provable.

---

## 2. Monetization: the answer

### Ads — no. Permanently. Here is the arithmetic that closes it

You asked whether it should be an ad a week or an ad every five days. **Those are the two
worst-performing options of the sixteen modelled.** Installs required to net $1,000:

| Model | Installs needed for $1,000 |
|---|---|
| Paid up front $4.99 | **236** |
| Free + $4.99 unlock | 24,817 |
| Banner ads (always on) | 25,218 |
| Interstitial once per session | 44,131 |
| **Interstitial every 5 days** | **110,329** |
| **Interstitial once a week** | **154,460** |
| Tip jar | 168,067 |

At a realistic 1,000–3,000 installs, lifetime ad revenue is **$22–66** — arriving at $12–30/month
against AdMob's **$100 payout threshold**, so your first cheque is 3–8 months out.

What that $22 costs, measured on a real build of this repo:

- **+2,024,089 bytes — a 135% APK increase** (1.50 MB → 3.5 MB) before one line of ad code.
- **Six new permissions**, including `AD_ID`, which cannot be stripped — it *is* the monetization.
- Play Data Safety rewritten off "no data collected". App Store label flipped from "Data Not
  Collected" to **"Data Used to Track You"**. An ATT prompt (~70% decline). A mandatory UMP consent
  implementation for EU/UK you cannot test from Colorado. IARC refiled. `PRIVACY.md` line 11
  becomes false.
- **A real bug injected into working code**: `Beeper.kt` builds its `AudioFocusRequest` with no
  `OnAudioFocusChangeListener`. A video ad taking `AUDIOFOCUS_GAIN` evicts the request silently —
  cue tones then fire at full volume over the ad and the user's music never comes back.
- Ad creatives render in a WebView composited over a 60fps AGSL shader, and there is no dark-mode
  ad inventory. Every creative arrives on white, on a screen whose identity is `#000000`.

And it is a one-way door in reputation terms: posting a privacy-framed launch and then adding an ad
SDK is the exact sequence that produces an HN pile-on which outranks your store listing for years.

**"Remove ads for $1" is worse than either pure option** — it pays the entire brand and compliance
cost of ads to collect a fraction of the unlock revenue.

### Price: $4.99. Not $1, not $2

Your instinct was $1–2. That is wrong by about a factor of four.

Revenue per install, indexed to $0.99: **$1.99 → 2.01× · $2.99 → 3.02× · $4.99 → 3.99× ·
$6.99 → 4.41×.** The curve has not peaked at $6.99. RevenueCat's 2026 data separately shows
high-priced apps converting at **2.8% D35 vs 1.4% for low-priced** — cheap pricing loses on price
*and* on conversion simultaneously. $0.99 needs ~99,000 installs to clear $1,000; $4.99 needs
~25,000.

$4.99 also sits correctly against the market. The modal **$2.99** in this category is the price of
*remove-ads* (dreamspark, 0FF, Next Up) — the price of making something stop. You're not selling
relief from a nuisance you inflicted. The comparable rung is SmartWOD's standalone "Ad-Free $4.99",
and you'd still be 4× under mdev's ~€20 lifetime — the competitor whose own reviews say
*"20€ is too much... below 10€ I would have bought it."*

Also: **$0.99 is a price that describes a disposable thing.** An app pitched as "you'd think Apple
made it" cannot be priced like a ringtone.

Price is the cheapest thing to change later — no code, no review, either store. The *model* is
expensive to change. Pick $4.99 and stop thinking about it.

### Model: free + one non-consumable unlock

**Decided.** Two of three independent judges landed here on the merits; the Play submission then
made it the only option on Android (§0). §7 keeps the paid-up-front case because it was genuinely
strong and you should know what you traded away — but it is not a live choice.

**Free forever:** the entire timer, background running, audio cues, all four built-in presets, all
12 languages including Cistercian, Minimal mode, the Wear OS app, the aurora shader, and 3 saved
custom sequences.

**Unlock — $4.99, one payment:** the six non-default themes (Default + Mono stay free), and
unlimited saved sequences.

Gate cosmetics and capacity. **Never function, never the timer, never a language.** The two worst
review clusters in the entire category both come from gating function — Float Tech's use-count wall
(*"It let me do one workout and then set up a paywall"*) and mdev's save-time wall (*"after 10–15
minutes inputting a series of sets... you go save and THEN it says hey, you have to pay"*). An app
that stops timing until you pay is not an app anyone thinks Apple made.

**The aurora shader stays free deliberately.** It's the screenshot and the TikTok hook. You do not
paywall your own demo.

No subscription, ever. It is the loudest hatred in the corpus — *"Imagine paying a monthly
subscription to a stopwatch"*, and *"Subscriptions are the devil"* appears in a **five-star** review
praising SmartWOD for offering a one-time SKU.

### Why free rather than paid, given paid needs only 236 sales

1. **Your goal order.** You said downloads first. Only 2 of 36 iOS "interval timer" results carry a
   price. Seconds prices the experiment inside one developer's own portfolio: **Seconds Pro (paid)
   59,239 Play installs vs Seconds (free) 1,104,425** — a 20:1 penalty for charging at the door.
2. **Your traffic is impulse traffic.** "Download free" is what makes a social CTA convert. A price
   wall is maximally lethal on cold TikTok viewers.
3. **The doors lock behind you.** On Play, free→paid is *impossible* (new package name, forfeit
   listing and reviews) and paid→free is *permanent*. Free-with-IAP is the only posture you can
   change your mind about — IAP prices and SKUs edit freely, no review, either store. You have
   zero real install data. Buy the optionality.

---

## 3. The number you need written down before launch

**1,000 downloads and $1,000 are not the same milestone. They are 8–25× apart.**

At $4.99 net $4.24, $1,000 = **236 unlock sales**. Downloads required:

| Attach rate | Downloads for $1,000 |
|---|---|
| 0.5% (25th pct) | ~47,000 |
| 1.2% (median) | ~19,700 |
| 2.0% | ~11,800 |
| 2.9% (RevenueCat H&F D35) | ~8,100 |

**Plan on 12,000–20,000 downloads for $1,000.** Your first 1,000 downloads will produce roughly
**$40–60.**

Write that down now. It is what stops month three reading as failure when it is actually on track,
and it is what makes the case for a creator budget before you burn a year on organic.

---

## 4. First 1,000 downloads

### The honest funnel

Short-form converts at roughly **0.03% of views to installs** (band 0.01–0.05%). So 1,000 installs
≈ **3.3M views**, planning band 2–5M.

A sub-10k-follower TikTok account gets a median **300–1,000 views per post**. Sixty TikToks in 30
days ≈ 18,000–60,000 views ≈ **5–18 installs**.

**Founder-posted organic alone is a 12–18 month path to 1,000, not a 30-day one.** "Just push it on
TikTok and IG" is a necessary component with a two-order-of-magnitude gap to the goal.

Every organic breakout in the case studies had one of exactly three things, and you have none:

- **A platform moment** — Widgetsmith's breakout was a *stranger's* 24M-view video during iOS 14
  widget week. Smith had no TikTok strategy and found out via Twitter.
- **A viral loop in the product** — Locket does nothing until you add up to five friends. Every
  install manufactured five more. A timer has zero loop.
- **Paid creators at scale** — Cal AI (closest analogue: Health & Fitness, TikTok/IG, young solo-ish
  founders) grew on **250+ micro-creators on retainer**, not founder posting.

### Free multipliers — do these before posting anything

1. **Custom store pages.** Apple Custom Product Pages (70 allowed) and Play Custom Store Listings
   (50 allowed). Apple's own data across 1M+ ad groups: tap-to-install **63.91% → 70.05%, +22.9%
   more installs from identical traffic.** Make the first screenshot a still frame of the
   green→blue flip — the exact thing the viewer just watched. Put *that* URL in your bios.
2. **Say the app name out loud and on screen, 2+ seconds, every video.** TikTok barely weights
   bio-link taps and every platform is burying external links. **Most installs will arrive by store
   name-search days later.** "Interval Timer: Not Ugly" is already engineered as a recall query —
   the videos have to plant it. The link is a leak, not a pipe.
3. **TikTok as a Business account** — bio link with zero followers. Personal/Creator needs 1,000.
4. **Apple's featuring nomination — file it BEFORE you submit iOS.** App Store Connect → your app →
   Featuring → Nominations. The "App Launch" type can only ever be used once per app and the window
   **closes permanently at submission.** Three-week lead time recommended. It accepts 5 supplemental
   URLs and a free-text field asking what makes the app unique — most indies leave all of it blank.
   Apple's editors reportedly score on UX, design, innovation, uniqueness, accessibility,
   **localization**, and product-page quality. Your 12 languages with native digit glyphs hit the
   axis that's usually a solo dev's weakest.
5. **Play featuring + Indie Corners forms** — `support.google.com/googleplay/contact/featuring_review`
   and `/indie_corners`. Gated on ~120 days post-launch and a 3.0+ rating, so the clock starts when
   you submit. Google's own featuring guide says non-phone form factors are the fastest-growing
   segment — **you qualify twice: standalone Wear OS and a foldable-targeted phone app.**
6. **Play Promotional Content (LiveOps)** — free, needs no featuring eligibility, always appears on
   your own listing and may surface on the Play homepage. Google reports a median **+25%** uplift.

### Best hook assets, ranked

1. **The colour flip at the interval boundary.** The only thing in the app that is inherently a
   video event rather than a screenshot, it lands a hard beat inside the 3-second window, and it is
   the literal product claim.
2. **The perimeter stroke draining.** The only element that reads at feed thumbnail size.
3. **Cistercian numerals.** Best curiosity hook, worst install intent — the viewer wants the fact,
   not the timer. (No existing TikTok trend to ride; it *has* hit HN four times: 171, 149, 140, 51
   points.)
4. **The 12-language countdown.** Good carousel, medium hook, pulls non-English For You pages which
   are far less competitive than US fitness.
5. **The aurora shader.** Weakest hook, strongest background — too subtle to survive a 3-inch
   viewport at scroll speed. Put it *under* everything; never make it the subject.

### The fifteen videos

Full concepts with first-1.5-second hooks are in the research; the six that matter most:

- **The Ugly Cut** — 1.2s of a hideous generic timer you mock up yourself (grey gradient, bevelled
  buttons, three clashing fonts), held dead still and silent, then a hard cut to the green Work
  screen on the frame the perimeter stroke ignites. Text at 1.5s: "every interval timer" / "this
  one". *Never show or name a real competitor.*
- **The Flip** — extreme close-up on 3, 2, 1 with the tone audible; at 1.5s the screen slams green
  to blue. Loop it seamlessly so it plays four times before anyone notices. 50% completion is the
  threshold where TikTok widens distribution.
- **Across the Room** — phone on the gym floor, camera 15 feet back, everything out of focus except
  the glowing phone. VO: "you can read this from across the room." This turns the aesthetic into a
  *function*, which is what converts a fitness viewer — and it solves the shader's problem, because
  at distance the subtlety becomes the asset.
- **25 and 5** — desk at night, book, 25 in green. "pomodoro but it's not ugly." **StudyTok is
  probably a better-matched audience than GymTok**: there the timer is on screen for hours as an
  aesthetic object; in the gym it's a beep in someone's pocket. "Aesthetic study timer" is an
  established, actively-searched category with its own listicle ecosystem. You already ship this —
  25/5 is just a preset.
- **Zero Permissions** — Android app-info screen, Permissions tapped, empty list. "no internet
  permission. it literally cannot phone home." Unfalsifiable on camera, and the most
  screenshot-and-shareable fact you own.
- **Drift** — split screen against a stopwatch, both started together, timecode overlay: "same
  start. 45 minutes later." Cut to both matched to the frame. Craft *proof* rather than craft
  *claim*; "you'd think Apple made it" needs one piece of evidence, not an adjective.

### 30-day schedule

- **Days −3 to 0** (only once both listings are live): bank 20 clips in two sessions — one of pure
  screen capture (every theme, every language, editor drags, flips, Minimal, Wear), one of physical
  setups (gym floor, desk at night, across-the-room). Business/Professional accounts set up. Bio
  links point at the custom store pages.
- **Days 1–7:** 2 TikToks/day, 1 Reel/day, 11:00 and 19:00 local. **Do not read results yet.**
- **Days 8–14:** find the two clips with the highest **3-second retention** — ignore view counts
  entirely; at sub-10k followers you get 300–1,000 views regardless of quality, so views carry
  almost no signal and retention carries all of it. Reshoot each winner four ways. 70%
  winner-variants, 30% new.
- **Days 15–21:** 20 personalised DMs to 5k–40k creators across GymTok, HyroxTok, boxing/Muay Thai
  round-timer, and StudyTok. Your only non-cash currency is exclusivity and credit — **offer a
  colour theme named after them** plus a named preset. (Gifting doesn't work; the app is free, so it
  has zero gift value.) Budget **$600–1,200** to pay two of them for one dedicated video each — a
  10k–100k fitness creator runs $400–1,200 all-in.
- **Days 22–30:** run every new hook through **Instagram Trial Reels** first (publishes to
  non-followers only, never touches your grid; kill anything under 40% 3-second view rate). Day 30,
  read installs, installs-per-1000-views, and which hook produced them. **If installs are under
  150, the answer is not more videos** — it's the creator budget or Apple Search Ads.

For calibration: buying 1,000 Android installs costs ~$1,900–2,500 (Android CPI $1.92, TikTok Ads
$2.45). That's the value ceiling of the organic effort, and the honest reason $1,000 is harder than
it sounds.

### Channels that aren't TikTok

- **Hacker News.** Do *not* post it as a timer — every interval-timer Show HN in 880+ indexed posts
  scored 1–11 points, median 2, zero front pages. **Post the zero-permissions angle.** "Show HN:
  Gander, an Android file viewer that asks for no permissions" scored **210 points nine days ago**.
  Caveat: every top-scoring Android Show HN of the last 18 months is FOSS or local-first, and Gander
  linked a GitHub repo, not a store page. Your repo is already public MIT — lead with it. Tue–Thu,
  9am–12pm ET; ~30–50 upvotes in the first hour is the front-page bar.
- **r/apphookup** (204k, deals-only) — structurally unavailable to a free app, available to a paid
  one or a launch discount. *Note: I could not verify a single subreddit's rules — Reddit is blocked
  to my tools. Read the sidebars yourself before posting.* The one universal norm: disclose you're
  the dev in the first sentence, real screen recordings not store badges, no shortened links.
- **Indie App Catalog** — free open submissions, Apple-only, auto-posted after review.
- **Sidebar.io** — 5 design links every weekday, open submissions, must be genuinely new.
- **MacStories and similar** want the pitch **pre-launch**. iOS has never shipped, so that window is
  open and closes permanently at release.
- **Product Hunt** — low yield now. Majority of 300+ daily launches get 0–2 upvotes.

---

## 5. First $1,000

The binding constraint is downloads, not price — which is why the monetization decision, though
worth getting right, is not what determines the outcome.

- **Months 1–2.** Both stores live, free, iOS carrying the unlock. Post 2/day. Expect 100–400
  downloads, $10–30. This phase is calibration, not revenue.
- **Months 3–6.** The levers that actually move volume, none of them pricing: the Apple App Launch
  nomination, the Play featuring + Indie Corners forms, custom store pages (+22.9%), Play
  Promotional Content (+25%), and $600–1,500 of creator seeding.
- **Months 6–18.** $1,000 cumulative lands here if downloads compound past ~12,000. If they don't,
  the answer is more distribution, **never a cheaper price**.

Three free things that move the number more than any price change:

1. **Enrol in Apple's Small Business Program and confirm Play's 15% tier BEFORE the first sale.**
   Apple's 15% only applies **15 days after the fiscal month in which enrolment is approved** — so
   launching first bills your best sales week at 30%. It's a 90-second form in Agreements, Tax and
   Banking. 30% → 15% is 236 sales instead of 331.
2. **File the Apple featuring nomination before submitting iOS.** One shot per app, ever.
3. **Ship an in-app review prompt in v1.1.** Neither codebase has one — I grepped both. This is the
   largest gap between the current build and the 1,000-download goal. Fire on the 3rd completed
   workout, 3+ days after install, once ever. **Do not gate it behind "do you like the app?"** —
   Google bans that verbatim. iOS: `AppStore.requestReview(in: scene)` (`SKStoreReviewController` is
   deprecated in iOS 18). ~15 lines per platform. 1,000 downloads yields ~10–30 ratings, which is
   enough to stop the page looking abandoned and to clear Play's 3.0 featuring gate.

---

## 6. What to build, and what it costs

Measured on a real `assembleRelease` of this repo — not estimated.

| Option | LOC | New permissions | Store forms | APK |
|---|---|---|---|---|
| **Paid up front** | 0 | none | none | 1.50 MB |
| **One-time unlock (Billing/StoreKit)** | ~100 Kotlin + ~100 Swift | none, if stripped | **none** | +67,987 B (+4.5%) |
| **AdMob** | 200–300 + consent | **six**, incl. `AD_ID` | Data Safety, privacy label, IARC, ads declaration, ATT | **+2,024,089 B (+135%)** |

Key findings for the unlock path:

- **Play Billing DOES pull `INTERNET` and `ACCESS_NETWORK_STATE` into the merged manifest** — not
  from the billing AAR, but from `com.google.android.datatransport:transport-backend-cct:3.1.8`.
  **Three lines of `tools:node="remove"` strip both back out**, verified against a clean release
  build with this project's fatal lint passing.
- **Adding Billing changes nothing on the Play Data Safety form.** It stays "No data collected".
  `docs/PLAY_SUBMISSION.md` §1 needs no edit. Apple says the same: payment info you never access
  isn't collected, so the "Data Not Collected" label survives. **`PRIVACY.md` needs one edited
  paragraph.**
- **You must `acknowledgePurchase` within 3 days** of a purchase reaching `PURCHASED` or Google
  automatically refunds and revokes it. This is the #1 way a naive integration silently loses money.
- Entitlement persistence with no backend: `queryPurchasesAsync` is the source of truth, a
  `Settings.kt` boolean is the cache, and **the cache only downgrades when Play explicitly says
  "not owned"** — never on a failed query, or a flaky cold launch locks out a paying customer.
- Billing needs **zero** new ProGuard rules; the AAR ships its own consumer rules.
- **iOS needs a Restore Purchases button** wired to `AppStore.sync()` — its absence is the standard
  3.1.1 rejection.

### Two 20-minute checks that gate everything

1. **The Billing smoke test.** Add Billing with `tools:node="remove"`, install on the Flip 7 with a
   licence-tester account, complete one real test purchase. The architecture says it works —
   `BillingClient` binds an AIDL service inside the Play Store app and the purchase UI is Play's own
   `ProxyBillingActivity`, so your process opens no sockets — but **nobody has actually completed a
   transaction with the permission stripped.** If it passes, the absolute privacy claim survives
   monetization forever and the Zero Permissions video is safe. If it fails, you reword one sentence
   in `PRIVACY.md` to "no network code of our own" — **you do not add ads.**
2. **Confirm App Store Connect has answered Apple's updated age-rating questionnaire.** Deadline was
   31 Jan 2026; unanswered apps cannot submit.

Also unverified and worth checking the same way: whether `com.google.android.play:review` merges
`INTERNET`. Run the merged-manifest check before shipping the review prompt.

---

## 7. The case against this plan — now historical

Kept at full strength because it was good, one of three judges picked it, and you should know what
the Play submission cost. **It is no longer actionable on Android** (§0), and taking it on iOS alone
means a split model, which all three judges rejected.

**Charge $4.99 up front, ship zero code, take the money.** 236 sales clears $1,000 versus ~20,000
downloads through a freemium funnel — paid is **10× more traffic-efficient**, and traffic is the
binding constraint on everything. RevenueCat's 2026 data: hard-paywall D14 revenue per install
$2.32 vs $0.27 freemium, an 8.6× gap that already prices the install penalty in. **At $4.99 paid,
1,000 downloads = $4,240 and your two goals collapse into one milestone.**

And the brand argument runs *against* where I landed. A price tag is the most honest expression of
"this is well-made." No lock badges on six of your eight themes. No "Unlock everything" row squatting
in a Settings screen you designed carefully. No moment where someone who loves the app finds a part
they aren't allowed to have. Every freemium app has a seam where the free version was deliberately
made worse, and an app claiming Apple-grade craft is exactly where that seam shows most.

**Why I still didn't pick it:** your stated goal order, the impulse-traffic problem, and the one-way
doors on Play. But the weakest number in this entire document is the 10× paid-download penalty —
explicitly low-confidence, no clean same-app A/B published in 2025–26, practitioner range 5–20×.
And there is **no elasticity data at all** for paid-up-front pricing in this category; only two live
paid price points exist. The $2.99-vs-$4.99 call is inference from unlock data, not measurement.

**If you'd rather have $1,700 and 900 downloads than $80 and 4,000 — say so and it's one Console
field.** That's a legitimate preference, not a mistake.

---

## 8. Checklist

1. ~~Submit the Play release.~~ **Done 2026-08-07.** Now wait out review; don't touch the listing
   mid-review unless it's rejected.
2. **File the Apple App Launch featuring nomination — before the iOS release date.** App Store
   Connect → your app → Featuring → Nominations. The **App Launch** type is specifically for a new
   app entering the store or a pre-order, and Apple wants it **6–8 weeks ahead** of the release date
   (2 weeks minimum, 3+ recommended). It is inherently pre-release: once the app is live that type
   no longer applies and you use **App Enhancements** at the first meaningful update instead. It
   accepts 5 supplemental URLs and a free-text "what makes this unique" field — most indies leave
   all of it blank.
3. **Enrol in Apple's Small Business Program.** 90 seconds, and it must land before the first sale —
   the 15% rate only starts 15 days after the fiscal month in which enrolment is approved.
4. **Attach the $4.99 unlock to the iOS 1.0 submission if it hasn't shipped.** Apple requires the
   first non-consumable to be submitted *with a new app version*; attaching it now costs one
   App Store Connect field, deferring it costs a full extra review cycle, permanently.
5. Fix the listing — **after review clears**, so nothing perturbs an in-flight submission. The Wear
   app is **standalone, not a "companion"**; Google gives top-chart and curated-collection
   eligibility specifically to standalone watch apps, and the current wording forfeits it. Also tick
   Play Console → Advanced Settings → Form factors → Add Wear OS, and get the literal string
   "Wear OS" into the listing.
6. Fill the dead space: the Play full description uses 740 of 4,000 indexed characters (**Play
   indexes the whole thing; Apple indexes none of it**), the short description 55 of 80, and the iOS
   subtitle wastes 17 of 27 characters repeating "Interval Timer", which the name already indexes.
   Put HIIT back — it's the highest-volume term in the category and currently appears nowhere.
7. Build the two custom store pages before posting a single video.
8. **Store-asset bug:** `docs/appstore-assets/screenshots/04-languages.png` has the Dynamic Island
   rendered as a floating black blob over the Spidey/Miami theme row. Re-shoot it.

Then, later and unhurried: ~100 lines of Kotlin, ~100 of Swift, the smoke test, the review prompt.

---

## 9. Where this research is weak

The fact-checking agent died on a session limit before it could run, so these are unaudited:

- **The 10× paid-download penalty** is the load-bearing number in the whole model and it is the
  weakest. Only hard datapoint: Seconds Pro 59,239 vs Seconds free 1,104,425 (20:1) — for an
  *established* brand, and a zero-review unknown should expect worse, not better.
- **No published Health & Fitness eCPM for 2026 exists.** Every ad figure is a cross-category blend
  discounted by judgment. It doesn't change the conclusion — the gap is three orders of magnitude —
  but the ad numbers are the softest in the document.
- **Attach rate is unmeasurable in advance.** No competitor publishes conversion. The 0.5–3% band is
  a 6× swing and it's what decides whether $1,000 needs 8,000 or 47,000 downloads.
- **Zero Reddit rules were verified.** Reddit is blocked to my tools and every search result was
  AI-generated SEO. Read the sidebars yourself.
- **Apple ASO keyword volume data is unreliable now** — in Sept 2025 the count of US keywords with
  a visible popularity score fell 77.4% in four days when Apple stopped exposing scores below 50.
  Don't buy an ASO tool expecting real numbers.
