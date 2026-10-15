package com.max97k.pddf.domain.repository

import com.max97k.pddf.domain.model.AppUpdateStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for Google Play In-App Updates operations.
 * Completely free of Android UI/Activity classes to protect Clean Architecture domain purity.
 */
interface AppUpdateRepository {
    val updateStatus: StateFlow<AppUpdateStatus>

    suspend fun checkForUpdate()

    suspend fun completeUpdate()

    fun resetStatus()

    fun startFlexibleUpdate(launcher: Any): Boolean
}
