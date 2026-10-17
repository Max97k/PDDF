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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject

class PdfPrintDocumentAdapter(
    private val context: Context,
    private val originalUri: Uri,
    private val fileName: String,
    private val isTempFile: Boolean = false,
    private val isGrayscale: Boolean = false,
    private val customPageRange: String? = null
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
        var document: PDDocument? = null

        try {
            inputStream = context.contentResolver.openInputStream(originalUri)
            if (inputStream == null) {
                callback.onWriteFailed("Cannot read PDF")
                return
            }

            document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages

            // Parse custom page ranges if provided, else use the ones from PrintManager
            val pagesToPrint = mutableSetOf<Int>()

            if (customPageRange != null) {
                val parts = customPageRange.split(",")
                for (part in parts) {
                    val trim = part.trim()
                    if (trim.contains("-")) {
                        val rangeParts = trim.split("-")
                        if (rangeParts.size == 2) {
                            val start = rangeParts[0].toIntOrNull()
                            val end = rangeParts[1].toIntOrNull()
                            if (start != null && end != null && start > 0 && end <= totalPages && start <= end) {
                                for (i in start..end) {
                                    pagesToPrint.add(i - 1)
                                }
                            }
                        }
                    } else {
                        val page = trim.toIntOrNull()
                        if (page != null && page > 0 && page <= totalPages) {
                            pagesToPrint.add(page - 1)
                        }
                    }
                }
            } else {
                for (pageRange in pages) {
                    if (pageRange == PageRange.ALL_PAGES) {
                        for (i in 0 until totalPages) {
                            pagesToPrint.add(i)
                        }
                        break
                    } else {
                        for (i in pageRange.start..pageRange.end) {
                            if (i in 0 until totalPages) {
                                pagesToPrint.add(i)
                            }
                        }
                    }
                }
            }

            // Create a new document for the selected pages
            val newDocument = PDDocument()
            val sortedPagesToPrint = pagesToPrint.sorted()

            for (pageIndex in sortedPagesToPrint) {
                if (cancellationSignal.isCanceled) {
                    newDocument.close()
                    callback.onWriteCancelled()
                    return
                }

                val page = document.getPage(pageIndex)

                if (isGrayscale) {
                    // Convert images to grayscale
                    val resources = page.resources
                    if (resources != null) {
                        val xObjectNames = resources.xObjectNames.toList()
                        for (name in xObjectNames) {
                            val xObject = resources.getXObject(name)
                            if (xObject is PDImageXObject) {
                                // Simplified approach: just change color space metadata
                                // A true pixel-by-pixel conversion requires loading the bitmap
                                // For PDFBox-Android, converting color space is often enough for standard printing
                                xObject.colorSpace = PDDeviceGray.INSTANCE
                            }
                        }
                    }
                }

                newDocument.addPage(page)
            }

            outputStream = FileOutputStream(destination.fileDescriptor)
            newDocument.save(outputStream)
            newDocument.close()

            // Construct array of page ranges printed
            val printedRanges = mutableListOf<PageRange>()
            if (sortedPagesToPrint.size == totalPages && sortedPagesToPrint.firstOrNull() == 0 && sortedPagesToPrint.lastOrNull() == totalPages - 1) {
                printedRanges.add(PageRange.ALL_PAGES)
            } else {
                // Simplified range reporting
                var start = -1
                var end = -1
                for (page in sortedPagesToPrint) {
                    if (start == -1) {
                        start = page
                        end = page
                    } else if (page == end + 1) {
                        end = page
                    } else {
                        printedRanges.add(PageRange(start, end))
                        start = page
                        end = page
                    }
                }
                if (start != -1) {
                    printedRanges.add(PageRange(start, end))
                }
            }

            callback.onWriteFinished(printedRanges.toTypedArray())
        } catch (e: Exception) {
            Log.e("PdfPrintAdapter", "Failed to write PDF for printing", e)
            callback.onWriteFailed(e.message)
        } finally {
            try { document?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    override fun onFinish() {
        super.onFinish()
    }
}
