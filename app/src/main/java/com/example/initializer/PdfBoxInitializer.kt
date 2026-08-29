package com.example.initializer

import android.content.Context
import androidx.startup.Initializer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PdfBoxInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // Asynchronously preload and initialize PDFBox resources during app startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
