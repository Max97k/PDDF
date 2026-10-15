package com.max97k.pddf.feature.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.max97k.pddf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.lazy.grid.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageOverviewDialog(
    uri: Uri,
    currentPage: Int = 0,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }

    // Calculate memory safely as per memory guidelines
    val cacheSize = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    val bitmapCache = remember {
        object : LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    LaunchedEffect(uri) {
        var localFd: ParcelFileDescriptor? = null
        var localRenderer: PdfRenderer? = null
        try {
            withContext(Dispatchers.IO) {
                localFd = context.contentResolver.openFileDescriptor(uri, "r")
                if (localFd != null) {
                    localRenderer = PdfRenderer(localFd!!)
                    fileDescriptor = localFd
                    pdfRenderer = localRenderer
                    pageCount = localRenderer!!.pageCount
                }
            }
            awaitCancellation()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                bitmapCache.evictAll()
                try {
                    localRenderer?.let {
                        synchronized(it) {
                            it.close()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    localFd?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.btn_page_overview)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            }
        ) { innerPadding ->
            if (pdfRenderer != null && pageCount > 0) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    items(pageCount) { index ->
                        PdfThumbnailItem(
                            renderer = pdfRenderer!!,
                            pageIndex = index,
                            isCurrentPage = (index == currentPage),
                            cache = bitmapCache,
                            onClick = { onPageSelected(index) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

}

@Composable
fun PdfThumbnailItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    isCurrentPage: Boolean = false,
    cache: LruCache<Int, Bitmap>,
    onClick: () -> Unit
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(cache.get(pageIndex)) }
    val density = LocalDensity.current.density

    LaunchedEffect(pageIndex) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    synchronized(renderer) {
                        renderer.openPage(pageIndex).use { page ->
                            val targetWidth = 120 * density
                            val scale = targetWidth / page.width
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)

                            val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(newBitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)

                            page.render(newBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            cache.put(pageIndex, newBitmap)
                            bitmap = newBitmap
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .let {
                    if (isCurrentPage) {
                        it.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(4.dp)
                    } else {
                        it
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.content_desc_page, pageIndex + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            // Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
