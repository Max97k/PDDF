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
     * Overwrites file contents with zeros/random bytes before deletion to prevent recovery of sensitive PDF data.
     */
    fun secureDelete(file: File?): Boolean {
        if (file == null || !file.exists()) return true
        return try {
            if (file.isFile && file.length() > 0) {
                val length = file.length()
                RandomAccessFile(file, "rws").use { raf ->
                    val buffer = ByteArray(4096.coerceAtMost(length.toInt()).coerceAtLeast(1))
                    SecureRandom().nextBytes(buffer)
                    var written = 0L
                    while (written < length) {
                        val toWrite = (length - written).coerceAtMost(buffer.size.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
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
