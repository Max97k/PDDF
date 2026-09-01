package com.example.util

object MemoryUtils {
    fun wipe(chars: CharArray) {
        chars.fill('\u0000')
    }

    fun wipe(bytes: ByteArray) {
        bytes.fill(0)
    }

    fun wipe(builder: StringBuilder) {
        for (i in 0 until builder.length) {
            builder.setCharAt(i, '\u0000')
        }
        builder.setLength(0)
    }
}
