package com.pratul.mmplayer.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One tip size, with its price as Google Play shows it to this user (in their currency). */
data class TipOption(val emoji: String, val title: String, val price: String, val details: ProductDetails)

sealed class TipJarState {
    data object Loading : TipJarState()
    /** Tips can't be offered, e.g. the app wasn't installed from Google Play, or the products aren't set up yet. */
    data class Unavailable(val message: String) : TipJarState()
    data class Ready(val options: List<TipOption>) : TipJarState()
}

/**
 * "Buy me a coffee" tips through Google Play Billing. Each tip is a consumable in-app product, so
 * it can be bought again. Every paid tip is consumed (completed) straight away, or Google refunds it
 * after 3 days; tips still pending (e.g. a UPI payment awaiting approval) are completed the next time
 * the app connects.
 *
 * The products must exist in Play Console with exactly the ids in [PRODUCTS].
 */
class TipJar(context: Context) : PurchasesUpdatedListener {

    private data class Product(val id: String, val emoji: String, val title: String)

    companion object {
        private val PRODUCTS = listOf(
            Product("tip_coffee", "☕", "Coffee"),
            Product("tip_coffee_cake", "🍰", "Coffee & cake"),
            Product("tip_big_thanks", "🎉", "Big thanks")
        )
        private const val NOT_FROM_PLAY =
            "Tips are available in the Google Play Store version of the app."
    }

    var state by mutableStateOf<TipJarState>(TipJarState.Loading)
        private set

    /** Goes up by one each time a tip completes, so the screen can say thanks. */
    var completedTips by mutableIntStateOf(0)
        private set

    /** True while a tip has been paid for but is waiting for approval (e.g. UPI or cash payments). */
    var hasPendingTip by mutableStateOf(false)
        private set

    /** A short problem message to show once (purchase failed), or null. */
    var problem by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) {
            scope.launch { refresh() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { refresh() }
                } else {
                    state = TipJarState.Unavailable(NOT_FROM_PLAY)
                }
            }

            // Reconnected next time connect() is called (the About page calls it when it opens)
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Loads prices, and completes any tips that were paid while the app wasn't looking. */
    private suspend fun refresh() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(PRODUCTS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it.id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()
        // Billing calls run in the background: on phones where Google Play is missing or broken they can
        // take a while to fail, and must never freeze the screen
        val details = withContext(Dispatchers.IO) { client.queryProductDetails(params) }.productDetailsList.orEmpty()
        val options = PRODUCTS.mapNotNull { product ->
            val d = details.firstOrNull { it.productId == product.id } ?: return@mapNotNull null
            val offer = d.oneTimePurchaseOfferDetails ?: return@mapNotNull null
            TipOption(product.emoji, product.title, offer.formattedPrice, d)
        }
        state = if (options.isEmpty()) TipJarState.Unavailable(NOT_FROM_PLAY) else TipJarState.Ready(options)

        val owned = withContext(Dispatchers.IO) {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            )
        }
        hasPendingTip = false
        owned.purchasesList.forEach { handle(it) }
    }

    /** Opens Google Play's payment sheet for this tip. */
    fun buy(activity: Activity, option: TipOption) {
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(option.details).build())
            )
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            problem = "Couldn't open Google Play payment (${result.debugMessage.ifBlank { "code ${result.responseCode}" }})"
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { scope.launch { handle(it) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            // An earlier tip of the same size wasn't completed yet: complete it, then they can tip again
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
            else -> problem = "The tip didn't go through. You haven't been charged."
        }
    }

    private suspend fun handle(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val consumed = withContext(Dispatchers.IO) {
                    client.consumePurchase(
                        ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    )
                }
                if (consumed.billingResult.responseCode == BillingClient.BillingResponseCode.OK) completedTips++
            }
            Purchase.PurchaseState.PENDING -> hasPendingTip = true
        }
    }

    fun close() {
        client.endConnection()
        scope.cancel()
    }
}
