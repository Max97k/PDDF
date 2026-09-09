package com.example.feature.viewer

import android.app.Application
import android.content.ComponentName
import android.os.Binder
import android.os.IInterface
import androidx.pdf.PdfDocumentRemote
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.Proxy

/**
 * Test utility providing a fake Android Binder for PdfDocumentService.
 * In Robolectric JVM unit tests, external IPC services are not run by default.
 * Registering this mock Binder ensures that androidx.pdf's service connection
 * receives a valid non-null IBinder and avoids NullPointerException during bindService.
 */
object PdfTestUtils {

    fun registerFakePdfService() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)

        val remoteProxy = Proxy.newProxyInstance(
            PdfDocumentRemote::class.java.classLoader,
            arrayOf(PdfDocumentRemote::class.java, IInterface::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "asBinder" -> null
                "numPages" -> 1
                "openPdfDocument" -> 0
                "getInterfaceVersion" -> 1
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    List::class.java -> emptyList<Any>()
                    else -> null
                }
            }
        } as IInterface

        val mockBinder = object : Binder() {
            override fun queryLocalInterface(descriptor: String): IInterface {
                return remoteProxy
            }
        }

        val componentNames = listOf(
            ComponentName(app.packageName, "androidx.pdf.service.PdfDocumentService"),
            ComponentName("androidx.pdf.service", "androidx.pdf.service.PdfDocumentService"),
            ComponentName("com.max97k.pddf", "androidx.pdf.service.PdfDocumentService")
        )
        for (cn in componentNames) {
            shadowApp.setComponentNameAndServiceForBindService(cn, mockBinder)
        }
    }
}
