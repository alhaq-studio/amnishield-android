package com.alhaq.amnishield.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alhaq.amnishield.data.AmniShieldProductDetails
import com.alhaq.amnishield.premium.PremiumProducts
import com.android.billingclient.api.*

class BillingClientWrapper(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var onPurchaseFinished: ((Boolean, String) -> Unit)? = null
    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()

    companion object {
        private const val TAG = "BillingClientWrapper"
    }

    fun startConnection(onConnected: () -> Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    mainHandler.post {
                        try {
                            onConnected()
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error in onConnected callback", e)
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    fun queryProducts(productIds: List<String>, onProductsQueried: (List<AmniShieldProductDetails>) -> Unit) {
        val inAppProductList = productIds.filter { it in PremiumProducts.allInAppProducts }.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val subProductList = productIds.filter { it in PremiumProducts.allSubscriptionProducts }.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val results = mutableListOf<AmniShieldProductDetails>()
        var pendingQueries = 0
        if (inAppProductList.isNotEmpty()) pendingQueries++
        if (subProductList.isNotEmpty()) pendingQueries++

        if (pendingQueries == 0) {
            mainHandler.post { onProductsQueried(emptyList()) }
            return
        }

        val checkAndCallback = {
            pendingQueries--
            if (pendingQueries == 0) {
                mainHandler.post {
                    try {
                        onProductsQueried(results)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error in onProductsQueried callback", e)
                    }
                }
            }
        }

        if (inAppProductList.isNotEmpty()) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(inAppProductList).build()
            billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val productDetailsList = queryResult.productDetailsList
                    val mapped = productDetailsList.map { details ->
                        cachedProductDetails[details.productId] = details
                        val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                            ?: details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                            ?: ""
                        AmniShieldProductDetails(details.productId, price)
                    }
                    synchronized(results) { results.addAll(mapped) }
                } else {
                    Log.w(TAG, "Failed to query INAPP products: ${billingResult.debugMessage}")
                }
                checkAndCallback()
            }
        }

        if (subProductList.isNotEmpty()) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(subProductList).build()
            billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val productDetailsList = queryResult.productDetailsList
                    val mapped = productDetailsList.map { details ->
                        cachedProductDetails[details.productId] = details
                        val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                            ?: details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                            ?: ""
                        AmniShieldProductDetails(details.productId, price)
                    }
                    synchronized(results) { results.addAll(mapped) }
                } else {
                    Log.w(TAG, "Failed to query SUBS products: ${billingResult.debugMessage}")
                }
                checkAndCallback()
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: AmniShieldProductDetails, onPurchaseFinished: (Boolean, String) -> Unit) {
        this.onPurchaseFinished = onPurchaseFinished
        val realDetails = cachedProductDetails[productDetails.productId]
        if (realDetails == null) {
            dispatchPurchaseFinished(false, "Product details not found or cached")
            return
        }
        val paramBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(realDetails)

        val offerToken = realDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken != null) {
            paramBuilder.setOfferToken(offerToken)
        }

        val productDetailsParamsList = listOf(paramBuilder.build())
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Query existing purchases to restore premium status.
     * This works for both real users and License Test accounts.
     * Test accounts will see their test purchases here without being charged.
     */
    fun queryPurchases(onPurchasesQueried: (List<String>) -> Unit) {
        val activePurchases = mutableListOf<String>()
        var pendingQueries = 2

        val checkAndCallback = {
            pendingQueries--
            if (pendingQueries == 0) {
                mainHandler.post {
                    try {
                        onPurchasesQueried(activePurchases)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error in onPurchasesQueried callback", e)
                    }
                }
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Found ${purchases.size} active INAPP purchase(s)")
                val mapped = purchases.mapNotNull { it.products.firstOrNull() }
                synchronized(activePurchases) { activePurchases.addAll(mapped) }
            } else {
                Log.w(TAG, "Failed to query INAPP purchases: ${billingResult.debugMessage}")
            }
            checkAndCallback()
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Found ${purchases.size} active SUBS purchase(s)")
                val mapped = purchases.mapNotNull { it.products.firstOrNull() }
                synchronized(activePurchases) { activePurchases.addAll(mapped) }
            } else {
                Log.w(TAG, "Failed to query SUBS purchases: ${billingResult.debugMessage}")
            }
            checkAndCallback()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated - Result code: ${billingResult.responseCode}, Debug message: ${billingResult.debugMessage}")
        
        val isSuccess = billingResult.responseCode == BillingClient.BillingResponseCode.OK
        if (isSuccess && purchases != null) {
            Log.d(TAG, "Processing ${purchases.size} purchase(s)")
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase")
            dispatchPurchaseFinished(false, "User canceled the purchase")
        } else {
            Log.w(TAG, "Purchase failed or canceled: ${billingResult.debugMessage}")
            dispatchPurchaseFinished(false, billingResult.debugMessage)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.d(TAG, "Processing purchase: ${purchase.products}, state: ${purchase.purchaseState}")
        
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "Purchase successful. Acknowledged: ${purchase.isAcknowledged}")
            
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    Log.d(TAG, "Acknowledgment result: ${billingResult.responseCode}")
                    val isAckSuccess = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    dispatchPurchaseFinished(isAckSuccess, billingResult.debugMessage)
                }
            } else {
                dispatchPurchaseFinished(true, "Already acknowledged")
            }
        } else {
            Log.d(TAG, "Purchase not in PURCHASED state: ${purchase.purchaseState}")
            dispatchPurchaseFinished(false, "Purchase state is: ${purchase.purchaseState}")
        }
    }

    private fun dispatchPurchaseFinished(isSuccess: Boolean, message: String) {
        mainHandler.post {
            try {
                onPurchaseFinished?.invoke(isSuccess, message)
            } catch (e: Throwable) {
                Log.e(TAG, "Error invoking onPurchaseFinished callback", e)
            }
        }
    }

    fun endConnection() {
        try {
            if (billingClient.isReady) {
                billingClient.endConnection()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing BillingClient connection", e)
        }
    }
}
