package com.chrispoole.intervaltimer

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.chrispoole.intervaltimer.ui.Palette

/** The one thing there is to buy. Must match the Play Console product ID exactly. */
const val UNLOCK_SKU = "unlock"

/** Themes that never cost anything: the app has to be complete and good-looking without paying. */
val FREE_PALETTES = setOf(Palette.DEFAULT, Palette.MONO)

/** Saved sequences on the free tier. Built-ins don't count — they aren't in presets.json. */
const val FREE_PRESET_SLOTS = 3

// The two gate rules as plain functions of (thing, entitlement), so they're testable without a
// BillingClient, a Play Store, or an Activity. The Billing object below is only the entitlement.
fun paletteLocked(p: Palette, unlocked: Boolean) = !unlocked && p !in FREE_PALETTES
fun presetsFull(savedCount: Int, unlocked: Boolean) = !unlocked && savedCount >= FREE_PRESET_SLOTS

/**
 * The $2.99 one-time unlock: six themes and unlimited saved sequences. Nothing about the timer
 * itself is behind it — not a phase, not a language, not background running.
 *
 * Play is the source of truth, re-queried on every launch, so "restore" is not a thing the user has
 * to do — the row exists anyway for the case where they bought it thirty seconds ago on another
 * device. No receipt is cached beyond [purchased], because a cache is a thing that can be wrong and
 * queryPurchasesAsync is free.
 */
object Billing {
    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    /** Bought it, on this Play account. */
    var purchased by mutableStateOf(false); private set

    /**
     * Installed before the unlock existed. These people already have all eight themes and as many
     * presets as they made; taking them away in an update is how you earn a one-star review from
     * someone who liked the app. Decided once, on the first launch of the version that added this,
     * and written down — `firstInstallTime < lastUpdateTime` means "this install was updated into
     * the paywall", but it stays true for every later update too, so it must not be re-asked.
     */
    private var legacy = false

    /** What everything else asks. Owning it and predating it are the same thing to the app. */
    val unlocked: Boolean get() = purchased || legacy

    /** Play's own formatted price in the user's currency — never a hardcoded "$2.99" in the UI. */
    var price by mutableStateOf<String?>(null); private set

    /** Set by any gate the user walks into; the root composable shows the sheet when it's true. */
    var paywall by mutableStateOf(false)

    fun init(context: Context) {
        if (client != null) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("billing", Context.MODE_PRIVATE)
        if (!prefs.contains("legacy")) {
            val pi = app.packageManager.getPackageInfo(app.packageName, 0)
            prefs.edit { putBoolean("legacy", pi.firstInstallTime < pi.lastUpdateTime) }
        }
        legacy = prefs.getBoolean("legacy", false)

        client = BillingClient.newBuilder(app)
            .setListener { result, purchases -> onPurchases(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (c.isReady) { refresh(); return }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }
            // No retry loop. Nothing is broken while billing is down — the app is a timer, and the
            // next launch (or the next tap on Unlock) reconnects.
            override fun onBillingServiceDisconnected() {}
        })
    }

    /** Re-ask Play what this account owns and what the unlock costs. Cheap; called on every launch. */
    fun refresh() {
        val c = client?.takeIf { it.isReady } ?: run { connect(); return }
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { _, purchases -> onPurchases(null, purchases) }

        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(UNLOCK_SKU)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build(),
        ) { _, result ->
            details = result.productDetailsList.firstOrNull()
            price = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** Opens Play's own sheet. Silently a no-op if billing never connected — see [connect]. */
    fun purchase(activity: Activity) {
        val c = client?.takeIf { it.isReady } ?: run { connect(); return }
        val pd = details ?: run { refresh(); return }
        c.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build()),
            ).build(),
        )
    }

    /**
     * One path for both a fresh purchase and a launch-time restore, so the two can't disagree about
     * what counts as owned. PENDING (cash at a kiosk, a parent's approval) deliberately grants
     * nothing yet — it isn't paid — and resolves on a later launch's query.
     */
    private fun onPurchases(result: BillingResult?, purchases: List<Purchase>?) {
        if (result != null && result.responseCode != BillingClient.BillingResponseCode.OK) return
        val owned = purchases.orEmpty().filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && UNLOCK_SKU in it.products
        }
        if (owned.isEmpty()) return
        purchased = true
        paywall = false
        // Unacknowledged for three days and Play refunds it automatically, so this is the whole
        // difference between being paid and not.
        owned.filterNot { it.isAcknowledged }.forEach { p ->
            client?.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
            ) { }
        }
    }
}
