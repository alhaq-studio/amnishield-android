package com.alhaq.amnishield.api

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking

/**
 * The bound service that Guardian and other authorized clients bind to.
 * Checks calling package permissions using [ApiAuthStore].
 */
class AmniShieldApiService : Service() {

    private val gson = Gson()

    private val binder = object : IAmniShieldApi.Stub() {

        override fun apiVersion(): Int = AmniShieldApiContract.API_VERSION

        override fun isGranted(): Boolean = callerAllowed()

        override fun execute(command: String?, args: Bundle?): String {
            if (!callerAllowed()) return AmniShieldApiContract.STATUS_DENIED
            if (ApiCommand.fromNameOrNull(command) == null) return AmniShieldApiContract.STATUS_UNKNOWN_COMMAND
            return try {
                val applied = runBlocking {
                    AmniShieldApiCommands.runCommand(applicationContext, command, args ?: Bundle())
                }
                if (applied) AmniShieldApiContract.STATUS_OK else AmniShieldApiContract.STATUS_FAILED
            } catch (e: Exception) {
                Log.e(TAG, "execute failed for $command", e)
                AmniShieldApiContract.STATUS_FAILED
            }
        }

        override fun query(state: String?): String? {
            if (!callerAllowed()) return null
            return try {
                val values = runBlocking {
                    AmniShieldApiCommands.queryState(applicationContext, state)
                } ?: return null
                gson.toJson(values)
            } catch (e: Exception) {
                Log.e(TAG, "query failed for $state", e)
                null
            }
        }

        override fun list(kind: String?): String? {
            if (!callerAllowed()) return null
            return try {
                val data = runBlocking {
                    AmniShieldApiCommands.list(applicationContext, kind)
                } ?: return null
                gson.toJson(data)
            } catch (e: Exception) {
                Log.e(TAG, "list failed for $kind", e)
                null
            }
        }
    }

    private fun callerAllowed(): Boolean {
        val packages = packageManager.getPackagesForUid(Binder.getCallingUid())
        // For development, always allow the Guardian/Firewall apps and standard debug applications.
        if (packages != null && (
            packages.contains("com.alhaq.amnishield.guardian") ||
            packages.contains("com.alhaq.amnishield.guardian.debug") ||
            packages.contains("com.alhaq.amguard") ||
            packages.contains("com.alhaq.amniguard") ||
            packages.contains("com.alhaq.amniguard") ||
            packages.contains("com.alhaq.amnishield.netblock")
        )) {
            return true
        }
        return ApiAuthStore.isAnyGranted(applicationContext, packages)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        private const val TAG = "AmniShieldApiService"
    }
}
