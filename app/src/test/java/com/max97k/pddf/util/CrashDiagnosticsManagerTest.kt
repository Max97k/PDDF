package com.max97k.pddf.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashDiagnosticsManagerTest {

    @Test
    fun `sanitize redacts file paths and content URIs`() {
        val input = """
            Exception: file not found
            at path: /storage/emulated/0/Download/secret_doc.pdf
            caused by: content://com.android.providers.media.documents/document/document%3A123
        """.trimIndent()

        val expected = """
            Exception: file not found
            at path: [REDACTED]
            caused by: [REDACTED]
        """.trimIndent()

        val result = CrashDiagnosticsManager.sanitize(input)
        assertEquals(expected, result)
    }

    @Test
    fun `sanitize redacts passwords`() {
        val input = """
            java.lang.IllegalArgumentException: Invalid password: my_secret_pwd
            Another password=admin
            pwd: 12345
        """.trimIndent()

        val expected = """
            java.lang.IllegalArgumentException: Invalid password: [REDACTED]
            Another password=[REDACTED]
            pwd: [REDACTED]
        """.trimIndent()

        val result = CrashDiagnosticsManager.sanitize(input)
        assertEquals(expected, result)
    }

    @Test
    fun `sanitize handles normal stack traces without redactions`() {
        val input = """
            java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference
            at com.example.MyClass.doSomething(MyClass.java:12)
            at android.app.ActivityThread.main(ActivityThread.java:7356)
        """.trimIndent()

        val result = CrashDiagnosticsManager.sanitize(input)
        assertEquals(input, result)
    }
}
