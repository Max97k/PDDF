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
                var doc: PDDocument? = null
                try {
                    doc = PDDocument.load(
                        input.buffered(),
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    )
                } catch (e: Exception) {
                    if (e.message?.contains("xref", ignoreCase = true) == true) {
                        try {
                            openSafeInputStream(sourceUri)?.use { fallbackInput ->
                                doc = PDDocument.load(
                                    fallbackInput.buffered(),
                                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly()
                                )
                            }
                        } catch (_: Exception) {}
                    } else throw e
                }
                doc?.use {
                    sanitizePdf(it)
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
                var docWithoutPass: PDDocument? = null
                try {
                    docWithoutPass = PDDocument.load(
                        inputStream.buffered(),
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    )
                } catch (e: Exception) {
                    if (e.message?.contains("xref", ignoreCase = true) == true) {
                        try {
                            openSafeInputStream(sourceUri)?.use { fallbackInput ->
                                docWithoutPass = PDDocument.load(
                                    fallbackInput.buffered(),
                                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly()
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
                docWithoutPass?.use {
                    sanitizePdf(it)
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
                        sanitizePdf(document)
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
                var doc: PDDocument? = null
                try {
                    doc = PDDocument.load(
                        inputStream.buffered(),
                        passwordValue,
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    )
                } catch (e: Exception) {
                    if (e.message?.contains("xref", ignoreCase = true) == true) {
                        try {
                            openSafeInputStream(sourceUri)?.use { fallbackInput ->
                                doc = PDDocument.load(
                                    fallbackInput.buffered(),
                                    passwordValue,
                                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly()
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
                doc?.use {
                    sanitizePdf(it)
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

    private fun sanitizePdf(document: PDDocument) {
        val catalog = document.documentCatalog?.cosObject
        if (catalog != null) {
            catalog.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("JS"))
            catalog.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("JavaScript"))
            catalog.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("Launch"))
            catalog.removeItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("EmbeddedFiles"))
        }

        for (page in document.pages) {
            val annotations = page.annotations ?: continue
            for (annotation in annotations) {
                val cos = annotation.cosObject
                cos.removeItem(com.tom_roush.pdfbox.cos.COSName.A)
                cos.removeItem(com.tom_roush.pdfbox.cos.COSName.AA)
            }
        }

        // Defend against decompression bombs (Zip Bombs) by actively inspecting stream objects
        for (obj in document.document.objects) {
            if (obj is com.tom_roush.pdfbox.cos.COSStream) {
                val filters = obj.getFilters()
                val isFlate = filters != null && (filters.toString().contains("FlateDecode"))
                if (isFlate) {
                    val rawLength = obj.length.toLong()
                    val decodedLength = obj.getInt(com.tom_roush.pdfbox.cos.COSName.DL).toLong()

                    // Cap max uncompressed stream expansion factor (e.g. factor > 100 on small streams)
                    if (decodedLength > 0 && rawLength > 0 && (decodedLength / rawLength) > 100L) {
                        throw java.io.IOException("Stream exceeded maximum allowed decoded expansion factor (Zip Bomb Protection)")
                    }

                    // Cap absolute uncompressed stream size to 100MB
                    if (decodedLength > 100 * 1024 * 1024) {
                        throw java.io.IOException("Stream exceeded maximum allowed decoded size (Zip Bomb Protection)")
                    }
                }
            }
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
