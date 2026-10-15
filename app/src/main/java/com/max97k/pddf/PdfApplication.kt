package com.max97k.pddf

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import com.max97k.pddf.util.CrashDiagnosticsManager
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.max97k.pddf.util.MemoryUtils

@HiltAndroidApp
class PdfApplication : Application(), Configuration.Provider, ComponentCallbacks2 {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashDiagnosticsManager.initialize(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            MemoryUtils.clearPdfBoxFontCache()
            MemoryUtils.clearTemporaryBitmaps()
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MemoryUtils.clearPdfBoxFontCache()
        MemoryUtils.clearTemporaryBitmaps()
        System.gc()
    }
}
