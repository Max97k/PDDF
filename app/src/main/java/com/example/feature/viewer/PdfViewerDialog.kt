package com.example.feature.viewer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PdfViewerDialog(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        PdfViewerScreen(
            uri = uri,
            modifier = modifier,
            title = title,
            onClose = onDismiss,
            onShare = onShare,
            onSaveAs = onSaveAs
        )
    }
}
