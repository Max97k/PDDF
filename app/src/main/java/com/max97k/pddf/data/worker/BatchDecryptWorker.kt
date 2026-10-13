package com.max97k.pddf.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.max97k.pddf.ConflictMode
import com.max97k.pddf.domain.usecase.BatchProcessUseCase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class BatchDecryptWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val batchProcessUseCase: BatchProcessUseCase
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

        setProgress(
            workDataOf(
                KEY_PROGRESS_CURRENT to 0,
                KEY_PROGRESS_TOTAL to uriStrings.size
            )
        )

        val progressCallback: (Int, Int) -> Unit = { current, total ->
            if (!isStopped) {
                try {
                    val notification = buildNotification(current, total)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } catch (_: Exception) {}
            }
        }

        val result: BatchProcessUseCase.BatchResult = if (outputDirectoryUriString != null) {
            batchProcessUseCase.processToDirectory(
                inputUriStrings = uriStrings.toList(),
                outputDirectoryUriString = outputDirectoryUriString,
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
                inputUriStrings = uriStrings.toList(),
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
