package com.max97k.pddf

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import com.max97k.pddf.util.CrashDiagnosticsManager
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PdfApplication : Application(), Configuration.Provider {

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
}
