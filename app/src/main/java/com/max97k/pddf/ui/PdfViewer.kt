package com.max97k.pddf.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.max97k.pddf.feature.viewer.PdfViewerDialog as FeaturePdfViewerDialog
import com.max97k.pddf.feature.viewer.PdfViewerScreen as FeaturePdfViewerScreen

@Composable
fun PdfViewerDialog(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null,
    onExtract: (() -> Unit)? = null
) {
    FeaturePdfViewerDialog(
        uri = uri,
        modifier = modifier,
        title = title,
        onDismiss = onDismiss,
        onShare = onShare,
        onSaveAs = onSaveAs,
        onExtract = onExtract
    )
}

@Composable
fun PdfViewerScreen(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onClose: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null,
    onExtract: (() -> Unit)? = null
) {
    FeaturePdfViewerScreen(
        uri = uri,
        modifier = modifier,
        title = title,
        onClose = onClose,
        onShare = onShare,
        onSaveAs = onSaveAs,
        onExtract = onExtract
    )
}
