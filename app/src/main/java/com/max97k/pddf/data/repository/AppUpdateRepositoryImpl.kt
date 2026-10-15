package com.max97k.pddf.data.repository

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.bytesDownloaded
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import com.google.android.play.core.ktx.totalBytesToDownload
import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.model.AppUpdateStatus
import com.max97k.pddf.domain.repository.AppUpdateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AppUpdateRepository {

    private val _updateStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Idle)
    override val updateStatus: StateFlow<AppUpdateStatus> = _updateStatus.asStateFlow()

    private var latestUpdateInfo: AppUpdateInfo? = null

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                _updateStatus.value = AppUpdateStatus.Downloading(
                    bytesDownloaded = state.bytesDownloaded(),
                    totalBytesToDownload = state.totalBytesToDownload()
                )
            }
            InstallStatus.DOWNLOADED -> {
                _updateStatus.value = AppUpdateStatus.Downloaded
            }
            InstallStatus.FAILED -> {
                _updateStatus.value = AppUpdateStatus.Failed("Download failed")
            }
            InstallStatus.CANCELED -> {
                _updateStatus.value = AppUpdateStatus.Idle
            }
            else -> {}
        }
    }

    init {
        try {
            appUpdateManager.registerListener(installStateListener)
        } catch (_: Exception) {}
    }

    override suspend fun checkForUpdate() = withContext(ioDispatcher) {
        _updateStatus.value = AppUpdateStatus.Checking
        try {
            val appUpdateInfo = appUpdateManager.requestAppUpdateInfo()
            latestUpdateInfo = appUpdateInfo

            when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                    val isFlexible = appUpdateInfo.isFlexibleUpdateAllowed
                    _updateStatus.value = AppUpdateStatus.Available(
                        availableVersionCode = appUpdateInfo.availableVersionCode(),
                        isFlexibleAllowed = isFlexible
                    )
                }
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    _updateStatus.value = AppUpdateStatus.Downloading(
                        bytesDownloaded = appUpdateInfo.bytesDownloaded,
                        totalBytesToDownload = appUpdateInfo.totalBytesToDownload
                    )
                }
                else -> {
                    if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                        _updateStatus.value = AppUpdateStatus.Downloaded
                    } else {
                        _updateStatus.value = AppUpdateStatus.UpToDate
                    }
                }
            }
        } catch (e: Exception) {
            _updateStatus.value = AppUpdateStatus.Failed(e.localizedMessage ?: "Unable to check for updates")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun startFlexibleUpdate(launcher: Any): Boolean {
        val actualLauncher = launcher as? ActivityResultLauncher<IntentSenderRequest> ?: return false
        val info = latestUpdateInfo ?: return false
        return try {
            val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            appUpdateManager.startUpdateFlowForResult(info, actualLauncher, options)
        } catch (e: Exception) {
            _updateStatus.value = AppUpdateStatus.Failed(e.localizedMessage)
            false
        }
    }

    override suspend fun completeUpdate() = withContext(ioDispatcher) {
        try {
            appUpdateManager.requestCompleteUpdate()
        } catch (e: Exception) {
            _updateStatus.value = AppUpdateStatus.Failed(e.localizedMessage)
        }
    }

    override fun resetStatus() {
        _updateStatus.value = AppUpdateStatus.Idle
    }
}
