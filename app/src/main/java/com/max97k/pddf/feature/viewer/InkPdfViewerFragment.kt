package com.max97k.pddf.feature.viewer

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * A thin subclass of [PdfViewerFragment] that bridges the immersive-mode callback
 * and search state changes to Compose-friendly lambdas and manages document/search
 * lifecycles and toolbox visibility.
 *
 * [PdfViewerFragment.onRequestImmersiveMode] is the only way to be notified when
 * the user taps the PDF page body and the fragment wishes to toggle full-screen
 * reading mode.
 */
class InkPdfViewerFragment : PdfViewerFragment() {

    companion object {
        private const val TAG = "InkPdfViewerFragment"
    }

    /** Invoked on the main thread whenever the fragment requests an immersive-mode change. */
    var onImmersiveModeRequest: ((enterImmersive: Boolean) -> Unit)? = null

    /** Invoked on the main thread whenever search active state changes (e.g. user closes native search bar). */
    var onSearchModeChanged: ((isActive: Boolean) -> Unit)? = null

    /** Tracks whether the document has finished loading successfully. */
    var isDocumentLoaded: Boolean = false
        internal set

    /** Tracks desired search active state while document is loading or unattached. */
    var pendingSearchActive: Boolean = false
        internal set

    /** Notifies when document is loaded so we can check reading progress */
    var onDocumentLoaded: ((Int) -> Unit)? = null

    /** Tracks the current visible page. Called periodically. */
    var onPageChanged: ((Int, Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val searchViewId = view.resources.getIdentifier("pdfSearchView", "id", view.context.packageName)
        val searchView = if (searchViewId != 0) view.findViewById<View>(searchViewId) else null
        searchView?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val isVisible = searchView.visibility == View.VISIBLE
            onSearchModeChanged?.invoke(isVisible)
        }
    }

    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        super.onRequestImmersiveMode(enterImmersive)
        onImmersiveModeRequest?.invoke(enterImmersive)
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        handleDocumentLoadedSuccess()
    }

    /** Overload for direct or test invocation without [PdfDocument] parameter. */
    fun onLoadDocumentSuccess() {
        handleDocumentLoadedSuccess()
    }

    private fun handleDocumentLoadedSuccess() {
        isDocumentLoaded = true

        try {
            val pdfViewField = PdfViewerFragment::class.java.getDeclaredField("pdfView")
            pdfViewField.isAccessible = true
            val pdfView = pdfViewField.get(this)
            if (pdfView != null) {
                // Since there is no listener, we use a Runnable to poll
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val runnable = object : Runnable {
                    override fun run() {
                        if (isAdded && !isDetached && isDocumentLoaded) {
                            try {
                                val getFirstVisiblePageMethod = pdfView.javaClass.getMethod("getFirstVisiblePage")
                                val page = getFirstVisiblePageMethod.invoke(pdfView) as? Int ?: 0

                                val pdfDocumentField = PdfViewerFragment::class.java.getDeclaredField("pdfDocument")
                                pdfDocumentField.isAccessible = true
                                val pdfDoc = pdfDocumentField.get(this@InkPdfViewerFragment) as? PdfDocument
                                val totalPages = pdfDoc?.pageCount ?: 0

                                onPageChanged?.invoke(page, totalPages)
                                handler.postDelayed(this, 1000)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
                handler.postDelayed(runnable, 500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup page polling", e)
        }

        try {
            val pdfDocumentField = PdfViewerFragment::class.java.getDeclaredField("pdfDocument")
            pdfDocumentField.isAccessible = true
            val pdfDoc = pdfDocumentField.get(this) as? PdfDocument
            onDocumentLoaded?.invoke(pdfDoc?.pageCount ?: 0)
        } catch (e: Exception) {
            onDocumentLoaded?.invoke(0)
        }

        if (pendingSearchActive) {
            try {
                if (isAdded && !isDetached) {
                    isTextSearchActive = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to activate pending search in onLoadDocumentSuccess", e)
            }
        }
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        Log.e(TAG, "Failed to load PDF document", error)
        isDocumentLoaded = false
    }

    fun scrollToPage(page: Int) {
        gotoPage(page)
    }

    /**
     * Sets whether text search is active. If the document is not yet loaded or
     * the fragment is not attached, queues the state in [pendingSearchActive].
     */
    fun setSearchActive(active: Boolean) {
        pendingSearchActive = active
        if (isDocumentLoaded && isAdded && !isDetached) {
            try {
                isTextSearchActive = active
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to set text search active: $active", e)
            }
        }
    }

    /** Jumps to a specific page using reflection on the internal pdfView. */
    fun gotoPage(pageIndex: Int) {
        if (!isDocumentLoaded || !isAdded || isDetached) return
        try {
            val pdfViewField = PdfViewerFragment::class.java.getDeclaredField("pdfView")
            pdfViewField.isAccessible = true
            val pdfView = pdfViewField.get(this)
            if (pdfView != null) {
                val scrollToPageMethod = pdfView.javaClass.getMethod("scrollToPage", Int::class.javaPrimitiveType)
                scrollToPageMethod.invoke(pdfView, pageIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to page $pageIndex", e)
        }
    }

    /** Gets the currently visible page using reflection on the internal pdfView. */
    fun getCurrentPage(): Int {
        if (!isDocumentLoaded || !isAdded || isDetached) return 0
        return try {
            val pdfViewField = PdfViewerFragment::class.java.getDeclaredField("pdfView")
            pdfViewField.isAccessible = true
            val pdfView = pdfViewField.get(this)
            if (pdfView != null) {
                val getFirstVisiblePageMethod = pdfView.javaClass.getMethod("getFirstVisiblePage")
                getFirstVisiblePageMethod.invoke(pdfView) as? Int ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current page", e)
            0
        }
    }

    fun getFirstVisiblePage(): Int? = getCurrentPage()
}
