package com.alhaq.amnishield.utils

import android.content.Context
import android.content.Intent
import com.alhaq.amnishield.data.AmniShieldAccount

/**
 * FOSS stub implementation of GoogleSignInHelper for F-Droid.
 * Bypasses GMS Sign-In dependencies entirely.
 */
class GoogleSignInHelper(private val context: Context) {

    fun getSignInIntent(): Intent {
        // Return dummy intent
        return Intent()
    }

    fun handleSignInResult(data: Intent?): AmniShieldAccount? {
        return null
    }

    fun signOut(onComplete: () -> Unit) {
        onComplete()
    }

    fun getLastSignedInAccount(): AmniShieldAccount? {
        return null
    }

    fun isSignedIn(): Boolean {
        return false
    }
}
