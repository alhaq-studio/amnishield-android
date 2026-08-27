/**
 * ============================================================================
 * AmniShield Diagnostic & Error Report Manager
 * ============================================================================
 * Responsibility:
 * Manages local error and crash diagnostics and user-initiated feedback reports.
 * 
 * Invariants & AI/Developer Guidance:
 * - ZERO External Telemetry: Stored locally by default; users can export via Android Sharesheet.
 * - PII Sanitization: Strips passwords, tokens, auth keys, and emails before output.
 * ============================================================================
 */
package com.alhaq.amnishield.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.alhaq.amnishield.CrashLogger
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized manager for error/crash logs and user feedback collection.
 * Stores locally by default; users can opt-in to share via standard Android Sharesheet.
 */
class ErrorReportManager(private val context: Context) {

    private val errorDir = File(context.filesDir, "error_reports")
    private val crashLogFile = File(errorDir, "crash_log.txt")
    private val feedbackDir = File(errorDir, "feedback")
    private val prefs = context.getSharedPreferences("error_reporting", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        errorDir.mkdirs()
        feedbackDir.mkdirs()
    }

    fun setErrorReportingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("crash_reporting_enabled", enabled).apply()
    }

    fun isErrorReportingEnabled(): Boolean {
        return prefs.getBoolean("crash_reporting_enabled", false)
    }

    fun setFeedbackCollectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feedback_collection_enabled", enabled).apply()
    }

    fun isFeedbackCollectionEnabled(): Boolean {
        return prefs.getBoolean("feedback_collection_enabled", false)
    }

    /**
     * Log a fatal crash with diagnostic information
     */
    fun logCrash(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val crashEntry = StringBuilder()
            
            crashEntry.append("\n\n")
            crashEntry.append("═".repeat(80)).append("\n")
            crashEntry.append("CRASH REPORT\n")
            crashEntry.append("═".repeat(80)).append("\n")
            crashEntry.append("Timestamp: $timestamp\n")
            
            crashEntry.append("\n--- DEVICE INFORMATION ---\n")
            crashEntry.append("Manufacturer: ${Build.MANUFACTURER}\n")
            crashEntry.append("Model: ${Build.MODEL}\n")
            crashEntry.append("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            crashEntry.append("Device Brand: ${Build.BRAND}\n")
            crashEntry.append("Hardware: ${Build.HARDWARE}\n")
            
            crashEntry.append("\n--- APP INFORMATION ---\n")
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            crashEntry.append("App Version: ${packageInfo.versionName} (${packageInfo.longVersionCode})\n")
            crashEntry.append("Package: ${context.packageName}\n")
            
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            val freeMemory = runtime.freeMemory() / 1024 / 1024
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            crashEntry.append("Total Memory: ${totalMemory}MB\n")
            crashEntry.append("Free Memory: ${freeMemory}MB\n")
            crashEntry.append("Max Memory: ${maxMemory}MB\n")
            crashEntry.append("Used Memory: ${totalMemory - freeMemory}MB\n")
            
            crashEntry.append("\n--- CRASH DETAILS ---\n")
            crashEntry.append("Thread: ${thread.name} (ID: ${thread.id})\n")
            crashEntry.append("Exception: ${throwable.javaClass.simpleName}\n")
            crashEntry.append("Message: ${CrashLogger.getInstance(context).sanitize(throwable.message ?: "No message")}\n")
            
            crashEntry.append("\n--- STACK TRACE ---\n")
            crashEntry.append(CrashLogger.getInstance(context).sanitize(throwable.stackTraceToString())).append("\n")
            
            if (throwable.cause != null) {
                crashEntry.append("\n--- CAUSED BY ---\n")
                crashEntry.append(CrashLogger.getInstance(context).sanitize(throwable.cause!!.stackTraceToString())).append("\n")
            }
            
            crashEntry.append("═".repeat(80)).append("\n")
            
            crashLogFile.appendText(crashEntry.toString())
            Log.e("ErrorReportManager", "Crash logged to ${crashLogFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("ErrorReportManager", "Failed to log crash", e)
        }
    }

    /**
     * Log a non-fatal error
     */
    fun logNonFatalError(tag: String, message: String, exception: Exception? = null) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val errorEntry = StringBuilder()
            
            errorEntry.append("\n--- NON-FATAL ERROR at $timestamp ---\n")
            errorEntry.append("Tag: ${CrashLogger.getInstance(context).sanitize(tag)}\n")
            errorEntry.append("Message: ${CrashLogger.getInstance(context).sanitize(message)}\n")
            
            if (exception != null) {
                errorEntry.append("Exception: ${exception.javaClass.simpleName}\n")
                errorEntry.append(CrashLogger.getInstance(context).sanitize(exception.stackTraceToString())).append("\n")
            }
            
            crashLogFile.appendText(errorEntry.toString())
            Log.w(tag, message, exception)
        } catch (e: Exception) {
            Log.e("ErrorReportManager", "Failed to log error", e)
        }
    }

    fun saveFeedback(feedback: UserFeedback): Boolean {
        return try {
            val filename = "feedback_${System.currentTimeMillis()}.json"
            val feedbackFile = File(feedbackDir, filename)
            val json = gson.toJson(feedback)
            feedbackFile.writeText(json)
            Log.d("ErrorReportManager", "Feedback saved: $filename")
            true
        } catch (e: Exception) {
            Log.e("ErrorReportManager", "Failed to save feedback", e)
            false
        }
    }

    fun collectDiagnostics(): String {
        return try {
            val diagnostics = mapOf(
                "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "android_version" to Build.VERSION.RELEASE,
                "api_level" to Build.VERSION.SDK_INT,
                "crash_log_exists" to crashLogFile.exists(),
                "feedback_count" to (feedbackDir.listFiles()?.size ?: 0),
                "crash_log_size_kb" to (crashLogFile.length() / 1024)
            )
            gson.toJson(diagnostics)
        } catch (e: Exception) {
            Log.e("ErrorReportManager", "Failed to collect diagnostics", e)
            "{}"
        }
    }

    fun getCrashLogContent(): String {
        return try {
            if (crashLogFile.exists()) {
                CrashLogger.getInstance(context).sanitize(crashLogFile.readText())
            } else {
                "No crash logs found."
            }
        } catch (e: Exception) {
            "Error reading crash logs: ${e.message}"
        }
    }

    fun getAllFeedbackAsText(): String {
        return try {
            val feedbacks = feedbackDir.listFiles()?.mapNotNull { file ->
                try {
                    val json = file.readText()
                    gson.fromJson(json, UserFeedback::class.java)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            if (feedbacks.isEmpty()) {
                return "No feedback submitted."
            }

            val sb = StringBuilder()
            feedbacks.forEachIndexed { index, feedback ->
                sb.append("FEEDBACK #${index + 1}\n")
                sb.append("Timestamp: ${feedback.timestamp}\n")
                sb.append("Category: ${feedback.category}\n")
                sb.append("Rating: ${feedback.rating}/5\n")
                sb.append("Message: ${CrashLogger.getInstance(context).sanitize(feedback.message)}\n")
                if (!feedback.email.isNullOrBlank()) {
                    sb.append("Email: [REDACTED_EMAIL]\n")
                }
                sb.append("─".repeat(50)).append("\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "Error reading feedback: ${e.message}"
        }
    }

    fun exportReportsAsText(): String {
        val appLogs = CrashLogger.getInstance(context).getRawLogContent()
        val crashLogs = getCrashLogContent()
        val feedback = getAllFeedbackAsText()
        val diagnostics = collectDiagnostics()

        return buildString {
            append("════════════════════════════════════════════════════════════════════════════════\n")
            append("AMNISHIELD DIAGNOSTIC LOG EXPORT (100% On-Device, Sanitized)\n")
            append("════════════════════════════════════════════════════════════════════════════════\n")
            append("Diagnostics:\n$diagnostics\n\n")
            append("════════════════════════════════════════════════════════════════════════════════\n")
            append("APPLICATION DIAGNOSTIC LOGS\n")
            append("════════════════════════════════════════════════════════════════════════════════\n")
            append(if (appLogs.isNotBlank()) appLogs else "No application log entries recorded.\n")
            append("\n\n════════════════════════════════════════════════════════════════════════════════\n")
            append("CRASH LOGS\n")
            append("════════════════════════════════════════════════════════════════════════════════\n")
            append(crashLogs)
            append("\n\n════════════════════════════════════════════════════════════════════════════════\n")
            append("FEEDBACK SUBMISSIONS\n")
            append("════════════════════════════════════════════════════════════════════════════════\n")
            append(feedback)
            append("\n")
        }
    }

    fun clearAllReports() {
        try {
            if (crashLogFile.exists()) crashLogFile.delete()
            feedbackDir.listFiles()?.forEach { it.delete() }
            CrashLogger.getInstance(context).clearLogs()
            Log.d("ErrorReportManager", "All reports and logs cleared")
        } catch (e: Exception) {
            Log.e("ErrorReportManager", "Failed to clear reports", e)
        }
    }

    fun createBundledReportFile(prefixText: String? = null): File? {
        return try {
            val reportDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val reportFile = File(reportDir, "amni_shield_diagnostics_${System.currentTimeMillis()}.txt")

            val content = buildString {
                if (!prefixText.isNullOrBlank()) {
                    append(prefixText.trim())
                    append("\n\n")
                }
                append(exportReportsAsText())
            }

            reportFile.writeText(content)
            reportFile
        } catch (e: Exception) {
            Log.e("ErrorReportManager", "Failed to create bundled report file", e)
            null
        }
    }

    companion object {
        @Volatile
        private var instance: ErrorReportManager? = null

        fun getInstance(context: Context): ErrorReportManager {
            return instance ?: synchronized(this) {
                instance ?: ErrorReportManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * Data class for user feedback
 */
data class UserFeedback(
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
    val category: String = "General",
    val message: String,
    val rating: Int = 3,
    val email: String? = null,
    val stackTrace: String? = null,
    val deviceInfo: String? = null
)
