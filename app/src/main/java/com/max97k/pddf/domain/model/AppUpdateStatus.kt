package com.max97k.pddf.domain.model

import androidx.compose.runtime.Immutable

/**
 * Immutable state representation for the in-app update checking and installation lifecycle.
 */
@Immutable
sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data class Available(val availableVersionCode: Int, val isFlexibleAllowed: Boolean = true) : AppUpdateStatus
    data object UpToDate : AppUpdateStatus
    data class Downloading(val bytesDownloaded: Long, val totalBytesToDownload: Long) : AppUpdateStatus {
        val progressPercent: Int
            get() = if (totalBytesToDownload > 0) ((bytesDownloaded * 100) / totalBytesToDownload).toInt().coerceIn(0, 100) else 0
    }
    data object Downloaded : AppUpdateStatus
    data class Failed(val message: String? = null) : AppUpdateStatus
}
