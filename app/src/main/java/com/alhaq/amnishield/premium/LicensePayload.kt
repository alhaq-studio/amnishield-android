package com.alhaq.amnishield.premium

data class LicensePayload(
    val email: String,
    val type: String,
    val expires: Long,
    val user_id: String? = null,
    val app_id: String? = null,
    val version: Int = 1
)
