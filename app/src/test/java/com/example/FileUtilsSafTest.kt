package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileUtilsSafTest {

    @Test
    fun takePersistableUriPermissionSafely_basicTake() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.example.provider/doc1.pdf")
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val result = FileUtils.takePersistableUriPermissionSafely(context, uri, flags)
        assertTrue(result)
    }

    @Test
    fun releasePersistableUriPermissionSafely_whenNotPersisted_returnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.example.provider/unpersisted.pdf")

        val released = FileUtils.releasePersistableUriPermissionSafely(context, uri)
        assertFalse(released)
    }

    @Test
    fun pruneStalePersistedUriPermissions_doesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activeUri = Uri.parse("content://com.example.provider/active.pdf")

        FileUtils.pruneStalePersistedUriPermissions(context, listOf(activeUri), maxRetained = 64)
        FileUtils.pruneStalePersistedUriPermissions(context, emptyList(), maxRetained = 0)
    }

    @Test
    fun takePersistableUriPermissionSafely_quotaPruningBehavior() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.example.provider/quota_test.pdf")
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        // With maxLimit = 0, it triggers the quota pruning branch before taking
        val result = FileUtils.takePersistableUriPermissionSafely(context, uri, flags, maxLimit = 0)
        assertTrue(result)
    }
}
