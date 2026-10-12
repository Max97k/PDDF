package com.example

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class WriteModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testSolutionsForFileTruncation() {
        val originalContent = "START_OF_FILE_DATA_LONG_STRING_END_OF_FILE"
        val shorterContent = "SHORT_DATA"

        // =========================================================================
        // 解法 1：使用 "wt" 模式 (專案規範)
        // =========================================================================
        val file1 = File(context.filesDir, "solution_1_wt.txt").apply { writeText(originalContent) }
        val fd1 = context.contentResolver.openFileDescriptor(Uri.fromFile(file1), "wt")
        fd1?.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { fos ->
                fos.write(shorterContent.toByteArray())
            }
        }
        assertEquals("解法 1 ('wt' 模式) 應該成功截斷", shorterContent, file1.readText())

        // =========================================================================
        // 解法 2：使用 Os.ftruncate 手動將 FileDescriptor 截斷為 0 字節
        // =========================================================================
        val file2 = File(context.filesDir, "solution_2_ftruncate.txt").apply { writeText(originalContent) }
        val fd2 = context.contentResolver.openFileDescriptor(Uri.fromFile(file2), "w")
        fd2?.use { pfd ->
            // 手動截斷底層 FileDescriptor
            Os.ftruncate(pfd.fileDescriptor, 0)
            FileOutputStream(pfd.fileDescriptor).use { fos ->
                fos.write(shorterContent.toByteArray())
            }
        }
        assertEquals("解法 2 (Os.ftruncate 截斷) 應該成功截斷", shorterContent, file2.readText())

        // =========================================================================
        // 解法 3：寫入完成後，使用 Os.ftruncate 將檔案截斷至「實際寫入長度」
        // =========================================================================
        val file3 = File(context.filesDir, "solution_3_ftruncate_after.txt").apply { writeText(originalContent) }
        val fd3 = context.contentResolver.openFileDescriptor(Uri.fromFile(file3), "w")
        fd3?.use { pfd ->
            val bytes = shorterContent.toByteArray()
            val fos = FileOutputStream(pfd.fileDescriptor)
            fos.write(bytes)
            fos.flush()
            // 寫完後將長度裁切至實際位元組數
            Os.ftruncate(pfd.fileDescriptor, bytes.size.toLong())
        }
        assertEquals("解法 3 (寫入後截斷) 應該成功截斷", shorterContent, file3.readText())

        // =========================================================================
        // 解法 4：FileChannel.truncate(0) 模式
        // =========================================================================
        val file4 = File(context.filesDir, "solution_4_channel.txt").apply { writeText(originalContent) }
        val fd4 = context.contentResolver.openFileDescriptor(Uri.fromFile(file4), "rw")
        fd4?.use { pfd ->
            val fos = FileOutputStream(pfd.fileDescriptor)
            val channel = fos.channel
            channel.truncate(0) // 清空 Channel
            fos.write(shorterContent.toByteArray())
        }
        assertEquals("解法 4 (FileChannel.truncate) 應該成功截斷", shorterContent, file4.readText())
    }
}
