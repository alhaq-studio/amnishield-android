package com.alhaq.amnishield.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.alhaq.amnishield.R
import com.alhaq.amnishield.data.AmniShieldAccount
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class GoogleSignInHelper(private val context: Context) {

    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?): AmniShieldAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            account?.let {
                AmniShieldAccount(
                    displayName = it.displayName,
                    email = it.email,
                    photoUrl = it.photoUrl,
                    idToken = it.idToken
                )
            }
        } catch (e: ApiException) {
            null
        }
    }

    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }

    fun getLastSignedInAccount(): AmniShieldAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account?.let {
            AmniShieldAccount(
                displayName = it.displayName,
                email = it.email,
                photoUrl = it.photoUrl,
                idToken = it.idToken
            )
        }
    }

    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }
}
