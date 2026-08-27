package com.alhaq.amnishield.data

import android.net.Uri

data class AmniShieldAccount(
    val displayName: String?,
    val email: String?,
    val photoUrl: Uri? = null,
    val idToken: String? = null
)
