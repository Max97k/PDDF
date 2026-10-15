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
import com.max97k.pddf.util.MemoryUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.max97k.pddf.SignatureInfo
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.filter.FilterFactory
import java.security.MessageDigest
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.util.Selector
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        val fileSizeBytes = FileUtils.getFileSize(context, parseUri(sourceUri))
        try {
            inputStream.use { input ->
                var doc: PDDocument? = null
                try {
                    doc = PDDocument.load(
                        input.buffered(),
                        MemoryUtils.getOptimalMemorySetting(fileSizeBytes)
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
        val fileSizeBytes = FileUtils.getFileSize(context, parseUri(sourceUri))
        try {
            openSafeInputStream(sourceUri)?.use { inputStream ->
                var docWithoutPass: PDDocument? = null
                try {
                    docWithoutPass = PDDocument.load(
                        inputStream.buffered(),
                        MemoryUtils.getOptimalMemorySetting(fileSizeBytes)
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
                        MemoryUtils.getOptimalMemorySetting(fileSizeBytes)
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


    override suspend fun exportPageAsImage(
        sourceUri: String,
        pageIndex: Int,
        outputUri: String,
        format: android.graphics.Bitmap.CompressFormat,
        quality: Int
    ): Boolean = withContext(ioDispatcher) {
        try {
            var success = false
            openSafeInputStream(sourceUri)?.use { inputStream ->
                val document = PDDocument.load(
                    inputStream.buffered(),
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                )
                document.use { doc ->
                    if (pageIndex in 0 until doc.numberOfPages) {
                        val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
                        val bitmap = renderer.renderImageWithDPI(pageIndex, 300f, com.tom_roush.pdfbox.rendering.ImageType.RGB)

                        openSafeOutputStream(outputUri)?.use { outputStream ->
                            success = bitmap.compress(format, quality, outputStream)
                        }
                        bitmap.recycle()
                    }
                }
            }
            success
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

        val fileSizeBytes = FileUtils.getFileSize(context, parseUri(sourceUri))
        try {
            openSafeInputStream(sourceUri)?.use { inputStream ->
                val fileBytes = inputStream.readBytes()
                var doc: PDDocument? = null
                try {
                    doc = PDDocument.load(
                        ByteArrayInputStream(fileBytes),
                        passwordValue,
                        MemoryUtils.getOptimalMemorySetting(fileSizeBytes)
                    )
                } catch (e: Exception) {
                    if (e.message?.contains("xref", ignoreCase = true) == true) {
                        try {
                            doc = PDDocument.load(
                                ByteArrayInputStream(fileBytes),
                                passwordValue,
                                com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly()
                            )
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

                    val signatures = mutableListOf<SignatureInfo>()
                    try {
                        val sigDicts = it.signatureDictionaries
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        for (sig in sigDicts) {
                            val name = sig.name ?: "Unknown"
                            val reason = sig.reason ?: "Unknown"
                            val location = sig.location ?: "Unknown"
                            val dateStr = sig.signDate?.let { date -> sdf.format(date.time) } ?: "Unknown"

                            var isValid = false
                            var certSubject = "Unknown"
                            var certIssuer = "Unknown"

                            try {
                                val contents = sig.contents
                                val signedContent = sig.getSignedContent(fileBytes)

                                if (contents != null && signedContent != null) {
                                    val cms = CMSSignedData(CMSProcessableByteArray(signedContent), contents)
                                    val signers = cms.signerInfos.signers
                                    val certificates = cms.certificates

                                    for (signer in signers) {
                                        @Suppress("UNCHECKED_CAST")
                                        val selector = signer.sid as? Selector<X509CertificateHolder>
                                        val certCollection = if (selector != null) certificates.getMatches(selector) else certificates.getMatches(null)

                                        for (certHolder in certCollection) {
                                            val holder = certHolder as? X509CertificateHolder ?: continue
                                            val certFactory = CertificateFactory.getInstance("X.509")
                                            val cert = certFactory.generateCertificate(ByteArrayInputStream(holder.encoded)) as X509Certificate

                                            certSubject = cert.subjectDN?.name ?: "Unknown"
                                            certIssuer = cert.issuerDN?.name ?: "Unknown"

                                            val verifier = JcaSimpleSignerInfoVerifierBuilder().build(cert)
                                            if (signer.verify(verifier)) {
                                                isValid = true
                                                break
                                            }
                                        }
                                        if (isValid) break
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            signatures.add(
                                SignatureInfo(
                                    signerName = name,
                                    signingDate = dateStr,
                                    reason = reason,
                                    location = location,
                                    isValid = isValid,
                                    certificateSubject = certSubject,
                                    certificateIssuer = certIssuer
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val attachments = mutableListOf<com.max97k.pddf.domain.usecase.PdfAttachment>()
                    try {
                        val names = it.documentCatalog.names
                        val embeddedFiles = names?.embeddedFiles
                        if (embeddedFiles != null) {
                            val namesMap = mutableMapOf<String, Any>()
                            fun extractNames(treeNode: com.tom_roush.pdfbox.pdmodel.common.PDNameTreeNode<*>) {
                                if (treeNode.names != null) {
                                    namesMap.putAll(treeNode.names)
                                }
                                if (treeNode.kids != null) {
                                    for (kid in treeNode.kids) {
                                        extractNames(kid)
                                    }
                                }
                            }
                            extractNames(embeddedFiles)
                            for ((name, fs) in namesMap) {
                                if (fs is com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification) {
                                    val ef = fs.embeddedFile
                                    if (ef != null) {
                                        val size = ef.size.toLong()
                                        val subtype = ef.subtype ?: "application/octet-stream"
                                        attachments.add(com.max97k.pddf.domain.usecase.PdfAttachment(fs.filename ?: name, size, subtype))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore extraction errors for metadata
                    }

                    return@withContext PdfMetadata(
                        title = title,
                        author = author,
                        pageCount = pages,
                        fileSizeMb = sizeMb,
                        encryptionMethod = encMethod,
                        canPrint = canPrint,
                        canCopy = canCopy,
                        signatures = signatures,
                        attachments = attachments
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

    override suspend fun addWatermark(
        sourceUri: String,
        destUri: String,
        text: String,
        opacity: Float,
        fontSize: Float
    ): Boolean = withContext(ioDispatcher) {
        try {
            openSafeInputStream(sourceUri)?.use { inputStream ->
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(
                    inputStream.buffered(),
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                ).use { document ->
                    for (page in document.pages) {
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                            document, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { contentStream ->
                            val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
                            contentStream.setFont(font, fontSize)

                            val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                            gs.nonStrokingAlphaConstant = opacity
                            contentStream.setGraphicsStateParameters(gs)

                            contentStream.setNonStrokingColor(0.5f, 0.5f, 0.5f) // GRAY

                            val mediaBox = page.mediaBox
                            val cx = mediaBox.width / 2f
                            val cy = mediaBox.height / 2f

                            val stringWidth = font.getStringWidth(text) / 1000f * fontSize
                            val startX = cx - (stringWidth / 2f)
                            val startY = cy

                            contentStream.transform(
                                com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(45.0), cx, cy)
                            )

                            contentStream.beginText()
                            contentStream.newLineAtOffset(startX - cx, startY - cy)
                            contentStream.showText(text)
                            contentStream.endText()
                        }
                    }
                    openSafeOutputStream(destUri)?.use { outputStream ->
                        document.save(outputStream)
                        return@withContext true
                    } ?: return@withContext false
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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

    override suspend fun extractPages(
        sourceUri: String,
        destUri: String,
        pageRange: String
    ): kotlin.Result<Int> = withContext(ioDispatcher) {
        try {
            val srcStream = openSafeInputStream(sourceUri)
                ?: return@withContext kotlin.Result.failure(Exception("Cannot open source file"))
            val dstStream = openSafeOutputStream(destUri)
                ?: run {
                    srcStream.close()
                    return@withContext kotlin.Result.failure(Exception("Cannot open destination file"))
                }

            srcStream.use { input ->
                PDDocument.load(
                    input.buffered(),
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                ).use { sourceDoc ->
                    val totalPages = sourceDoc.numberOfPages
                    val pagesToExtract = com.max97k.pddf.util.PageRangeParser.parse(pageRange, totalPages)

                    if (pagesToExtract.isEmpty()) {
                        dstStream.close()
                        return@withContext kotlin.Result.failure(IllegalArgumentException("Invalid or empty page range"))
                    }

                    PDDocument().use { destDoc ->
                        for (pageNum in pagesToExtract) {
                            val page = sourceDoc.getPage(pageNum - 1)
                            destDoc.importPage(page)
                        }

                        dstStream.buffered().use { output ->
                            destDoc.save(output)
                        }
                    }
                    kotlin.Result.success(pagesToExtract.size)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kotlin.Result.failure(e)
        }
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



    override suspend fun rotatePages(
        sourceUri: String,
        destUri: String,
        pageIndices: List<Int>?,
        rotationDegrees: Int
    ): Boolean = withContext(ioDispatcher) {
        try {
            var success = false
            openSafeInputStream(sourceUri)?.use { inputStream ->
                val document = PDDocument.load(
                    inputStream.buffered(),
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                )
                document.use { doc ->
                    val pagesToRotate = pageIndices ?: (0 until doc.numberOfPages).toList()
                    for (index in pagesToRotate) {
                        if (index in 0 until doc.numberOfPages) {
                            val page = doc.getPage(index)
                            val currentRotation = page.rotation
                            val newRotation = (currentRotation + rotationDegrees) % 360
                            page.rotation = if (newRotation < 0) newRotation + 360 else newRotation
                        }
                    }

                    val tempFile = File.createTempFile("rotate_tmp_", ".pdf", context.cacheDir).apply {
                        deleteOnExit()
                    }
                    try {
                        tempFile.outputStream().buffered().use { outputStream ->
                            doc.save(outputStream)
                        }
                        // Need atomicReplace, let's see why it's failing
                        if (atomicReplace(tempFile, destUri)) {
                            success = true
                        } else {
                            println("atomicReplace failed")
                        }
                    } finally {
                        if (tempFile.exists()) {
                            FileUtils.secureDelete(tempFile)
                        }
                    }
                }
            } ?: println("openSafeInputStream returned null for $sourceUri")
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

    override suspend fun packageToZip(
        fileUris: List<String>,
        outputZipUri: String,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(ioDispatcher) {
        try {
            val total = fileUris.size
            val outStream = openSafeOutputStream(outputZipUri) ?: return@withContext false
            ZipOutputStream(outStream).use { zos ->
                val buffer = ByteArray(8192)
                fileUris.forEachIndexed { index, uriString ->
                    ensureActive()
                    val inStream = openSafeInputStream(uriString)
                    if (inStream != null) {
                        inStream.use { fis ->
                            val fileName = getFileName(uriString)
                            val zipEntry = ZipEntry(fileName)
                            zos.putNextEntry(zipEntry)
                            var length: Int
                            while (fis.read(buffer).also { length = it } > 0) {
                                zos.write(buffer, 0, length)
                            }
                            zos.closeEntry()
                        }
                    }
                    onProgress(index + 1, total)
                }
            }
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            false
        }
    }
}
