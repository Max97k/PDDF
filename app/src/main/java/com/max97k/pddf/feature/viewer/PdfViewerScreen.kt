package com.max97k.pddf.feature.viewer

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Print

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.max97k.pddf.R

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
    val view = LocalView.current
    val fragmentActivity = remember(context) { context.findFragmentActivity() }
    val insetsController = remember(fragmentActivity, view) {
        fragmentActivity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view)
        }
    }

    // true  → TopAppBar is visible (normal reading)
    // false → TopAppBar is hidden (immersive full-screen reading)
    var topBarVisible by rememberSaveable { mutableStateOf(true) }

    // Tracks whether the native PDF search bar is currently active.
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    var showReadingModeSheet by rememberSaveable { mutableStateOf(false) }

    val preferencesEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PreferencesEntryPoint::class.java)
    }
    val securityPreferences = preferencesEntryPoint.securityPreferences()
    val readingMode by securityPreferences.readingMode.collectAsState()
    val scope = rememberCoroutineScope()

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

    Scaffold(
        modifier = modifier,
        contentWindowInsets = contentInsets,
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
                        // Search toggle — open search on first tap, close on second tap.
                        IconButton(onClick = {
                            showReadingModeSheet = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = stringResource(R.string.btn_reading_mode)
                            )
                        }

                        IconButton(onClick = {
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

                        IconButton(onClick = {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                            printManager?.let {
                                val fileName = com.max97k.pddf.util.FileUtils.getFileName(context, uri)
                                val adapter = com.max97k.pddf.print.PdfPrintDocumentAdapter(context, uri, fileName)
                                val builder = android.print.PrintAttributes.Builder()
                                it.print(fileName, adapter, builder.build())
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = stringResource(R.string.btn_print)
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
            val containerId = rememberSaveable { View.generateViewId() }
            val fragmentTag = remember(containerId) { "pdf_viewer_$containerId" }

            if (showReadingModeSheet) {
                ReadingModeBottomSheet(
                    currentMode = readingMode,
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
                            else -> {
                                view.setLayerType(View.LAYER_TYPE_NONE, null)
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
                                } catch (e: Exception) {
                                    Log.e("PdfViewerScreen", "Failed to update existing fragment", e)
                                }
                            }
                        }
                    }
                )

                DisposableEffect(containerId, fragmentManager) {
                    onDispose {
                        if (!fragmentManager.isDestroyed) {
                            val fragment = fragmentManager.findFragmentByTag(fragmentTag)
                            if (fragment != null) {
                                // Clear the lambdas to avoid retaining a stale Compose closure.
                                (fragment as? InkPdfViewerFragment)?.let { inkFrag ->
                                    inkFrag.onImmersiveModeRequest = null
                                    inkFrag.onSearchModeChanged = null
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
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
