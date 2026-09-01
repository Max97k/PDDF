package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.data.worker.BatchDecryptWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatchDecryptWorkerTest {

    @Test
    fun testBatchDecryptWorker_emptyInputReturnsSuccess() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<BatchDecryptWorker>(context)
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
