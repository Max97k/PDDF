package com.max97k.pddf.domain.usecase

import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import javax.inject.Inject

class ExportMetadataReportUseCase @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun exportToCsv(
        metadataList: List<Pair<String, PdfMetadata>>,
        outputStream: OutputStream
    ): Boolean = withContext(ioDispatcher) {
        try {
            outputStream.bufferedWriter().use { writer ->
                writer.write("FileName,Title,Author,PageCount,FileSizeMb,PdfVersion,EncryptionMethod,CanPrint,CanCopy\n")
                metadataList.forEach { (fileName, metadata) ->
                    val escapedName = escapeCsv(fileName)
                    val escapedTitle = escapeCsv(metadata.title)
                    val escapedAuthor = escapeCsv(metadata.author)
                    writer.write(
                        "$escapedName,$escapedTitle,$escapedAuthor,${metadata.pageCount}," +
                        "${metadata.fileSizeMb},${metadata.pdfVersion},${escapeCsv(metadata.encryptionMethod)}," +
                        "${metadata.canPrint},${metadata.canCopy}\n"
                    )
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportToJson(
        metadataList: List<Pair<String, PdfMetadata>>,
        outputStream: OutputStream
    ): Boolean = withContext(ioDispatcher) {
        try {
            val jsonArray = JSONArray()
            metadataList.forEach { (fileName, metadata) ->
                val jsonObject = JSONObject().apply {
                    put("fileName", fileName)
                    put("title", metadata.title)
                    put("author", metadata.author)
                    put("pageCount", metadata.pageCount)
                    put("fileSizeMb", metadata.fileSizeMb)
                    put("pdfVersion", metadata.pdfVersion)
                    put("encryptionMethod", metadata.encryptionMethod)
                    put("canPrint", metadata.canPrint)
                    put("canCopy", metadata.canCopy)
                }
                jsonArray.put(jsonObject)
            }
            outputStream.bufferedWriter().use { writer ->
                writer.write(jsonArray.toString(4))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
