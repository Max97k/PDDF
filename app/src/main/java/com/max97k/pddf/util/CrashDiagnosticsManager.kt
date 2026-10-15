package com.max97k.pddf.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashDiagnosticsManager : Thread.UncaughtExceptionHandler {
    private const val MAX_RECORDS = 5
    private const val LOG_FILE_NAME = "crash_logs.txt"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var logFile: File? = null

    fun initialize(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun getLogs(): String {
        return try {
            if (logFile?.exists() == true) {
                logFile?.readText() ?: ""
            } else {
                "No crash logs found."
            }
        } catch (e: Exception) {
            "Error reading crash logs."
        }
    }

    fun clearLogs() {
        try {
            logFile?.delete()
        } catch (e: Exception) {
            Log.e("CrashDiagnostics", "Failed to clear logs", e)
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        saveLog(e)
        defaultHandler?.uncaughtException(t, e)
    }

    private fun saveLog(e: Throwable) {
        try {
            val file = logFile ?: return

            val stackTrace = e.stackTraceToString()
            val sanitizedTrace = sanitize(stackTrace)

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val logEntry = "--- Crash at $timestamp ---\n$sanitizedTrace\n\n"

            val existingLogs = if (file.exists()) {
                file.readText().split("--- Crash at ").filter { it.isNotBlank() }.map { "--- Crash at $it" }.toMutableList()
            } else {
                mutableListOf()
            }

            existingLogs.add(0, logEntry)

            if (existingLogs.size > MAX_RECORDS) {
                existingLogs.subList(MAX_RECORDS, existingLogs.size).clear()
            }

            file.writeText(existingLogs.joinToString(""))
        } catch (ex: Exception) {
            Log.e("CrashDiagnostics", "Failed to save crash log", ex)
        }
    }

    fun sanitize(log: String): String {
        var sanitized = log
        // Redact URIs
        sanitized = sanitized.replace(Regex("(file|content)://[^\\s\"']+"), "[REDACTED]")
        // Redact UNIX-like file paths
        sanitized = sanitized.replace(Regex("(/[a-zA-Z0-9_.-]+)+/[a-zA-Z0-9_.-]+"), "[REDACTED]")
        // Redact passwords
        sanitized = sanitized.replace(Regex("(?i)(password|pwd|pass)([\\s=:]+)[^\\s\"',]+"), "$1$2[REDACTED]")
        return sanitized
    }
}
