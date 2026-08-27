package com.alhaq.amnishield.utils

import android.app.Activity
import android.content.Context
import com.alhaq.amnishield.data.AmniShieldProductDetails

/**
 * FOSS stub implementation of BillingClientWrapper for F-Droid.
 * Excludes GMS BillingClient dependencies entirely.
 */
class BillingClientWrapper(private val context: Context) {

    fun startConnection(onConnected: () -> Unit) {
        // Invoke callback directly
        onConnected()
    }

    fun queryProducts(productIds: List<String>, onProductsQueried: (List<AmniShieldProductDetails>) -> Unit) {
        // Return dummy/mock FOSS offline product prices
        val mockDetails = productIds.map { productId ->
            val mockPrice = when (productId) {
                "premium_lifetime" -> "$29.99"
                "premium_monthly" -> "$1.99"
                "premium_yearly" -> "$14.99"
                else -> "$0.00"
            }
            AmniShieldProductDetails(productId, mockPrice)
        }
        onProductsQueried(mockDetails)
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: AmniShieldProductDetails, onPurchaseFinished: (Boolean, String) -> Unit) {
        // Purchases are unsupported in FOSS F-Droid variant (should use Special Access or Compassionate Access instead)
        onPurchaseFinished(false, "In-app purchases are not supported in this F-Droid (FOSS) version. Please use Special Access or Compassionate Access.")
    }

    fun queryPurchases(onPurchasesQueried: (List<String>) -> Unit) {
        // No purchases exist in FOSS version
        onPurchasesQueried(emptyList())
    }

    fun endConnection() {
        // No-op in F-Droid stub
    }
}
