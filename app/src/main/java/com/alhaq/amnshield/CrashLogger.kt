/**
 * ============================================================================
 * AmniShield Diagnostic Engine - CrashLogger
 * ============================================================================
 * Architecture: Local-First, Privacy-Preserving Diagnostic & Crash Logging Engine
 * Execution Context: Thread.UncaughtExceptionHandler & Global App Logging
 * 
 * Invariants & AI/Developer Guidance:
 * - ZERO External Telemetry: Logs remain strictly 100% on-device in app storage.
 * - PII Sanitization: All credentials, tokens, emails, and PINs are stripped before disk write.
 * - Non-blocking: Logging failures must NEVER crash the host process.
 * ============================================================================
 */
package com.alhaq.amnshield

import android.content.Context
import android.os.Build
import android.util.Log
import com.alhaq.amnshield.utils.ErrorReportManager
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Severity levels for local diagnostics
 */
enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    FATAL
}

/**
 * Structured diagnostic log entry for UI rendering and filtering
 */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

/**
 * Robust, privacy-first local diagnostic logging system for AmniShield.
 * Handles fatal uncaught exceptions and application lifecycle logging with rolling file rotation.
 */
class CrashLogger(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val logDir = File(context.filesDir, "logs").apply { mkdirs() }
    private val activeLogFile = File(logDir, "app_logs.txt")
    private val lock = Any()

    companion object {
        private const val TAG = "CrashLogger"
        private const val MAX_LOG_FILE_SIZE_BYTES = 500 * 1024L // 500 KB per file
        private const val MAX_BACKUP_FILES = 4 // Keep up to app_logs.txt + 4 backups = 5 files max (~2 MB total)

        // PII and Security Sanitization Regex Patterns
        private val PASSWORD_PATTERN = Pattern.compile("(?i)(password|pin|pass|secret|token|license_key|auth_token|salt|jwt)[\"':\\s=]+([^\\s,;}\\]\"]+)")
        private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
        private val BEARER_TOKEN_PATTERN = Pattern.compile("(?i)(bearer\\s+|token[=:]|authorization:\\s*)([a-zA-Z0-9_\\-\\.]{15,})")
        private val JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_\\-]{10,}\\.[a-zA-Z0-9_\\-]{10,}\\.[a-zA-Z0-9_\\-]{10,}")
        private val SUPABASE_KEY_PATTERN = Pattern.compile("sbp_[a-zA-Z0-9]+")

        @Volatile
        private var instance: CrashLogger? = null

        fun getInstance(context: Context): CrashLogger {
            return instance ?: synchronized(this) {
                instance ?: CrashLogger(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Global uncaught exception installer during Application.onCreate()
         */
        fun install(context: Context) {
            val logger = getInstance(context)
            Thread.setDefaultUncaughtExceptionHandler(logger)
            logger.info("AmniShieldApp", "Global CrashLogger installed successfully.")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            logFatalCrash(thread, throwable)
            Log.e(TAG, "Uncaught fatal exception logged to ${activeLogFile.absolutePath}", throwable)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to log uncaught crash", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Sanitizes strings by stripping credentials, tokens, passwords, and emails.
     */
    fun sanitize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var sanitized = input

        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]")
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED_TOKEN]")
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("[REDACTED_JWT]")
        sanitized = SUPABASE_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_KEY]")
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[REDACTED_EMAIL]")

        return sanitized
    }

    /**
     * Log a general informational message
     */
    fun info(tag: String, message: String) {
        writeLog(LogLevel.INFO, tag, message, null)
    }

    /**
     * Log a warning message
     */
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        writeLog(LogLevel.WARN, tag, message, throwable)
    }

    /**
     * Log a non-fatal error
     */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        writeLog(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * Log a fatal error
     */
    fun fatal(tag: String, message: String, throwable: Throwable? = null) {
        writeLog(LogLevel.FATAL, tag, message, throwable)
    }

    /**
     * Legacy compatibility bridge for ErrorReportManager
     */
    fun logNonFatalError(tag: String, message: String, exception: Exception? = null) {
        error(tag, message, exception)
        try {
            ErrorReportManager.getInstance(context).logNonFatalError(tag, message, exception)
        } catch (_: Exception) {}
    }

    private fun logFatalCrash(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sanitizedStack = sanitize(throwable.stackTraceToString())
        val sanitizedMsg = sanitize(throwable.message ?: "No error message")

        val sb = StringBuilder()
        sb.append("\n").append("═".repeat(80)).append("\n")
        sb.append("[$timestamp] [FATAL] [CRASH] Thread: ${thread.name} (ID: ${thread.id})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
        sb.append("App: ${context.packageName}\n")
        sb.append("Exception: ${throwable.javaClass.name}: $sanitizedMsg\n")
        sb.append("Stacktrace:\n$sanitizedStack\n")
        sb.append("═".repeat(80)).append("\n")

        synchronized(lock) {
            rotateLogsIfNeeded()
            activeLogFile.appendText(sb.toString())
        }

        try {
            ErrorReportManager.getInstance(context).logCrash(thread, throwable)
        } catch (_: Exception) {}
    }

    private fun writeLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sanitizedMessage = sanitize(message)
        val sanitizedTag = sanitize(tag)

        val logLine = StringBuilder()
        logLine.append("[$timestamp] [${level.name}] [$sanitizedTag] $sanitizedMessage\n")

        if (throwable != null) {
            val sanitizedStack = sanitize(throwable.stackTraceToString())
            logLine.append("Stacktrace:\n$sanitizedStack\n")
        }

        synchronized(lock) {
            try {
                rotateLogsIfNeeded()
                activeLogFile.appendText(logLine.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error appending to log file", e)
            }
        }

        // Output to Logcat for development
        when (level) {
            LogLevel.INFO -> Log.i(sanitizedTag, sanitizedMessage)
            LogLevel.WARN -> Log.w(sanitizedTag, sanitizedMessage, throwable)
            LogLevel.ERROR -> Log.e(sanitizedTag, sanitizedMessage, throwable)
            LogLevel.FATAL -> Log.wtf(sanitizedTag, sanitizedMessage, throwable)
        }
    }

    /**
     * Rotates files if the current log exceeds [MAX_LOG_FILE_SIZE_BYTES].
     */
    private fun rotateLogsIfNeeded() {
        if (!activeLogFile.exists() || activeLogFile.length() < MAX_LOG_FILE_SIZE_BYTES) {
            return
        }

        // Delete oldest backup file if exists
        val oldestFile = File(logDir, "app_logs.$MAX_BACKUP_FILES.txt")
        if (oldestFile.exists()) {
            oldestFile.delete()
        }

        // Shift existing backup files
        for (i in (MAX_BACKUP_FILES - 1) downTo 1) {
            val current = File(logDir, "app_logs.$i.txt")
            if (current.exists()) {
                val next = File(logDir, "app_logs.${i + 1}.txt")
                current.renameTo(next)
            }
        }

        // Move active log file to app_logs.1.txt
        val firstBackup = File(logDir, "app_logs.1.txt")
        activeLogFile.renameTo(firstBackup)
    }

    /**
     * Reads all local log files and returns structured entries sorted chronologically (newest first).
     */
    fun getParsedLogs(): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        val allLogsText = getRawLogContent()
        if (allLogsText.isBlank()) return emptyList()

        val lines = allLogsText.lines()
        var currentEntry: LogEntry? = null
        val stackTraceBuffer = StringBuilder()

        for (line in lines) {
            if (line.startsWith("[") && line.contains("] [")) {
                // Flush previous entry if any
                currentEntry?.let { entry ->
                    entries.add(
                        if (stackTraceBuffer.isNotBlank()) {
                            entry.copy(stackTrace = stackTraceBuffer.toString().trim())
                        } else entry
                    )
                }
                stackTraceBuffer.clear()

                // Parse new entry: [timestamp] [LEVEL] [TAG] message
                try {
                    val parts = line.split("] [", limit = 3)
                    if (parts.size >= 3) {
                        val timestamp = parts[0].removePrefix("[").trim()
                        val levelStr = parts[1].trim()
                        val level = when (levelStr.uppercase(Locale.US)) {
                            "INFO" -> LogLevel.INFO
                            "WARN" -> LogLevel.WARN
                            "ERROR" -> LogLevel.ERROR
                            "FATAL" -> LogLevel.FATAL
                            else -> LogLevel.INFO
                        }
                        val rest = parts[2]
                        val tag = rest.substringBefore("]").trim()
                        val message = rest.substringAfter("]").trim()

                        currentEntry = LogEntry(timestamp, level, tag, message)
                    } else {
                        stackTraceBuffer.append(line).append("\n")
                    }
                } catch (e: Exception) {
                    stackTraceBuffer.append(line).append("\n")
                }
            } else if (line.isNotBlank() && !line.startsWith("═")) {
                stackTraceBuffer.append(line).append("\n")
            }
        }

        // Flush last entry
        currentEntry?.let { entry ->
            entries.add(
                if (stackTraceBuffer.isNotBlank()) {
                    entry.copy(stackTrace = stackTraceBuffer.toString().trim())
                } else entry
            )
        }

        return entries.reversed() // Newest first
    }

    /**
     * Returns combined raw content across all rotated log files.
     */
    fun getRawLogContent(): String {
        synchronized(lock) {
            val sb = StringBuilder()
            val logFiles = mutableListOf<File>()

            // Add backups in reverse order
            for (i in MAX_BACKUP_FILES downTo 1) {
                val backup = File(logDir, "app_logs.$i.txt")
                if (backup.exists()) {
                    logFiles.add(backup)
                }
            }
            if (activeLogFile.exists()) {
                logFiles.add(activeLogFile)
            }

            for (file in logFiles) {
                try {
                    sb.append(file.readText())
                } catch (e: Exception) {
                    sb.append("\n[Error reading ${file.name}: ${e.message}]\n")
                }
            }

            return sb.toString()
        }
    }

    /**
     * Returns a bundled, sanitized export file for sharing via FileProvider.
     */
    fun getExportLogFile(): File {
        synchronized(lock) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val exportFile = File(exportDir, "amni_shield_logs.txt")
            val content = getRawLogContent()
            exportFile.writeText(
                if (content.isNotBlank()) sanitize(content) else "AmniShield Diagnostic Logs\nNo entries recorded.\n"
            )
            return exportFile
        }
    }

    /**
     * Clears all local log files.
     */
    fun clearLogs() {
        synchronized(lock) {
            try {
                activeLogFile.delete()
                for (i in 1..MAX_BACKUP_FILES) {
                    File(logDir, "app_logs.$i.txt").delete()
                }
                info("CrashLogger", "All diagnostic logs cleared by user.")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing logs", e)
            }
        }
    }

    /**
     * Get total size of all log files in KB.
     */
    fun getTotalLogSizeBytes(): Long {
        synchronized(lock) {
            var size = 0L
            if (activeLogFile.exists()) size += activeLogFile.length()
            for (i in 1..MAX_BACKUP_FILES) {
                val backup = File(logDir, "app_logs.$i.txt")
                if (backup.exists()) size += backup.length()
            }
            return size
        }
    }
}
