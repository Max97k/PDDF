package com.example.feature.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

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
    val context = LocalContext.current
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var tempFileHolder by remember { mutableStateOf<File?>(null) }

    // 12.5% of max heap for PDF bitmap caching
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
    val pageBitmapCache = remember {
        object : LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    LaunchedEffect(uri) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                var tempFile: File? = null
                val fileDescriptor: ParcelFileDescriptor? = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (_: Exception) {
                    null
                } ?: run {
                    val temp = File(context.cacheDir, "preview_temp_${System.currentTimeMillis()}.pdf")
                    val inputStream = try {
                        context.contentResolver.openInputStream(uri)
                    } catch (_: Exception) {
                        try {
                            if (uri.scheme == "file" && uri.path != null) {
                                FileInputStream(File(uri.path!!))
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }

                    inputStream?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile = temp
                    tempFileHolder = temp
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
        }
    }

    DisposableEffect(uri) {
        onDispose {
            try {
                renderer?.close()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
            tempFileHolder?.let { file ->
                CoroutineScope(Dispatchers.IO).launch {
                    FileUtils.secureDelete(file)
                }
            }
            val snapshot = pageBitmapCache.snapshot()
            pageBitmapCache.evictAll()
            snapshot.values.forEach { bmp ->
                if (bmp != null && !bmp.isRecycled) {
                    bmp.recycle()
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf {
            if (pageCount > 0) (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount) else 1
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
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
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 88.dp
                            ),
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

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
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
