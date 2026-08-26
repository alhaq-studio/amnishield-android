package com.alhaq.amnshield.premium

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.alhaq.amnshield.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object PaymentManager {

    private const val SUPABASE_CHECKOUT_URL = "https://jrgpmcomvibgklmvnxud.supabase.co/functions/v1/stripe-checkout"
    private const val FALLBACK_PRICING_URL = "https://amnishield.com/#pricing"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    interface CheckoutCallback {
        fun onSuccess(checkoutUrl: String)
        fun onError(error: String)
    }

    /**
     * Start Stripe Checkout session via Supabase Edge Function for Universal & F-Droid builds.
     */
    fun openStripeCheckout(
        context: Context,
        plan: String = "annual",
        customerEmail: String = "",
        callback: CheckoutCallback? = null
    ) {
        if (BuildConfig.IS_PLAYSTORE) {
            mainHandler.post {
                Toast.makeText(
                    context,
                    "Google Play distribution mode active. Please visit alhaq.uk on the web to manage subscription.",
                    Toast.LENGTH_LONG
                ).show()
                callback?.onError("Play Store build restriction")
            }
            return
        }

        mainHandler.post {
            Toast.makeText(context, "Connecting to Supabase Checkout...", Toast.LENGTH_SHORT).show()
        }

        executor.execute {
            var finalUrl: String? = null

            try {
                val url = URL(SUPABASE_CHECKOUT_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("plan", plan)
                    put("customerEmail", customerEmail.ifEmpty { null })
                    put("successUrl", "https://app.amnishield.com/?checkout=success")
                    put("cancelUrl", "https://amnishield.com/#pricing")
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseStr = reader.readText()
                    reader.close()

                    val json = JSONObject(responseStr)
                    finalUrl = json.optString("url")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback to web pricing page if Edge function returned empty or errored
            val checkoutUrlToLaunch = if (!finalUrl.isNullOrEmpty()) {
                finalUrl
            } else {
                "$FALLBACK_PRICING_URL?plan=$plan"
            }

            mainHandler.post {
                try {
                    callback?.onSuccess(checkoutUrlToLaunch)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrlToLaunch)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not launch web browser.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
