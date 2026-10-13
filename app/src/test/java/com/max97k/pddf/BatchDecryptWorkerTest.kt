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
        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, kotlinx.coroutines.Dispatchers.IO)
        val decryptPdfUseCase = com.max97k.pddf.domain.usecase.DecryptPdfUseCase(pdfRepository, kotlinx.coroutines.Dispatchers.IO)
        val batchProcessUseCase = com.max97k.pddf.domain.usecase.BatchProcessUseCase(pdfRepository, decryptPdfUseCase, kotlinx.coroutines.Dispatchers.IO)
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
        assertEquals(1001, BatchDecryptWorker.NOTIFICATION_ID)
        assertNotNull(BatchDecryptWorker.KEY_INPUT_URIS)
        assertNotNull(BatchDecryptWorker.KEY_PASSWORD)
    }
}
