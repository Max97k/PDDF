package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.ConflictMode
import com.example.R
import com.example.domain.usecase.BatchProcessUseCase
import com.example.domain.usecase.DecryptPdfUseCase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatchDecryptWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "BatchDecryptWorker"
        const val CHANNEL_ID = "batch_decrypt_channel"
        const val NOTIFICATION_ID = 1001

        const val KEY_INPUT_URIS = "input_uris"
        const val KEY_PASSWORD = "password"
        const val KEY_OUTPUT_DIRECTORY_URI = "output_directory_uri"
        const val KEY_CONFLICT_MODE = "conflict_mode"

        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_SUCCESS_COUNT = "success_count"
        const val KEY_FAIL_COUNT = "fail_count"
        const val KEY_NOT_ENCRYPTED_COUNT = "not_encrypted_count"
        const val KEY_WRONG_PASSWORD_COUNT = "wrong_password_count"
        const val KEY_UNSUPPORTED_COUNT = "unsupported_count"
        const val KEY_ERROR_COUNT = "error_count"
        const val KEY_CANCELLED_COUNT = "cancelled_count"
        const val KEY_LAST_DECRYPTED_URI = "last_decrypted_uri"
    }

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStrings = inputData.getStringArray(KEY_INPUT_URIS) ?: emptyArray()
        val passwordValue = inputData.getString(KEY_PASSWORD) ?: ""
        val outputDirectoryUriString = inputData.getString(KEY_OUTPUT_DIRECTORY_URI)
        val conflictModeName = inputData.getString(KEY_CONFLICT_MODE) ?: ConflictMode.SAVE_AS_COPY.name
        val conflictMode = try {
            ConflictMode.valueOf(conflictModeName)
        } catch (_: Exception) {
            ConflictMode.SAVE_AS_COPY
        }

        if (uriStrings.isEmpty()) {
            return@withContext Result.success()
        }

        createNotificationChannel()

        // Initialize PDFBox
        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Exception) {}

        // Initial foreground notification & progress
        try {
            setForeground(createForegroundInfo(0, uriStrings.size))
        } catch (_: Exception) {}

        setProgress(
            workDataOf(
                KEY_PROGRESS_CURRENT to 0,
                KEY_PROGRESS_TOTAL to uriStrings.size
            )
        )

        val inputUris = uriStrings.map { Uri.parse(it) }
        val decryptPdfUseCase = DecryptPdfUseCase(Dispatchers.IO)
        val batchProcessUseCase = BatchProcessUseCase(decryptPdfUseCase, Dispatchers.IO)

        val progressCallback: (Int, Int) -> Unit = { current, total ->
            if (!isStopped) {
                try {
                    val notification = buildNotification(current, total)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } catch (_: Exception) {}
            }
        }

        val result: BatchProcessUseCase.BatchResult = if (outputDirectoryUriString != null) {
            val outputDirUri = Uri.parse(outputDirectoryUriString)
            batchProcessUseCase.processToDirectory(
                context = applicationContext,
                inputUris = inputUris,
                outputDirectoryUri = outputDirUri,
                passwordValue = passwordValue,
                conflictMode = conflictMode,
                onProgress = { current, total ->
                    progressCallback(current, total)
                    setProgressAsync(
                        workDataOf(
                            KEY_PROGRESS_CURRENT to current,
                            KEY_PROGRESS_TOTAL to total
                        )
                    )
                }
            )
        } else {
            batchProcessUseCase.processInPlace(
                context = applicationContext,
                inputUris = inputUris,
                passwordValue = passwordValue,
                onProgress = { current, total ->
                    progressCallback(current, total)
                    setProgressAsync(
                        workDataOf(
                            KEY_PROGRESS_CURRENT to current,
                            KEY_PROGRESS_TOTAL to total
                        )
                    )
                }
            )
        }

        if (isStopped) {
            notificationManager.cancel(NOTIFICATION_ID)
            return@withContext Result.failure()
        }

        notificationManager.cancel(NOTIFICATION_ID)

        val outputData = workDataOf(
            KEY_SUCCESS_COUNT to result.successCount,
            KEY_FAIL_COUNT to (result.wrongPasswordCount + result.unsupportedCount + result.errorCount),
            KEY_NOT_ENCRYPTED_COUNT to result.notEncryptedCount,
            KEY_WRONG_PASSWORD_COUNT to result.wrongPasswordCount,
            KEY_UNSUPPORTED_COUNT to result.unsupportedCount,
            KEY_ERROR_COUNT to result.errorCount,
            KEY_CANCELLED_COUNT to result.cancelledCount,
            KEY_LAST_DECRYPTED_URI to result.lastDecryptedUri?.toString()
        )

        Result.success(outputData)
    }

    private fun createForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(current, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(current: Int, total: Int): android.app.Notification {
        val cancelPendingIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val progressText = if (total > 0) "$current / $total" else ""

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Decrypting PDFs")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(total, current, total == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PDF Batch Decryption",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for PDF decryption jobs"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
