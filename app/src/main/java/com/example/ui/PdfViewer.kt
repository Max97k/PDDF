package com.example.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.feature.viewer.PdfViewerDialog as FeaturePdfViewerDialog
import com.example.feature.viewer.PdfViewerScreen as FeaturePdfViewerScreen

@Composable
fun PdfViewerDialog(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null
) {
    FeaturePdfViewerDialog(
        uri = uri,
        modifier = modifier,
        title = title,
        onDismiss = onDismiss,
        onShare = onShare,
        onSaveAs = onSaveAs
    )
}

@Composable
fun PdfViewerScreen(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onClose: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null
) {
    FeaturePdfViewerScreen(
        uri = uri,
        modifier = modifier,
        title = title,
        onClose = onClose,
        onShare = onShare,
        onSaveAs = onSaveAs
    )
}
