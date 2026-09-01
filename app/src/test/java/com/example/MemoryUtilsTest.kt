package com.example

import com.example.util.MemoryUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryUtilsTest {

    @Test
    fun testWipeCharArray() {
        val sensitiveChars = "SecretPassword123!".toCharArray()
        val expectedZeros = CharArray(sensitiveChars.size) { '\u0000' }

        MemoryUtils.wipe(sensitiveChars)

        assertArrayEquals(expectedZeros, sensitiveChars)
    }

    @Test
    fun testWipeByteArray() {
        val sensitiveBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val expectedZeros = ByteArray(10) { 0 }

        MemoryUtils.wipe(sensitiveBytes)

        assertArrayEquals(expectedZeros, sensitiveBytes)
    }

    @Test
    fun testWipeStringBuilder() {
        val sb = StringBuilder("ConfidentialData")
        assertEquals(16, sb.length)

        MemoryUtils.wipe(sb)

        assertEquals(0, sb.length)
    }
}
