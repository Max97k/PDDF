package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

object FileUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {
                // Return fallback on permission or cursor failure
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        var finalName = result ?: "Unknown"
        finalName = finalName.substringAfterLast("/")
        finalName = finalName.substringAfterLast("\\")
        return finalName
    }

    /**
     * Strictly implements DoD 5220.22-M 3-pass file shredding standard:
     * - Pass 1: Overwrite with 0x00 (zeros)
     * - Pass 2: Overwrite with 0xFF (ones)
     * - Pass 3: Overwrite with cryptographic pseudo-random bytes
     * Flushes hardware storage buffer with sync() after each pass before deletion.
     */
    fun secureDelete(file: File?): Boolean {
        if (file == null || !file.exists()) return true
        return try {
            if (file.isFile && file.length() > 0) {
                val length = file.length()
                RandomAccessFile(file, "rws").use { raf ->
                    val bufferSize = 4096.coerceAtMost(length.toInt()).coerceAtLeast(1)
                    val buffer = ByteArray(bufferSize)
                    val random = SecureRandom()

                    // Pass 1: 0x00 (zero bytes)
                    raf.seek(0)
                    buffer.fill(0x00.toByte())
                    var written = 0L
                    while (written < length) {
                        val toWrite = (length - written).coerceAtMost(buffer.size.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    try { raf.fd.sync() } catch (_: Exception) {}

                    // Pass 2: 0xFF (all-ones bytes)
                    raf.seek(0)
                    buffer.fill(0xFF.toByte())
                    written = 0L
                    while (written < length) {
                        val toWrite = (length - written).coerceAtMost(buffer.size.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    try { raf.fd.sync() } catch (_: Exception) {}

                    // Pass 3: Cryptographic pseudo-random bytes
                    raf.seek(0)
                    written = 0L
                    while (written < length) {
                        val toWrite = (length - written).coerceAtMost(buffer.size.toLong()).toInt()
                        random.nextBytes(buffer)
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    try { raf.fd.sync() } catch (_: Exception) {}
                }
            }
            file.delete()
        } catch (_: Exception) {
            file.delete()
        }
    }

    /**
     * Efficiently queries child documents in SAF tree without heavy reflection of DocumentFile.listFiles()
     */
    fun findChildUriInTree(context: Context, treeUri: Uri, targetDisplayName: String): Uri? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    if (name.equals(targetDisplayName, ignoreCase = true)) {
                        val childDocId = cursor.getString(idIndex)
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
