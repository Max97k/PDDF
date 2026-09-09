package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Persists security-related user preferences in a dedicated SharedPreferences file.
 *
 * Unlike ThemePreferences (DataStore), this class deliberately uses SharedPreferences
 * so that MainActivity.onCreate() can perform a **synchronous** read before setContent{}
 * is called — required for FLAG_SECURE to be set before the first frame is rendered
 * and before onPause() captures a Recents thumbnail.
 */
class SecurityPreferences(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "security_prefs"
        const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"

        /**
         * Synchronous read for use in Activity.onCreate() before setContent{}.
         * Only call from the main thread during cold-start initialisation.
         */
        fun readScreenshotProtectionSync(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SCREENSHOT_PROTECTION, false)
    }

    // Hot StateFlow so ViewModel can combine() it with other streams.
    private val _screenshotProtection = MutableStateFlow(
        prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, false)
    )
    val screenshotProtection: StateFlow<Boolean> = _screenshotProtection.asStateFlow()

    suspend fun saveScreenshotProtection(enabled: Boolean) = withContext(ioDispatcher) {
        prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply()
        _screenshotProtection.value = enabled
    }
}
