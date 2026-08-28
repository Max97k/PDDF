package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceTest {

    @Test
    fun measureFileNameResolution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Note: in a real device with actual content providers, this will take significantly longer.
        // We're measuring the overhead of the method call.
        val uris = (1..100).map { Uri.parse("content://media/external/file/$it") }

        // Baseline synchronous
        val timeSync = measureTimeMillis {
            uris.forEach { getFileName(context, it) }
        }
        println("Synchronous resolution time for 100 URIs: $timeSync ms")

        // Async resolution using IO dispatcher
        val timeAsync = measureTimeMillis {
            runBlocking {
                withContext(Dispatchers.IO) {
                    uris.map { getFileName(context, it) }
                }
            }
        }
        println("Asynchronous resolution time for 100 URIs: $timeAsync ms")
    }
}
