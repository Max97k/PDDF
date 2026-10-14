package com.max97k.pddf.print

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import com.max97k.pddf.util.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import androidx.pdf.PdfDocument

class PdfPrintDocumentAdapter(
    private val context: Context,
    private val originalUri: Uri,
    private val fileName: String,
    private val isTempFile: Boolean = false
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(fileName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = context.contentResolver.openInputStream(originalUri)
            if (inputStream == null) {
                callback.onWriteFailed("Cannot read PDF")
                return
            }

            outputStream = FileOutputStream(destination.fileDescriptor)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                outputStream.write(buffer, 0, bytesRead)
            }

            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            Log.e("PdfPrintAdapter", "Failed to write PDF for printing", e)
            callback.onWriteFailed(e.message)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    override fun onFinish() {
        super.onFinish()
    }
}
