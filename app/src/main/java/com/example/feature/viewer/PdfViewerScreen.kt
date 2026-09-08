package com.example.feature.viewer

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onClose: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifEmpty { "PDF Viewer" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_close)
                        )
                    }
                },
                actions = {
                    if (onSaveAs != null) {
                        IconButton(onClick = onSaveAs) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = stringResource(R.string.btn_save_as)
                            )
                        }
                    }
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.btn_share_file)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val context = LocalContext.current
            val fragmentActivity = remember(context) { context.findFragmentActivity() }
            val containerId = rememberSaveable { View.generateViewId() }
            val fragmentTag = remember(containerId) { "pdf_viewer_$containerId" }

            if (fragmentActivity != null) {
                val fragmentManager = fragmentActivity.supportFragmentManager
                // Guard against duplicate commits: AndroidView.update fires on every recomposition,
                // but fragment transactions are asynchronous — findFragmentByTag returns null until
                // the commit is processed, so without this flag a second commit could sneak in.
                val committed = remember(containerId) { booleanArrayOf(false) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FragmentContainerView(ctx).apply {
                            id = containerId
                        }
                    },
                    update = { _ ->
                        if (!fragmentManager.isStateSaved && !fragmentManager.isDestroyed) {
                            val existing = fragmentManager.findFragmentByTag(fragmentTag) as? PdfViewerFragment
                            if (existing == null && !committed[0]) {
                                committed[0] = true
                                val fragment = PdfViewerFragment().apply {
                                    documentUri = uri
                                }
                                fragmentManager.commit {
                                    setReorderingAllowed(true)
                                    replace(containerId, fragment, fragmentTag)
                                }
                            } else if (existing != null && existing.documentUri != uri) {
                                existing.documentUri = uri
                            }
                        }
                    }
                )

                DisposableEffect(containerId, fragmentManager) {
                    onDispose {
                        if (!fragmentManager.isDestroyed) {
                            val fragment = fragmentManager.findFragmentByTag(fragmentTag)
                            if (fragment != null) {
                                try {
                                    fragmentManager.commitNow(allowStateLoss = true) {
                                        remove(fragment)
                                    }
                                } catch (_: Exception) {
                                    fragmentManager.commit(allowStateLoss = true) {
                                        remove(fragment)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.error_pdf_preview),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
