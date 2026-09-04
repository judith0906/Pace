package com.novikon.pace.billing

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.QueryProductDetailsResult

/**
 * Gestiona la facturación de Google Play para la suscripción Premium.
 *
 * Conecta con BillingClient, consulta los productos (mensual/anual),
 * lanza la compra y devuelve el resultado al llamador a través de
 * [onPurchaseResult].
 */
class BillingManager(
    private val activity: Activity,
    private val onPurchaseResult: (Purchase?) -> Unit,
    private val onProductsLoaded: () -> Unit = {}
) : PurchasesUpdatedListener {

    companion object {
        const val MONTHLY_PRODUCT_ID = "pace_premium_monthly"
        const val YEARLY_PRODUCT_ID = "pace_premium_yearly"
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().build())
        .build()

    private var monthlyProduct: ProductDetails? = null
    private var yearlyProduct: ProductDetails? = null
    private var connected = false

    val isConnected: Boolean
        get() = connected

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    queryProducts()
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder().apply {
            setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MONTHLY_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(YEARLY_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
        }.build()

        billingClient.queryProductDetailsAsync(
            params,
            object : ProductDetailsResponseListener {
                override fun onProductDetailsResponse(
                    result: BillingResult,
                    productDetailsResult: QueryProductDetailsResult
                ) {
                    val details = productDetailsResult.productDetailsList
                    monthlyProduct = details.firstOrNull { it.productId == MONTHLY_PRODUCT_ID }
                    yearlyProduct = details.firstOrNull { it.productId == YEARLY_PRODUCT_ID }
                    activity.runOnUiThread {
                        onProductsLoaded()
                    }
                }
            }
        )
    }

    /** Precio formateado del plan mensual, o null si aún no está disponible. */
    fun monthlyPrice(): String? = subscriptionPrice(monthlyProduct)

    /** Precio formateado del plan anual, o null si aún no está disponible. */
    fun yearlyPrice(): String? = subscriptionPrice(yearlyProduct)

    private fun subscriptionPrice(product: ProductDetails?): String? =
        product?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()
            ?.formattedPrice

    fun launchMonthly() = launchSubscription(monthlyProduct, MONTHLY_PRODUCT_ID)

    fun launchYearly() = launchSubscription(yearlyProduct, YEARLY_PRODUCT_ID)

    private fun launchSubscription(product: ProductDetails?, productId: String) {
        if (!connected) {
            startConnection()
            return
        }
        val details = product ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    ?.let { purchase ->
                        onPurchaseResult(purchase)
                        if (!purchase.isAcknowledged) {
                            acknowledge(purchase.purchaseToken)
                        }
                    }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> onPurchaseResult(null)
        }
    }

    private fun acknowledge(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.acknowledgePurchase(
            params,
            object : AcknowledgePurchaseResponseListener {
                override fun onAcknowledgePurchaseResponse(result: BillingResult) {
                    // Acknowledgment completed; nothing else to do.
                }
            }
        )
    }

    fun restorePurchases(callback: (Purchase?) -> Unit = {}) {
        if (!connected) {
            startConnection()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(
            params,
            object : PurchasesResponseListener {
                override fun onQueryPurchasesResponse(
                    result: BillingResult,
                    purchases: List<Purchase>
                ) {
                    val active = purchases.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    callback(active)
                }
            }
        )
    }

    fun endConnection() {
        if (connected) {
            billingClient.endConnection()
            connected = false
        }
    }
}