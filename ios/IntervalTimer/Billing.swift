import Combine
import StoreKit
import SwiftUI

/// The one thing there is to buy. Must match the App Store Connect product ID exactly, and it is
/// deliberately the same string as the Play product ID — one name for one thing across both stores.
let UNLOCK_ID = "unlock"

/// Themes that never cost anything: the app has to be complete and good-looking without paying.
let FREE_PALETTES: Set<Palette> = [.standard, .mono]

/// Saved sequences on the free tier. Built-ins don't count — they aren't in presets.json.
let FREE_PRESET_SLOTS = 3

// The two gate rules as plain functions of (thing, entitlement), so they're testable without
// StoreKit, a sandbox account, or a running app. The Billing object below is only the entitlement.
func paletteLocked(_ p: Palette, unlocked: Bool) -> Bool { !unlocked && !FREE_PALETTES.contains(p) }
func presetsFull(_ savedCount: Int, unlocked: Bool) -> Bool { !unlocked && savedCount >= FREE_PRESET_SLOTS }

/// The $2.99 one-time unlock: six themes and unlimited saved sequences. Nothing about the timer
/// itself is behind it — not a phase, not a language, not background running.
///
/// The App Store is the source of truth, re-read on every launch, so "restore" is not something the
/// user has to do for the app to work — the button exists because Apple requires a visible restore
/// path for a non-consumable, and because a purchase made thirty seconds ago on another device
/// needs a way in. No receipt is cached anywhere it could be wrong.
///
/// Android's twin is `Billing.kt`, and the two are deliberately shaped the same. It has one thing
/// this doesn't: a grandfather flag for installs predating the paywall. iOS has never shipped, so
/// there is nobody to grandfather — the unlock is in version 1.0.0 from the first day, which is
/// also what Apple requires of a first non-consumable.
@MainActor
final class Billing: ObservableObject {
    static let shared = Billing()

    /// Bought it, on this Apple Account.
    @Published private(set) var purchased = false

    /// The App Store's own formatted price in the user's currency — never a hardcoded "$2.99".
    @Published private(set) var price: String?

    /// Set by any gate the user walks into; the root view shows the sheet when it's true.
    @Published var paywall = false

    /// What everything else asks. Named to match Android, where owning it and predating the paywall
    /// are two different things that mean the same to the app.
    var unlocked: Bool { purchased }

    private var product: Product?
    private var watcher: Task<Void, Never>?

    private init() {}

    /// Called once from the root view's `.task`. Reads what this account already owns, fetches the
    /// price, then keeps listening: `Transaction.updates` is how a purchase made on another device,
    /// an Ask-to-Buy approval, or a refund reaches a running app.
    func start() async {
        await refresh()
        watcher = Task { [weak self] in
            for await update in StoreKit.Transaction.updates {
                guard let self else { return }
                if case .verified(let t) = update {
                    await self.apply(t)
                    await t.finish()
                }
            }
        }
    }

    /// Re-ask the App Store what this account owns and what the unlock costs. Cheap, and the only
    /// thing standing between a reinstall and the themes the user already paid for.
    func refresh() async {
        var owns = false
        for await entitlement in StoreKit.Transaction.currentEntitlements {
            // `.unverified` is a transaction whose signature didn't check out. Ignoring it is the
            // whole point of verification — an unverified entitlement is not an entitlement.
            if case .verified(let t) = entitlement, t.productID == UNLOCK_ID, t.revocationDate == nil {
                owns = true
            }
        }
        purchased = owns
        product = try? await Product.products(for: [UNLOCK_ID]).first
        price = product?.displayPrice
    }

    /// Opens the App Store's own sheet. A no-op if the product hasn't loaded — no network, or the
    /// product isn't Ready to Submit in App Store Connect yet — so it re-fetches and lets the user
    /// tap again rather than showing an error for something a second tap usually fixes.
    func purchase() async {
        guard let product else { await refresh(); return }
        guard let result = try? await product.purchase() else { return }
        switch result {
        case .success(let verification):
            if case .verified(let t) = verification {
                await apply(t)
                // Unfinished transactions are replayed to the app forever. Finishing is what tells
                // StoreKit the goods were delivered.
                await t.finish()
            }
        // .pending is Ask to Buy waiting on a parent, and .userCancelled is a closed sheet. Neither
        // is an error, and neither grants anything — `Transaction.updates` delivers the approval if
        // and when it comes.
        case .pending, .userCancelled: break
        @unknown default: break
        }
    }

    /// Apple requires a visible restore control for a non-consumable. `AppStore.sync()` is the one
    /// that prompts for a password; `currentEntitlements` alone already covers the ordinary
    /// reinstall case on launch, which is why this is a button and not the mechanism.
    func restore() async {
        try? await AppStore.sync()
        await refresh()
    }

    private func apply(_ t: StoreKit.Transaction) async {
        guard t.productID == UNLOCK_ID else { return }
        // A refunded or revoked purchase arrives here too, carrying a revocationDate. Honouring it
        // is not optional: keeping the themes switched on after Apple has taken the money back is
        // the kind of thing that ends an account.
        purchased = t.revocationDate == nil
        if purchased { paywall = false }
    }
}

// MARK: - Paywall

/// The paywall. It appears only when the user has just reached for something behind it — a locked
/// theme, or a fourth saved sequence — never on launch, and never over a running workout.
///
/// Deliberately says what stays free before it says what costs money: the timer is the app, and
/// nobody should have to read to the bottom to find out the thing they came for still works.
struct UnlockSheet: View {
    @ObservedObject private var billing = Billing.shared
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text("Unlock everything")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(.white)
            Text("All eight themes.\nUnlimited saved sequences.")
                .font(.system(size: 16))
                .foregroundStyle(.white.opacity(0.85))
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.top, 14)
            Text("The timer, the cues, background running, all \(FREE_PRESET_SLOTS) free slots and "
                 + "every language stay free forever. One payment, no subscription.")
                .font(.system(size: 13))
                .foregroundStyle(.white.opacity(0.45))
                .multilineTextAlignment(.center)
                .lineSpacing(3)
                .padding(.top, 14)

            // The App Store's formatted price, in the user's own currency. Until it arrives the
            // button says what it does and nothing about money — a hardcoded "$2.99" would be a lie
            // in most of the places this app ships.
            GlassPill(
                text: billing.price.map { "Unlock — \($0)" } ?? "Unlock",
                action: { Task { await billing.purchase() } },
                wide: true,
                big: true
            )
            .padding(.top, 22)

            Button("Restore purchase") { Task { await billing.restore() } }
                .font(.system(size: 14))
                .foregroundStyle(.white.opacity(0.5))
                .buttonStyle(.plain)
                .padding(.top, 14)

            Button("Not now", action: onDismiss)
                .font(.system(size: 15))
                .foregroundStyle(.white.opacity(0.5))
                .buttonStyle(.plain)
                .padding(.top, 12)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        // Solid black under the glass: this is presented over the app, and the translucent fill
        // alone would read as a smear of the home screen rather than as a surface.
        .background(Color.black)
        .background(glassFill)
        .presentationDetents([.height(430)])
        .presentationBackground(.black)
        .preferredColorScheme(.dark)
    }
}
