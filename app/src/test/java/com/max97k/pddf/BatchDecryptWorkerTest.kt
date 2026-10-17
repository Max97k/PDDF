package com.max97k.pddf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.max97k.pddf.data.worker.BatchDecryptWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatchDecryptWorkerTest {

    @Test
    fun testBatchDecryptWorker_emptyInputReturnsSuccess() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, kotlinx.coroutines.Dispatchers.IO, com.max97k.pddf.domain.usecase.AnalyzePdfFontsUseCase())
        val decryptPdfUseCase = com.max97k.pddf.domain.usecase.DecryptPdfUseCase(pdfRepository, kotlinx.coroutines.Dispatchers.IO)
        val passwordRepository = com.max97k.pddf.data.PasswordRepository(androidx.room.Room.inMemoryDatabaseBuilder(context, com.max97k.pddf.data.AppDatabase::class.java).allowMainThreadQueries().build().passwordDao(), com.max97k.pddf.util.CryptoManager(context), kotlinx.coroutines.Dispatchers.IO)
        val batchProcessUseCase = com.max97k.pddf.domain.usecase.BatchProcessUseCase(pdfRepository, decryptPdfUseCase, passwordRepository, kotlinx.coroutines.Dispatchers.IO)
        val worker = TestListenableWorkerBuilder<BatchDecryptWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker {
                    return BatchDecryptWorker(appContext, workerParameters, batchProcessUseCase)
                }
            })
            .setInputData(workDataOf(BatchDecryptWorker.KEY_INPUT_URIS to emptyArray<String>()))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testBatchDecryptWorker_constants() {
        assertEquals("BatchDecryptWorker", BatchDecryptWorker.TAG)
        assertEquals("batch_decrypt_channel", BatchDecryptWorker.CHANNEL_ID)
        assertEquals("batch_decrypt_completed_sound", BatchDecryptWorker.CHANNEL_COMPLETED_SOUND)
        assertEquals("batch_decrypt_completed_vibe", BatchDecryptWorker.CHANNEL_COMPLETED_VIBE)
        assertEquals("batch_decrypt_completed_silent", BatchDecryptWorker.CHANNEL_COMPLETED_SILENT)
        assertEquals(1001, BatchDecryptWorker.NOTIFICATION_ID)
        assertEquals(1002, BatchDecryptWorker.NOTIFICATION_ID_COMPLETED)
        assertNotNull(BatchDecryptWorker.KEY_INPUT_URIS)
        assertNotNull(BatchDecryptWorker.KEY_PASSWORD)
    }

    @Test
    fun testNotificationChannelsConfigured() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, kotlinx.coroutines.Dispatchers.IO)
        val decryptPdfUseCase = com.max97k.pddf.domain.usecase.DecryptPdfUseCase(pdfRepository, kotlinx.coroutines.Dispatchers.IO)
        val passwordRepository = com.max97k.pddf.data.PasswordRepository(androidx.room.Room.inMemoryDatabaseBuilder(context, com.max97k.pddf.data.AppDatabase::class.java).allowMainThreadQueries().build().passwordDao(), com.max97k.pddf.util.CryptoManager(context), kotlinx.coroutines.Dispatchers.IO)
        val batchProcessUseCase = com.max97k.pddf.domain.usecase.BatchProcessUseCase(pdfRepository, decryptPdfUseCase, passwordRepository, kotlinx.coroutines.Dispatchers.IO)
        val worker = TestListenableWorkerBuilder<BatchDecryptWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker {
                    return BatchDecryptWorker(appContext, workerParameters, batchProcessUseCase)
                }
            })
            .setInputData(workDataOf(BatchDecryptWorker.KEY_INPUT_URIS to arrayOf("content://dummy")))
            .build()

        // Trigger doWork which initializes channels
        worker.doWork()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val soundChannel = notificationManager.getNotificationChannel(BatchDecryptWorker.CHANNEL_COMPLETED_SOUND)
        assertNotNull(soundChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, soundChannel.importance)

        val vibeChannel = notificationManager.getNotificationChannel(BatchDecryptWorker.CHANNEL_COMPLETED_VIBE)
        assertNotNull(vibeChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, vibeChannel.importance)
        assertEquals(true, vibeChannel.shouldVibrate())
        assertEquals(null, vibeChannel.sound)

        val silentChannel = notificationManager.getNotificationChannel(BatchDecryptWorker.CHANNEL_COMPLETED_SILENT)
        assertNotNull(silentChannel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, silentChannel.importance)
        assertEquals(false, silentChannel.shouldVibrate())
        assertEquals(null, silentChannel.sound)
    }
}
