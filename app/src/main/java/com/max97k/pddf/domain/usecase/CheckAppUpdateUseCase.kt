package com.max97k.pddf.domain.usecase

import com.max97k.pddf.domain.model.AppUpdateStatus
import com.max97k.pddf.domain.repository.AppUpdateRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Clean Architecture UseCase coordinating in-app update checks and completion.
 */
class CheckAppUpdateUseCase @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository
) {
    val updateStatus: StateFlow<AppUpdateStatus> = appUpdateRepository.updateStatus

    suspend fun checkForUpdate() {
        appUpdateRepository.checkForUpdate()
    }

    suspend fun completeUpdate() {
        appUpdateRepository.completeUpdate()
    }

    fun resetStatus() {
        appUpdateRepository.resetStatus()
    }

    fun startFlexibleUpdate(launcher: Any): Boolean {
        return appUpdateRepository.startFlexibleUpdate(launcher)
    }
}
