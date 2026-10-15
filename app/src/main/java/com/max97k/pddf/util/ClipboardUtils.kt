package com.max97k.pddf.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardUtils {

    @OptIn(DelicateCoroutinesApi::class)
    fun copyPasswordToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val bundle = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            try {
                val method = ClipDescription::class.java.getMethod("setExtras", PersistableBundle::class.java)
                method.invoke(clip.description, bundle)
            } catch (e: Exception) {
                // Ignore if method is not available via reflection
            }
        }

        clipboard.setPrimaryClip(clip)

        // Use GlobalScope to ensure the clipboard is cleared even if the dialog/activity is dismissed
        GlobalScope.launch(Dispatchers.Default) {
            delay(30_000)
            if (clipboard.primaryClip?.getItemAt(0)?.text?.toString() == text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
}
