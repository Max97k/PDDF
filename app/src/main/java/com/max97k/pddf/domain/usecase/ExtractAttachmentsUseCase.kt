package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.common.PDNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PdfAttachment(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
)

class ExtractAttachmentsUseCase @Inject constructor(
    private val pdfRepository: PdfRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getAttachments(
        uriString: String,
        passwordValue: String = ""
    ): List<PdfAttachment> = withContext(ioDispatcher) {
        val attachments = mutableListOf<PdfAttachment>()
        try {
            pdfRepository.openSafeInputStream(uriString)?.use { inputStream ->
                val doc = try {
                    PDDocument.load(inputStream.buffered(), passwordValue)
                } catch (e: Exception) {
                    null
                }
                doc?.use {
                    val names: PDDocumentNameDictionary? = it.documentCatalog.names
                    val embeddedFiles = names?.embeddedFiles
                    if (embeddedFiles != null) {
                        val namesMap = getEmbeddedFiles(embeddedFiles)
                        for ((name, fs) in namesMap) {
                            if (fs is PDComplexFileSpecification) {
                                val ef = fs.embeddedFile
                                if (ef != null) {
                                    val size = ef.size.toLong()
                                    val subtype = ef.subtype ?: "application/octet-stream"
                                    attachments.add(PdfAttachment(fs.filename ?: name, size, subtype))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        attachments
    }

    private fun getEmbeddedFiles(treeNode: PDNameTreeNode<*>): Map<String, Any> {
        val names = mutableMapOf<String, Any>()
        if (treeNode.names != null) {
            names.putAll(treeNode.names)
        }
        if (treeNode.kids != null) {
            for (kid in treeNode.kids) {
                names.putAll(getEmbeddedFiles(kid))
            }
        }
        return names
    }

    suspend fun saveAttachment(
        sourceUriString: String,
        attachmentName: String,
        destUriString: String,
        passwordValue: String = ""
    ): Boolean = withContext(ioDispatcher) {
        try {
            pdfRepository.openSafeInputStream(sourceUriString)?.use { inputStream ->
                val doc = try {
                    PDDocument.load(inputStream.buffered(), passwordValue)
                } catch (e: Exception) {
                    null
                }
                doc?.use {
                    val names = it.documentCatalog.names
                    val embeddedFiles = names?.embeddedFiles
                    if (embeddedFiles != null) {
                        val namesMap = getEmbeddedFiles(embeddedFiles)
                        for ((name, fs) in namesMap) {
                            if (fs is PDComplexFileSpecification && (fs.filename == attachmentName || name == attachmentName)) {
                                val ef = fs.embeddedFile
                                if (ef != null) {
                                    pdfRepository.openSafeOutputStream(destUriString)?.use { outputStream ->
                                        ef.createInputStream().use { efInput ->
                                            efInput.copyTo(outputStream)
                                        }
                                    }
                                    return@withContext true
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }
}
