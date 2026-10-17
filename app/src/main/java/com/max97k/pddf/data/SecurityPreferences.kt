package com.max97k.pddf.data

import android.content.Context
import android.content.SharedPreferences
import com.max97k.pddf.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class NotificationPreference {
    SOUND, VIBRATION, SILENT
}

/**
 * Persists security-related user preferences in a dedicated SharedPreferences file.
 *
 * Unlike ThemePreferences (DataStore), this class deliberately uses SharedPreferences
 * so that MainActivity.onCreate() can perform a **synchronous** read before setContent{}
 * is called — required for FLAG_SECURE to be set before the first frame is rendered
 * and before onPause() captures a Recents thumbnail.
 */
enum class ReadingMode {
    LIGHT, DARK, SEPIA, E_INK
}

class SecurityPreferences(
    context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "security_prefs"
        const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"
        const val KEY_READING_MODE = "reading_mode"
        const val KEY_BATCH_NOTIFICATION_PREF = "batch_notification_pref"
        const val KEY_PRESERVE_IMAGES = "preserve_images"
        const val KEY_AUTO_LOCK_DURATION = "auto_lock_duration"

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

    private val _batchNotificationPreference = MutableStateFlow(
        try {
            NotificationPreference.valueOf(
                prefs.getString(KEY_BATCH_NOTIFICATION_PREF, NotificationPreference.SOUND.name) ?: NotificationPreference.SOUND.name
            )
        } catch (e: Exception) {
            NotificationPreference.SOUND
        }
    )
    val batchNotificationPreference: StateFlow<NotificationPreference> = _batchNotificationPreference.asStateFlow()

    private val _preserveEmbeddedImages = MutableStateFlow(
        prefs.getBoolean(KEY_PRESERVE_IMAGES, false)
    )
    val preserveEmbeddedImages: StateFlow<Boolean> = _preserveEmbeddedImages.asStateFlow()

    suspend fun savePreserveImages(enabled: Boolean) = withContext(ioDispatcher) {
        prefs.edit().putBoolean(KEY_PRESERVE_IMAGES, enabled).apply()
        _preserveEmbeddedImages.value = enabled
    }

    suspend fun saveScreenshotProtection(enabled: Boolean) = withContext(ioDispatcher) {
        prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply()
        _screenshotProtection.value = enabled
    }

    private val _readingMode = MutableStateFlow(
        run {
            val name = prefs.getString(KEY_READING_MODE, ReadingMode.LIGHT.name)
            try {
                ReadingMode.valueOf(name ?: ReadingMode.LIGHT.name)
            } catch (e: IllegalArgumentException) {
                ReadingMode.LIGHT
            }
        }
    )
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    suspend fun saveReadingMode(mode: ReadingMode) = withContext(ioDispatcher) {
        prefs.edit().putString(KEY_READING_MODE, mode.name).apply()
        _readingMode.value = mode
    }

    suspend fun saveBatchNotificationPreference(pref: NotificationPreference) = withContext(ioDispatcher) {
        prefs.edit().putString(KEY_BATCH_NOTIFICATION_PREF, pref.name).apply()
        _batchNotificationPreference.value = pref
    }

    private val _autoLockDuration = MutableStateFlow(
        prefs.getLong(KEY_AUTO_LOCK_DURATION, 60000L)
    )
    val autoLockDuration: StateFlow<Long> = _autoLockDuration.asStateFlow()

    suspend fun saveAutoLockDuration(duration: Long) = withContext(ioDispatcher) {
        prefs.edit().putLong(KEY_AUTO_LOCK_DURATION, duration).apply()
        _autoLockDuration.value = duration
    }
}
