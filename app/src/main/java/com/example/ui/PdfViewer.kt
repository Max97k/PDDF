package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance, zero-dependency native PDF Viewer dialog built on Android's PdfRenderer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerDialog(
    uri: Uri,
    title: String = "",
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null
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
            title = title,
            onClose = onDismiss,
            onShare = onShare
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    uri: Uri,
    title: String = "",
    onClose: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Memory cache for rendered pages
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val cacheSize = (maxMemory / 8).coerceAtLeast(1024) // 12.5% of heap for PDF bitmap cache
    val pageBitmapCache = remember {
        object : LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    // Initialize PdfRenderer safely
    DisposableEffect(uri) {
        var tempFile: java.io.File? = null
        try {
            val fileDescriptor: ParcelFileDescriptor? = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null
            } ?: run {
                // Guaranteed fallback: Copy stream to app's private cache directory
                val temp = java.io.File(context.cacheDir, "preview_temp_${System.currentTimeMillis()}.pdf")
                val inputStream = try {
                    if (uri.scheme == "file" && uri.path != null) {
                        java.io.FileInputStream(java.io.File(uri.path!!))
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }
                } catch (_: Exception) {
                    context.contentResolver.openInputStream(uri)
                }

                inputStream?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile = temp
                if (temp.exists() && temp.length() > 0) {
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    null
                }
            }

            if (fileDescriptor != null) {
                pfd = fileDescriptor
                val pdfRenderer = PdfRenderer(fileDescriptor)
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
                isLoading = false
            } else {
                errorMessage = context.getString(R.string.error_pdf_preview)
                isLoading = false
            }
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: context.getString(R.string.error_pdf_preview)
            isLoading = false
        }

        onDispose {
            try {
                renderer?.close()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
            tempFile?.delete()
            pageBitmapCache.evictAll()
        }
    }

    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf {
            if (pageCount > 0) (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount) else 1
        }
    }

    // Pinch-to-zoom & Pan transform states
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title.ifEmpty { "PDF Viewer" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = stringResource(R.string.label_page_indicator, currentPage, pageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = errorMessage ?: stringResource(R.string.error_pdf_preview),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onClose) {
                                Text(stringResource(R.string.btn_close))
                            }
                        }
                    }
                }
                renderer != null && pageCount > 0 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(
                                count = pageCount,
                                key = { it }
                            ) { pageIndex ->
                                PdfPageItem(
                                    renderer = renderer!!,
                                    pageIndex = pageIndex,
                                    cache = pageBitmapCache
                                )
                            }
                        }
                    }

                    // Floating page badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_page_indicator, currentPage, pageCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: LruCache<Int, Bitmap>,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(cache.get(pageIndex)) }
    val density = LocalDensity.current

    // Asynchronously render the page if not in cache
    LaunchedEffect(pageIndex) {
        if (pageBitmap == null) {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    synchronized(renderer) {
                        val page = renderer.openPage(pageIndex)
                        val screenDensity = density.density
                        val width = (page.width * screenDensity * 1.5f).toInt().coerceAtLeast(1)
                        val height = (page.height * screenDensity * 1.5f).toInt().coerceAtLeast(1)

                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        cache.put(pageIndex, bmp)
                        bmp
                    }
                } catch (e: Exception) {
                    null
                }
            }
            pageBitmap = bitmap
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // Standard A4 ratio placeholder
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            }
        }
    }
}
