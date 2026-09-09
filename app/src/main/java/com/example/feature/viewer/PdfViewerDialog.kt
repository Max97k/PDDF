package com.example.feature.viewer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

@Composable
fun PdfViewerDialog(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        PdfViewerScreen(
            uri = uri,
            modifier = Modifier.fillMaxSize(),
            title = title,
            onClose = onDismiss,
            onShare = onShare,
            onSaveAs = onSaveAs
        )
    }
}

