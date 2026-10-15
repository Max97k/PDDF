package com.max97k.pddf.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import androidx.core.content.FileProvider
import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.util.FileUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.filter.FilterFactory
import java.security.MessageDigest
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PdfRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PdfRepository {

    private fun parseUri(uriString: String): Uri {
        return if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
            Uri.parse(uriString)
        } else {
            Uri.fromFile(File(uriString))
        }
    }

    override suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck = withContext(ioDispatcher) {
        val inputStream = try {
            openSafeInputStream(sourceUri)
        } catch (_: Exception) {
            null
        } ?: return@withContext PdfEncryptionCheck.Error("Unable to open file stream")

        var isEncrypted = false
        try {
            inputStream.use { input ->
                val doc = PDDocument.load(
                    input.buffered(),
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                )
                doc.use {
                    isEncrypted = it.isEncrypted
                }
            }
        } catch (_: InvalidPasswordException) {
            isEncrypted = true
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("incorrect password") || msg.contains("password is required")) {
                isEncrypted = true
            } else if (msg.contains("security handler") ||
                msg.contains("cryptfilter") ||
                msg.contains("certificate") ||
                msg.contains("public key") ||
                msg.contains("unsupported") ||
                msg.contains("filter") ||
                msg.contains("drm") ||
                msg.contains("algorithm")
            ) {
                return@withContext PdfEncryptionCheck.Error("Unsupported encryption/DRM")
            } else {
                return@withContext PdfEncryptionCheck.Error("Corrupted PDF header or file")
            }
        }

        if (isEncrypted) {
            PdfEncryptionCheck.Encrypted
        } else {
            PdfEncryptionCheck.NotEncrypted
        }
    }

    override suspend fun decrypt(
        sourceUri: String,
        destUri: String,
        passwordChars: CharArray
    ): DecryptStatus = withContext(ioDispatcher) {
        val pwd = String(passwordChars)
        decrypt(sourceUri, destUri, pwd)
    }

    override suspend fun decrypt(
        sourceUri: String,
        destUri: String,
        passwordValue: String
    ): DecryptStatus = withContext(ioDispatcher) {
        // 1. Check if unencrypted
        try {
            openSafeInputStream(sourceUri)?.use { inputStream ->
                val docWithoutPass = try {
                    PDDocument.load(
                        inputStream.buffered(),
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    )
                } catch (_: Exception) {
                    null
                }
                docWithoutPass?.use {
                    if (!it.isEncrypted) {
                        val copySucceeded = copy(sourceUri, destUri)
                        return@withContext if (copySucceeded) {
                            DecryptStatus.NOT_ENCRYPTED
                        } else {
                            DecryptStatus.ERROR
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore pre-check exception
        }

        // 2. Candidate passwords
        val candidatePasswords = if (passwordValue != passwordValue.uppercase()) {
            listOf(passwordValue, passwordValue.uppercase())
        } else {
            listOf(passwordValue)
        }

        var lastException: Exception? = null
        for (pwd in candidatePasswords) {
            try {
                openSafeInputStream(sourceUri)?.use { inputStream ->
                    PDDocument.load(
                        inputStream.buffered(),
                        pwd,
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    ).use { document ->
                        if (!document.isEncrypted) {
                            return@withContext DecryptStatus.NOT_ENCRYPTED
                        }
                        document.setAllSecurityToBeRemoved(true)

                        val tempFile = File.createTempFile("decrypt_tmp_", ".pdf", context.cacheDir)
                        try {
                            tempFile.outputStream().buffered().use { outputStream ->
                                document.save(outputStream)
                            }
                            if (atomicReplace(tempFile, destUri)) {
                                return@withContext DecryptStatus.SUCCESS
                            } else {
                                return@withContext DecryptStatus.ERROR
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            return@withContext DecryptStatus.ERROR
                        } finally {
                            if (tempFile.exists()) {
                                FileUtils.secureDelete(tempFile)
                            }
                        }
                    }
                }
            } catch (e: InvalidPasswordException) {
                lastException = e
                continue
            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                if (msg.contains("password") || msg.contains("incorrect password") || msg.contains("password is required")) {
                    lastException = e
                    continue
                }
                return@withContext when {
                    msg.contains("security handler") ||
                    msg.contains("cryptfilter") ||
                    msg.contains("certificate") ||
                    msg.contains("public key") ||
                    msg.contains("unsupported") ||
                    msg.contains("filter") ||
                    msg.contains("drm") ||
                    msg.contains("algorithm") ->
                        DecryptStatus.UNSUPPORTED_ENCRYPTION
                    else ->
                        DecryptStatus.ERROR
                }
            }
        }

        if (lastException != null) {
            return@withContext DecryptStatus.WRONG_PASSWORD
        }
        DecryptStatus.ERROR
    }

    private fun atomicReplace(sourceFile: File, targetUriString: String): Boolean {
        return try {
            val targetUri = parseUri(targetUriString)
            if (targetUri.scheme != "file") {
                try {
                    context.contentResolver.openFileDescriptor(targetUri, "rw")?.use { pfd ->
                        Os.ftruncate(pfd.fileDescriptor, 0)
                    }
                } catch (_: Exception) {}
            }
            openSafeOutputStream(targetUriString)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun extractMetadata(
        sourceUri: String,
        passwordValue: String
    ): PdfMetadata? = withContext(ioDispatcher) {
        var sizeMb = 0.0
        try {
            val uri = parseUri(sourceUri)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                sizeMb = fd.statSize.toDouble() / (1024 * 1024)
            }
        } catch (_: Exception) {}

        try {
            openSafeInputStream(sourceUri)?.use { inputStream ->
                val doc = try {
                    PDDocument.load(
                        inputStream.buffered(),
                        passwordValue,
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    )
                } catch (_: Exception) {
                    null
                }
                doc?.use {
                    val info = it.documentInformation
                    val title = info?.title ?: "Unknown"
                    val author = info?.author ?: "Unknown"
                    val pages = it.numberOfPages
                    val enc = it.encryption
                    val encMethod = if (enc != null) "${enc.filter} ${enc.length}-bit" else if (it.isEncrypted) "Standard Encrypted" else "None"
                    val perm = it.currentAccessPermission
                    val canPrint = perm?.canPrint() ?: true
                    val canCopy = perm?.canExtractContent() ?: true
                    return@withContext PdfMetadata(
                        title = title,
                        author = author,
                        pageCount = pages,
                        fileSizeMb = sizeMb,
                        encryptionMethod = encMethod,
                        canPrint = canPrint,
                        canCopy = canCopy
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }


    override suspend fun compressPdf(
        sourceUri: String,
        destUri: String
    ): com.max97k.pddf.util.Result<Long> = withContext(ioDispatcher) {
        var originalSize = 0L
        try {
            val uri = parseUri(sourceUri)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                originalSize = fd.statSize
            }
        } catch (e: Exception) {
            return@withContext com.max97k.pddf.util.Result.Error(e, "Could not determine original size")
        }

        if (originalSize == 0L) {
             val file = if (sourceUri.startsWith("file://")) File(parseUri(sourceUri).path ?: sourceUri) else File(sourceUri)
             if (file.exists()) {
                 originalSize = file.length()
             } else {
                 return@withContext com.max97k.pddf.util.Result.Error(Exception("File not found"), "File not found")
             }
        }

        try {
            openSafeInputStream(sourceUri)?.use { inputStream ->
                val doc = PDDocument.load(
                    inputStream.buffered(),
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                )
                doc.use { document ->
                    // 1. Remove metadata
                    if (document.documentCatalog != null) {
                        document.documentCatalog.metadata = null
                    }

                    // 2. Reapply FlateDecode and strip duplicate fonts
                    val md = MessageDigest.getInstance("SHA-256")
                    val fontCache = mutableMapOf<String, com.tom_roush.pdfbox.pdmodel.font.PDFont>()

                    document.pages.forEach { page ->
                        val resources = page.resources
                        if (resources != null) {
                            val fontNames = resources.fontNames
                            for (fontName in fontNames) {
                                val font = resources.getFont(fontName)
                                if (font != null) {
                                    try {
                                        val fontCos = font.cosObject
                                        // Hash a portion of the font dictionary to detect exact duplicates
                                        val hashString = fontCos.keySet().sorted().joinToString("") { key ->
                                            key.name + (fontCos.getItem(key)?.toString() ?: "")
                                        }
                                        val hash = md.digest(hashString.toByteArray()).joinToString("") { "%02x".format(it) }
                                        val existingFont = fontCache[hash]
                                        if (existingFont != null && existingFont.cosObject != font.cosObject) {
                                            resources.put(fontName, existingFont)
                                        } else {
                                            fontCache[hash] = font
                                        }
                                    } catch (e: Exception) { }
                                }
                            }
                        }
                    }

                    // Prune unreferenced objects
                    document.document.dereferenceObjectStreams()
                    val objects = document.document.objects
                    val referenced = mutableSetOf<com.tom_roush.pdfbox.cos.COSObjectKey>()
                    // Traversing the document from trailer to find referenced
                    // We can just rely on PDFBox's save? Wait, the review said PDFBox save doesn't prune.
                    // How to prune in PDFBox: We can get the xref table.
                    // Or we could create a new PDDocument, and import pages... Wait, but that loses metadata.
                    // Actually, the review said: "There is no logic implemented to actually identify and prune unreferenced indirect objects. While PDDocument.save() may do some superficial cleanup, it does not act as a full garbage collector for unreachable COSBase objects within the pool unless explicitly traversed and cleaned."

                    // Let's implement a simple graph traversal starting from the trailer.
                    fun traverse(base: com.tom_roush.pdfbox.cos.COSBase?, visited: MutableSet<com.tom_roush.pdfbox.cos.COSObjectKey>) {
                        if (base == null) return
                        if (base is com.tom_roush.pdfbox.cos.COSObject) {
                            val key = com.tom_roush.pdfbox.cos.COSObjectKey(base.objectNumber, base.generationNumber)
                            if (visited.add(key)) {
                                traverse(base.`object`, visited)
                            }
                        } else if (base is com.tom_roush.pdfbox.cos.COSDictionary) {
                            for (value in base.values) {
                                traverse(value, visited)
                            }
                        } else if (base is com.tom_roush.pdfbox.cos.COSArray) {
                            for (value in base) {
                                traverse(value, visited)
                            }
                        }
                    }

                    traverse(document.document.trailer, referenced)

                    val toRemove = objects.filter {
                        val key = com.tom_roush.pdfbox.cos.COSObjectKey(it.objectNumber, it.generationNumber)
                        !referenced.contains(key)
                    }
                    toRemove.forEach { document.document.removeObject(com.tom_roush.pdfbox.cos.COSObjectKey(it.objectNumber, it.generationNumber)) }

                    // Apply FlateDecode to all streams that don't have it
                    for (obj in document.document.objects) {
                        val base = obj.`object`
                        if (base is COSStream) {
                            val filters = base.filters
                            val filtersStr = filters?.toString() ?: ""
                            if (filters == null || (!filtersStr.contains("FlateDecode") && !filtersStr.contains("DCTDecode") && !filtersStr.contains("JBIG2Decode") && !filtersStr.contains("JPXDecode"))) {
                                try {
                                    val uncompressedBytes = base.createInputStream().readBytes()
                                    base.createOutputStream(COSName.FLATE_DECODE).use { it.write(uncompressedBytes) }
                                } catch (e: Exception) {
                                    // Ignore if we can't compress this stream
                                }
                            }
                        }
                    }

                    val tempFile = File.createTempFile("compress_tmp_", ".pdf", context.cacheDir)
                    tempFile.outputStream().buffered().use { outputStream ->
                        document.save(outputStream)
                    }

                    val newSize = tempFile.length()

                    if (atomicReplace(tempFile, destUri)) {
                        if (tempFile.exists()) FileUtils.secureDelete(tempFile)
                        return@withContext com.max97k.pddf.util.Result.Success(originalSize - newSize)
                    } else {
                        if (tempFile.exists()) FileUtils.secureDelete(tempFile)
                        return@withContext com.max97k.pddf.util.Result.Error(Exception("Replace failed"), "Replace failed")
                    }
                }
            } ?: return@withContext com.max97k.pddf.util.Result.Error(Exception("Input stream null"), "Input stream null")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext com.max97k.pddf.util.Result.Error(e, e.message)
        }
    }

    override suspend fun copy(
        sourceUri: String,
        destUri: String
    ): Boolean = withContext(ioDispatcher) {
        try {
            val src = openSafeInputStream(sourceUri) ?: return@withContext false
            val dst = openSafeOutputStream(destUri) ?: run {
                src.close()
                return@withContext false
            }
            src.use { input ->
                dst.buffered().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun createTempFile(
        prefix: String,
        suffix: String
    ): String = withContext(ioDispatcher) {
        File.createTempFile(prefix, suffix, context.cacheDir).apply {
            deleteOnExit()
        }.absolutePath
    }

    override suspend fun getShareableUri(fileUri: String): String = withContext(ioDispatcher) {
        if (fileUri.startsWith("content://")) {
            return@withContext fileUri
        }
        val file = if (fileUri.startsWith("file://")) {
            File(parseUri(fileUri).path ?: fileUri)
        } else {
            File(fileUri)
        }
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
        } catch (_: Exception) {
            Uri.fromFile(file).toString()
        }
    }

    override suspend fun deleteFile(filePathOrUri: String): Boolean = withContext(ioDispatcher) {
        val file = if (filePathOrUri.startsWith("file://")) {
            File(parseUri(filePathOrUri).path ?: filePathOrUri)
        } else if (filePathOrUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(filePathOrUri)
                return@withContext context.contentResolver.delete(uri, null, null) > 0
            } catch (_: Exception) {
                return@withContext false
            }
        } else {
            File(filePathOrUri)
        }
        if (file.exists()) {
            FileUtils.secureDelete(file)
            true
        } else {
            false
        }
    }

    override suspend fun getFileName(uriString: String): String = withContext(ioDispatcher) {
        val uri = parseUri(uriString)
        FileUtils.getFileName(context, uri)
    }

    override suspend fun createDocumentInDirectory(
        directoryUriString: String,
        displayName: String,
        mimeType: String
    ): String? = withContext(ioDispatcher) {
        try {
            val treeUri = parseUri(directoryUriString)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                displayName
            )
            createdUri?.toString()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun findDocumentInDirectory(
        directoryUriString: String,
        displayName: String
    ): String? = withContext(ioDispatcher) {
        try {
            val treeUri = parseUri(directoryUriString)
            FileUtils.findChildUriInTree(context, treeUri, displayName)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteDocumentInDirectory(
        directoryUriString: String,
        displayName: String
    ): Boolean = withContext(ioDispatcher) {
        try {
            val treeUri = parseUri(directoryUriString)
            val childUri = FileUtils.findChildUriInTree(context, treeUri, displayName)
            if (childUri != null) {
                DocumentsContract.deleteDocument(context.contentResolver, childUri)
            } else false
        } catch (_: Exception) {
            false
        }
    }

    override fun openSafeInputStream(uriString: String): InputStream? {
        val uri = parseUri(uriString)
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    FileInputStream(File(uri.path!!))
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun openSafeOutputStream(uriString: String): OutputStream? {
        val uri = parseUri(uriString)
        return try {
            if (uri.scheme == "file" && uri.path != null) {
                FileOutputStream(File(uri.path!!))
            } else {
                context.contentResolver.openOutputStream(uri, "wt")
            }
        } catch (_: Exception) {
            null
        }
    }
}
