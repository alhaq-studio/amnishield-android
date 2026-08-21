package com.alhaq.amnshield.premium

object PremiumProducts {
    const val PRODUCT_LIFETIME = "amnishield_premium_lifetime"
    const val PRODUCT_MONTHLY = "amnishield_premium_monthly"
    const val PRODUCT_YEARLY = "amnishield_premium_yearly"

    // Legacy IDs without 'i' for backwards compatibility
    private const val LEGACY_PRODUCT_LIFETIME = "amnshield_premium_lifetime"
    private const val LEGACY_PRODUCT_MONTHLY = "amnshield_premium_monthly"
    private const val LEGACY_PRODUCT_YEARLY = "amnshield_premium_yearly"

    val allInAppProducts = listOf(PRODUCT_LIFETIME, LEGACY_PRODUCT_LIFETIME)
    val allSubscriptionProducts = listOf(PRODUCT_MONTHLY, PRODUCT_YEARLY, LEGACY_PRODUCT_MONTHLY, LEGACY_PRODUCT_YEARLY)
}

