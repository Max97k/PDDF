package com.max97k.pddf.feature.viewer

import android.content.Context
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Slider
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Palette
import com.max97k.pddf.ui.components.ReadingModeBottomSheet
import androidx.compose.runtime.collectAsState
import com.max97k.pddf.di.PreferencesEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.max97k.pddf.di.DatabaseModule
import com.max97k.pddf.data.AppDatabase
import com.max97k.pddf.data.local.ReadingProgressEntity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.max97k.pddf.data.ReadingMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.max97k.pddf.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.max97k.pddf.domain.repository.PdfRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import androidx.compose.material.icons.filled.List

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PdfViewerEntryPoint {
    fun pdfRepository(): PdfRepository
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    uri: Uri,
    modifier: Modifier = Modifier,
    title: String = "",
    onClose: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSaveAs: (() -> Unit)? = null,
    onAddWatermark: ((String) -> Unit)? = null,
    onRotateAll: ((Int) -> Unit)? = null,
    onExtract: (() -> Unit)? = null,
    viewModel: PdfViewerViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHash = remember(uri) { uri.toString().hashCode() }
    val isDarkTheme = isSystemInDarkTheme()
    val view = LocalView.current
    val fragmentActivity = remember(context) { context.findFragmentActivity() }
    val insetsController = remember(fragmentActivity, view) {
        fragmentActivity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view)
        }
    }

    val owner = LocalViewModelStoreOwner.current
    val effectiveViewModel: PdfViewerViewModel? = viewModel ?: remember(owner, context) {
        if (owner != null) {
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    return PdfViewerViewModel(db.bookmarkDao()) as T
                }
            }
            try {
                ViewModelProvider(owner, factory)[PdfViewerViewModel::class.java]
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // true  → TopAppBar is visible (normal reading)
    // false → TopAppBar is hidden (immersive full-screen reading)
    var topBarVisible by rememberSaveable { mutableStateOf(true) }

    // Tracks whether the native PDF search bar is currently active.
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showWatermarkDialog by rememberSaveable { mutableStateOf(false) }

    if (showWatermarkDialog) {
        PdfWatermarkDialog(
            onDismissRequest = { showWatermarkDialog = false },
            onApply = { text ->
                onAddWatermark?.invoke(text)
                showWatermarkDialog = false
            }
        )
    }

    // Tracks whether the rotation dropdown menu is visible.
    var showRotationMenu by rememberSaveable { mutableStateOf(false) }


    var showReadingModeSheet by rememberSaveable { mutableStateOf(false) }

    val preferencesEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PreferencesEntryPoint::class.java)
    }
    val securityPreferences = preferencesEntryPoint.securityPreferences()
    val readingMode by securityPreferences.readingMode.collectAsState()
    val scope = rememberCoroutineScope()

    // Tracks visibility of custom print options dialog
    var showPrintDialog by remember { mutableStateOf(false) }

    // Tracks whether the page overview dialog is currently open.
    var showPageOverview by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uri, effectiveViewModel) {
        effectiveViewModel?.loadBookmarks(uri)
    }

    val bookmarks = effectiveViewModel?.bookmarks?.collectAsState()?.value ?: emptyList()

    var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
    var showAddBookmarkDialog by rememberSaveable { mutableStateOf(false) }

    val containerId = rememberSaveable { View.generateViewId() }
    val fragmentTag = remember(containerId) { "pdf_viewer_$containerId" }

    var currentPageIndex by remember { mutableStateOf<Int?>(null) }



    val repository = remember(context) {
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                PdfViewerEntryPoint::class.java
            ).pdfRepository()
        } catch (_: Exception) {
            com.max97k.pddf.data.repository.PdfRepositoryImpl(
                context.applicationContext,
                kotlinx.coroutines.Dispatchers.IO
            )
        }
    }

    val ttsController = remember { PdfTextToSpeechController(context, repository, coroutineScope) }
    var ttsBarVisible by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(ttsController) {
        onDispose { ttsController.release() }
    }


    LaunchedEffect(topBarVisible, insetsController) {
        insetsController?.let { controller ->
            if (topBarVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(insetsController) {
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // When TopAppBar is hidden, 0 insets so the PDF takes up 100% of the display.
    // When visible, safeDrawing accounts for system bars.
    val contentInsets = if (topBarVisible) {
        WindowInsets.safeDrawing
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    if (showPrintDialog) {
        PdfPrintOptionsDialog(
            onDismiss = { showPrintDialog = false },
            onConfirm = { isGrayscale, pageRange ->
                showPrintDialog = false
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                printManager?.let {
                    val fileName = com.max97k.pddf.util.FileUtils.getFileName(context, uri)
                    val adapter = com.max97k.pddf.print.PdfPrintDocumentAdapter(
                        context = context,
                        originalUri = uri,
                        fileName = fileName,
                        isTempFile = false,
                        isGrayscale = isGrayscale,
                        customPageRange = pageRange
                    )
                    val builder = android.print.PrintAttributes.Builder()
                    it.print(fileName, adapter, builder.build())
                }
            }
        )
    }

    var outline by remember { mutableStateOf<List<com.max97k.pddf.feature.viewer.OutlineItem>>(emptyList()) }

    LaunchedEffect(uri) {
        outline = repository.getOutline(uri.toString())
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val isCurrentPageBookmarked = bookmarks.any { it.pageIndex == currentPageIndex }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                com.max97k.pddf.feature.viewer.PdfOutlineDrawer(
                    outline = outline,
                    onPageClicked = { pageIndex ->
                        coroutineScope.launch { drawerState.close() }
                        if (fragmentActivity != null) {
                            val fragmentManager = fragmentActivity.supportFragmentManager
                            val fragment = fragmentManager.fragments.firstOrNull { it is InkPdfViewerFragment } as? InkPdfViewerFragment
                            if (fragment != null) {
                                try {
                                    val method = fragment.javaClass.getMethod("getPdfView")
                                    method.isAccessible = true
                                    val pdfView = method.invoke(fragment)
                                    if (pdfView != null) {
                                        val scrollToPageMethod = pdfView.javaClass.getMethod("scrollToPage", Int::class.java)
                                        scrollToPageMethod.isAccessible = true
                                        scrollToPageMethod.invoke(pdfView, pageIndex)
                                    }
                                } catch (e: Exception) {
                                    Log.e("PdfViewerScreen", "Failed to scroll to page", e)
                                }
                            }
                        }
                    }
                )
            }
        }
    ) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = contentInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = topBarVisible,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = title.ifEmpty { stringResource(R.string.title_pdf_viewer) },
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
                        if (outline.isNotEmpty()) {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "Bookmarks"
                                )
                            }
                        }
                        IconButton(onClick = {
                            val activePage = currentPageIndex ?: (fragmentActivity?.supportFragmentManager?.findFragmentByTag(fragmentTag) as? InkPdfViewerFragment)?.getCurrentPage() ?: 0
                            currentPageIndex = activePage
                            if (isCurrentPageBookmarked) {
                                effectiveViewModel?.deleteBookmark(activePage)
                            } else {
                                showAddBookmarkDialog = true
                            }
                        }) {
                            Icon(
                                imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark"
                            )
                        }
                        // Search toggle — open search on first tap, close on second tap.

                        IconButton(onClick = {
                            ttsBarVisible = !ttsBarVisible
                            if (!ttsBarVisible) ttsController.stop()
                        }) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Text to Speech"
                            )
                        }

                        IconButton(onClick = {
                            showBookmarksSheet = false
                            isSearchActive = !isSearchActive
                            if (isSearchActive) {
                                topBarVisible = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = if (isSearchActive) {
                                    stringResource(R.string.btn_exit_search_pdf)
                                } else {
                                    stringResource(R.string.btn_search_pdf)
                                }
                            )
                        }

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
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Bookmarks") },
                                    leadingIcon = { Icon(Icons.Default.List, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showBookmarksSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_page_overview)) },
                                    leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showPageOverview = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_reading_mode)) },
                                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showReadingModeSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_print)) },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showPrintDialog = true
                                    }
                                )
                                if (onRotateAll != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.btn_rotate_cw)) },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onRotateAll.invoke(90)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.btn_rotate_ccw)) },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onRotateAll.invoke(-90)
                                        }
                                    )
                                }
                                if (onExtract != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.btn_extract_pages)) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onExtract.invoke()
                                        }
                                    )
                                }
                                if (onAddWatermark != null) {
                                    DropdownMenuItem(
                                        text = { Text("Watermark") },
                                        onClick = {
                                            showMoreMenu = false
                                            showWatermarkDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {

            val preserveImages by securityPreferences.preserveEmbeddedImages.collectAsState()

            if (showReadingModeSheet) {
                ReadingModeBottomSheet(
                    currentMode = readingMode,
                    preserveImages = preserveImages,
                    onPreserveImagesChanged = { checked ->
                        scope.launch {
                            securityPreferences.savePreserveImages(checked)
                        }
                    },
                    onModeSelected = { newMode ->
                        scope.launch {
                            securityPreferences.saveReadingMode(newMode)
                        }
                    },
                    onDismiss = { showReadingModeSheet = false }
                )
            }

            if (fragmentActivity != null) {
                val fragmentManager = fragmentActivity.supportFragmentManager
                // Guard against duplicate commits: AndroidView.update fires on every recomposition,
                // but fragment transactions are asynchronous — findFragmentByTag returns null until
                // the commit is processed, so without this flag a second commit could sneak in.
                val committed = remember(containerId) { booleanArrayOf(false) }

                LaunchedEffect(isSearchActive) {
                    if (!fragmentManager.isDestroyed) {
                        val fragment = fragmentManager.findFragmentByTag(fragmentTag) as? InkPdfViewerFragment
                        fragment?.setSearchActive(isSearchActive)
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FragmentContainerView(ctx).apply {
                            id = containerId
                            if (isDarkTheme && preserveImages) {
                                val matrix = ColorMatrix(floatArrayOf(
                                    -0.574f, 1.43f, 0.144f, 0f, 0f,
                                    0.426f, 0.43f, 0.144f, 0f, 0f,
                                    0.426f, 1.43f, -0.856f, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
                                    colorFilter = ColorMatrixColorFilter(matrix)
                                })
                            }
                        }
                    },
                    update = { view ->
                        when (readingMode) {
                            ReadingMode.SEPIA -> {
                                val sepiaMatrix = ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            0.984f, 0f, 0f, 0f, 0f,
                                            0f, 0.941f, 0f, 0f, 0f,
                                            0f, 0f, 0.851f, 0f, 0f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                }
                                val paint = Paint().apply {
                                    colorFilter = ColorMatrixColorFilter(sepiaMatrix)
                                }
                                view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                            }
                            ReadingMode.E_INK -> {
                                val eInkMatrix = ColorMatrix().apply {
                                    setSaturation(0f)
                                }
                                val paint = Paint().apply {
                                    colorFilter = ColorMatrixColorFilter(eInkMatrix)
                                }
                                view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                            }
                            ReadingMode.DARK -> {
                                if (preserveImages) {
                                    val matrix = ColorMatrix(floatArrayOf(
                                        -0.574f, 1.43f, 0.144f, 0f, 0f,
                                        0.426f, 0.43f, 0.144f, 0f, 0f,
                                        0.426f, 1.43f, -0.856f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
                                        colorFilter = ColorMatrixColorFilter(matrix)
                                    })
                                } else {
                                    val invertMatrix = ColorMatrix().apply {
                                        set(
                                            floatArrayOf(
                                                -1f, 0f, 0f, 0f, 255f,
                                                0f, -1f, 0f, 0f, 255f,
                                                0f, 0f, -1f, 0f, 255f,
                                                0f, 0f, 0f, 1f, 0f
                                            )
                                        )
                                    }
                                    val paint = Paint().apply {
                                        colorFilter = ColorMatrixColorFilter(invertMatrix)
                                    }
                                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                                }
                            }
                            else -> {
                                if (isDarkTheme && preserveImages) {
                                    val matrix = ColorMatrix(floatArrayOf(
                                        -0.574f, 1.43f, 0.144f, 0f, 0f,
                                        0.426f, 0.43f, 0.144f, 0f, 0f,
                                        0.426f, 1.43f, -0.856f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
                                        colorFilter = ColorMatrixColorFilter(matrix)
                                    })
                                } else {
                                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                                }
                            }
                        }

                        if (!fragmentManager.isStateSaved && !fragmentManager.isDestroyed) {
                            val existing = fragmentManager.findFragmentByTag(fragmentTag) as? InkPdfViewerFragment
                            if (existing == null && !committed[0]) {
                                committed[0] = true
                                val fragment = InkPdfViewerFragment()
                                fragmentManager.commit(allowStateLoss = true) {
                                    setReorderingAllowed(true)
                                    replace(containerId, fragment, fragmentTag)
                                    runOnCommit {
                                        if (!fragmentManager.isDestroyed && fragment.isAdded && !fragment.isDetached) {
                                            try {
                                                fragment.documentUri = uri
                                                fragment.setSearchActive(isSearchActive)
                                                fragment.onImmersiveModeRequest = { enter ->
                                                    topBarVisible = !enter
                                                }
                                                fragment.onSearchModeChanged = { active ->
                                                    if (isSearchActive != active) {
                                                        isSearchActive = active
                                                    }
                                                }

                                                val database = AppDatabase.getDatabase(context)
                                                val dao = database.readingProgressDao()

                                                fragment.onDocumentLoaded = { totalPages ->
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val progress = dao.getProgress(uriHash)
                                                        if (progress != null && progress.lastViewedPage > 0) {
                                                            withContext(Dispatchers.Main) {
                                                                val result = snackbarHostState.showSnackbar(
                                                                    message = "Resume from page ${progress.lastViewedPage + 1}?",
                                                                    actionLabel = "Resume",
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                    fragment.scrollToPage(progress.lastViewedPage)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                fragment.onPageChanged = { page, totalPages ->
                                                    currentPageIndex = page
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        dao.saveProgress(
                                                            ReadingProgressEntity(
                                                                documentUriHash = uriHash,
                                                                lastViewedPage = page,
                                                                totalPages = totalPages
                                                            )
                                                        )
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("PdfViewerScreen", "Failed to setup fragment in runOnCommit", e)
                                            }
                                        }
                                    }
                                }
                            } else if (existing != null && existing.isAdded && !existing.isDetached) {
                                try {
                                    if (existing.documentUri != uri) {
                                        existing.documentUri = uri
                                    }
                                    // Sync search state whenever the Compose state changes.
                                    existing.setSearchActive(isSearchActive)
                                    // Ensure the lambdas are always up-to-date after recomposition.
                                    existing.onImmersiveModeRequest = { enter ->
                                        topBarVisible = !enter
                                    }
                                    existing.onSearchModeChanged = { active ->
                                        if (isSearchActive != active) {
                                            isSearchActive = active
                                        }
                                    }

                                    val database = AppDatabase.getDatabase(context)
                                    val dao = database.readingProgressDao()

                                    existing.onDocumentLoaded = { totalPages ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val progress = dao.getProgress(uriHash)
                                            if (progress != null && progress.lastViewedPage > 0) {
                                                withContext(Dispatchers.Main) {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Resume from page ${progress.lastViewedPage + 1}?",
                                                        actionLabel = "Resume",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        existing.scrollToPage(progress.lastViewedPage)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    existing.onPageChanged = { page, totalPages ->
                                        currentPageIndex = page
                                        coroutineScope.launch(Dispatchers.IO) {
                                            dao.saveProgress(
                                                ReadingProgressEntity(
                                                    documentUriHash = uriHash,
                                                    lastViewedPage = page,
                                                    totalPages = totalPages
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("PdfViewerScreen", "Failed to update existing fragment", e)
                                }
                            }
                        }
                    }
                )


                AnimatedVisibility(
                    visible = ttsBarVisible,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val isPlaying by ttsController.isPlaying.collectAsState()
                    val currentPage by ttsController.currentPage.collectAsState()
                    val totalPages by ttsController.totalPages.collectAsState()
                    val rate by ttsController.playbackRate.collectAsState()

                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { ttsController.previousPage() }) {
                                    Icon(Icons.Default.SkipPrevious, "Previous Page")
                                }
                                IconButton(onClick = {
                                    if (isPlaying) ttsController.pause() else ttsController.play(uri.toString(), currentPage)
                                }) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        if (isPlaying) "Pause" else "Play"
                                    )
                                }
                                IconButton(onClick = { ttsController.stop() }) {
                                    Icon(Icons.Default.Stop, "Stop")
                                }
                                IconButton(onClick = { ttsController.nextPage() }) {
                                    Icon(Icons.Default.SkipNext, "Next Page")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Speed: ${"%.1f".format(rate)}x", modifier = Modifier.padding(end = 8.dp))
                                Slider(
                                    value = rate,
                                    onValueChange = { ttsController.setRate(it) },
                                    valueRange = 0.5f..2.0f,
                                    steps = 14
                                )
                            }
                            Text(
                                "Page: $currentPage / ${if (totalPages > 0) totalPages else "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                DisposableEffect(containerId, fragmentManager) {
                    onDispose {
                        if (!fragmentManager.isDestroyed) {
                            val fragment = fragmentManager.findFragmentByTag(fragmentTag)
                            if (fragment != null) {
                                // Clear the lambdas to avoid retaining a stale Compose closure.
                                (fragment as? InkPdfViewerFragment)?.let { inkFrag ->
                                    inkFrag.onImmersiveModeRequest = null
                                    inkFrag.onSearchModeChanged = null
                                    inkFrag.onDocumentLoaded = null
                                    inkFrag.onPageChanged = null
                                }
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

            // Draw overview dialog on top of the AndroidView
            if (showPageOverview) {
                var currentPage by remember { mutableStateOf(0) }

                LaunchedEffect(Unit) {
                    fragmentActivity?.supportFragmentManager?.findFragmentByTag(fragmentTag)?.let { frag ->
                        currentPage = (frag as? InkPdfViewerFragment)?.getCurrentPage() ?: 0
                    }
                }

                PdfPageOverviewDialog(
                    uri = uri,
                    currentPage = currentPage,
                    onDismiss = { showPageOverview = false },
                    onPageSelected = { pageIndex ->
                        showPageOverview = false
                        fragmentActivity?.supportFragmentManager?.findFragmentByTag(fragmentTag)?.let { frag ->
                            (frag as? InkPdfViewerFragment)?.gotoPage(pageIndex)
                        }
                    }
                )
            }
        }

        if (showAddBookmarkDialog && currentPageIndex != null) {
            var note by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAddBookmarkDialog = false },
                title = { Text("Add Bookmark") },
                text = {
                    Column {
                        Text("Page ${(currentPageIndex ?: 0) + 1}")
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note (Optional)") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        effectiveViewModel?.addBookmark(currentPageIndex ?: 0, note.ifEmpty { null })
                        showAddBookmarkDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { showAddBookmarkDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showBookmarksSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBookmarksSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
                    if (bookmarks.isEmpty()) {
                        Text("No bookmarks yet.", modifier = Modifier.padding(top = 16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                            items(bookmarks) { bookmark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            fragmentActivity?.let {
                                                val frag = it.supportFragmentManager.findFragmentByTag(fragmentTag) as? InkPdfViewerFragment
                                                frag?.scrollToPage(bookmark.pageIndex)
                                                showBookmarksSheet = false
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Page ${bookmark.pageIndex + 1}", fontWeight = FontWeight.Bold)
                                        if (!bookmark.note.isNullOrEmpty()) {
                                            Text(bookmark.note, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    IconButton(onClick = { effectiveViewModel?.deleteBookmark(bookmark.pageIndex) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Bookmark")
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
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
