@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.max97k.pddf.feature.decrypt

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import android.print.PrintManager
import android.os.ParcelFileDescriptor
import android.print.PrintDocumentAdapter
import android.print.PrintAttributes
import android.os.Bundle
import android.os.CancellationSignal
import java.io.FileInputStream
import java.io.FileOutputStream
import com.max97k.pddf.feature.viewer.PdfViewerScreen

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.max97k.pddf.MainUiAction
import com.max97k.pddf.MainUiState
import com.max97k.pddf.MainViewModel
import com.max97k.pddf.R
import com.max97k.pddf.UiEffect
import com.max97k.pddf.feature.vault.BiometricHelper
import com.max97k.pddf.feature.vault.SavePasswordDialog
import com.max97k.pddf.feature.vault.SavedPasswordListDialog
import com.max97k.pddf.feature.viewer.PdfViewerDialog
import com.max97k.pddf.ui.components.AboutDialog
import com.max97k.pddf.ui.components.DocumentDetailsCard
import com.max97k.pddf.ui.components.EmptyStateCard
import com.max97k.pddf.ui.components.PasswordInputSection
import com.max97k.pddf.ui.components.PostDecryptActionSection
import com.max97k.pddf.feature.decrypt.ReEncryptPasswordDialog
import com.max97k.pddf.ui.components.PrivacyShieldOverlay
import com.max97k.pddf.ui.components.SelectedFilesCard
import com.max97k.pddf.ui.components.SettingsBottomSheet
import com.max97k.pddf.ui.components.WhatsNewDialog
import com.max97k.pddf.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PDFDecryptorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStampConfig by remember { mutableStateOf<com.max97k.pddf.domain.model.StampConfig?>(null) }
    val stampDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { destUri ->
            if (destUri != null && currentStampConfig != null) {
                viewModel.onAction(MainUiAction.StampPages(destUri, currentStampConfig!!))
            }
        }
    )

    val exportReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            val actualFormat = uiState.exportFormat
            viewModel.onAction(MainUiAction.ExportReportToUri(uri, actualFormat))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is UiEffect.LaunchCreateExportDocument -> {
                    exportReportLauncher.launch(effect.defaultFileName)
                }
                is UiEffect.ShowSnackbar -> {

                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = effect.message,
                            actionLabel = effect.actionLabel,
                            duration = effect.duration
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            effect.onAction?.invoke()
                        }
                    }
                }
                is UiEffect.ShowToast -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is UiEffect.LaunchCreateStampedDocument -> {
                    currentStampConfig = effect.config
                    stampDocumentLauncher.launch(effect.defaultFileName)
                }
                else -> { /* Other effects handled in UI branches */ }
            }
        }
    }

    PDFDecryptorScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        windowWidthSizeClass = windowWidthSizeClass,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PDFDecryptorScreen(
    uiState: MainUiState,
    onAction: (MainUiAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: MainViewModel? = null
) {

    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.previewPdfUri) {
        if (uiState.previewPdfUri != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
        } else {
            if (navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail) {
                navigator.navigateBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isAppPaused by remember { mutableStateOf(false) }
    var showExtractPagesDialog by remember { mutableStateOf(false) }

    val openPasswordVault: () -> Unit = {
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity != null) {
            val promptTitle = context.resources.getString(R.string.biometric_prompt_title)
            val promptSubtitle = context.resources.getString(R.string.biometric_prompt_subtitle)
            BiometricHelper.authenticate(
                activity = fragmentActivity,
                title = promptTitle,
                subtitle = promptSubtitle,
                onSuccess = { onAction(MainUiAction.SetPasswordListDialogVisible(true)) },
                onError = { errorCode, errString ->
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        android.widget.Toast.makeText(context, errString, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            onAction(MainUiAction.SetPasswordListDialogVisible(true))
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        isAppPaused = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isAppPaused = false
    }

    // Predictive Back Handling
    BackHandler(
        enabled = uiState.hasSelectedFiles ||
                uiState.showPasswordListDialog ||
                uiState.showSavePasswordDialog ||
                uiState.showAutoUnlockPasswordPrompt ||
                uiState.previewPdfUri != null ||
                showAboutDialog
    ) {
        when {
            showAboutDialog -> showAboutDialog = false
            uiState.previewPdfUri != null -> onAction(MainUiAction.SetPreviewPdfUri(null))
            uiState.showPasswordListDialog -> onAction(MainUiAction.SetPasswordListDialogVisible(false))
            uiState.showSavePasswordDialog -> onAction(MainUiAction.SetSavePasswordDialogVisible(false))
            uiState.showAutoUnlockPasswordPrompt -> onAction(MainUiAction.DismissAutoUnlockPrompt)
            uiState.hasSelectedFiles -> onAction(MainUiAction.ClearSelectedFiles)
        }
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
    }

    LaunchedEffect(versionName) {
        val shouldShow = withContext(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lastSeenVersion = sharedPrefs.getString("last_seen_version", null)
            if (lastSeenVersion != versionName) {
                sharedPrefs.edit().putString("last_seen_version", versionName).apply()
                true
            } else {
                false
            }
        }
        if (shouldShow) {
            showAboutDialog = true
        }
    }

    val isSecureModeActive = uiState.isSecureModeActive
    val activity = context as? Activity
    DisposableEffect(isSecureModeActive) {
        if (isSecureModeActive) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            activity?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
            activity?.setTaskDescription(
                ActivityManager.TaskDescription.Builder()
                    .setPrimaryColor(android.graphics.Color.BLACK)
                    .build()
            )
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            activity?.window?.setBackgroundDrawable(null)
            activity?.setTaskDescription(
                ActivityManager.TaskDescription.Builder()
                    .setPrimaryColor(0)
                    .build()
            )
        }
        onDispose {
            // Do NOT clear FLAG_SECURE on dispose — if the user enabled screenshot
            // protection, the flag must persist for the entire Activity lifetime.
            // It is only cleared when isSecureModeActive transitions to false above.
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uris = mutableListOf<Uri>()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                } ?: result.data?.data?.let { uris.add(it) }

                if (uris.isNotEmpty()) {
                    val flags = result.data?.flags ?: 0
                    val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (takeFlags != 0) {
                        uris.forEach { uri ->
                            FileUtils.takePersistableUriPermissionSafely(context, uri, takeFlags)
                        }
                    }
                    onAction(MainUiAction.SelectFiles(uris))
                }
            }
        }
    )

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val destUri = result.data?.data
                if (destUri != null) {
                    onAction(MainUiAction.DecryptToDirectory(destUri))
                }
            }
        }
    )

    val createZipDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                onAction(MainUiAction.DecryptToZip(uri))
            }
        }
    }

    var pendingAttachmentName by remember { mutableStateOf<String?>(null) }

    val createAttachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val destUri = result.data?.data
                val attName = pendingAttachmentName
                if (destUri != null && attName != null) {
                    onAction(MainUiAction.SaveAttachmentToUri(destUri, attName))
                }
                pendingAttachmentName = null
            }
        }
    )

    val saveAllAttachmentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val treeUri = result.data?.data
                if (treeUri != null) {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    onAction(MainUiAction.SaveAllAttachmentsToDirectory(treeUri))
                }
            }
        }
    )

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val destUri = result.data?.data
                if (destUri != null) {
                    onAction(MainUiAction.DecryptToUri(destUri))
                }
            }
        }
    )

    // Dedicated launcher for saving the already-decrypted preview PDF.
    // MUST NOT reuse createDocumentLauncher — that one triggers a full re-decryption
    // with uiState.password (which is "" when opened from external sources like Gmail),
    // resulting in a 0-byte output file.
    var pageRangeToExtract by rememberSaveable { mutableStateOf<String?>(null) }

    val extractPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val destUri = result.data?.data
                val sourceUri = uiState.previewPdfUri
                val range = pageRangeToExtract
                if (destUri != null && sourceUri != null && range != null && viewModel != null) {
                    scope.launch {
                        val extractResult = viewModel.extractPages(sourceUri.toString(), destUri.toString(), range)
                        if (extractResult.isSuccess) {
                            android.widget.Toast.makeText(context, "Pages extracted successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Extraction failed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                pageRangeToExtract = null
            }
        }
    )

    val savePreviewDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val destUri = result.data?.data
                val sourceUri = uiState.previewPdfUri
                if (destUri != null && sourceUri != null) {
                    onAction(MainUiAction.CopyUriStream(sourceUri, destUri))
                }
            }
        }
    )

    val updateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { _ -> }
    )

    val exportVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val destUri = result.data?.data
                if (destUri != null) {
                    onAction(MainUiAction.ExportVault(destUri))
                }
            }
        }
    )

    val importVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val destUri = result.data?.data
                if (destUri != null) {
                    onAction(MainUiAction.ImportVault(destUri))
                }
            }
        }
    )

    LaunchedEffect(uiState.appUpdateStatus) {
        if (uiState.appUpdateStatus is com.max97k.pddf.domain.model.AppUpdateStatus.Downloaded) {
            val result = snackbarHostState.showSnackbar(
                message = context.resources.getString(R.string.status_update_downloaded),
                actionLabel = context.resources.getString(R.string.btn_restart_update),
                duration = androidx.compose.material3.SnackbarDuration.Indefinite
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                onAction(MainUiAction.CompleteAppUpdate)
            }
        }
    }

    var isDragging by remember { mutableStateOf(false) }

    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {}
            override fun onEntered(event: DragAndDropEvent) { isDragging = true }
            override fun onExited(event: DragAndDropEvent) { isDragging = false }
            override fun onEnded(event: DragAndDropEvent) { isDragging = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                val dragEvent = event.toAndroidDragEvent()
                val clipData = dragEvent.clipData ?: return false
                val act = context as? Activity
                act?.requestDragAndDropPermissions(dragEvent)
                val uris = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) {
                        uris.add(uri)
                    }
                }
                if (uris.isNotEmpty()) {
                    onAction(MainUiAction.SelectFiles(uris))
                    return true
                }
                return false
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        when (uiState.statusLevel) {
            com.max97k.pddf.StatusLevel.ERROR -> {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
            com.max97k.pddf.StatusLevel.SUCCESS -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            else -> Unit
        }
    }

    val isExpandedLayout = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when {
                        event.isCtrlPressed && event.key == Key.O -> {
                            onAction(MainUiAction.RequestFilePicker)
                            true
                        }
                        event.isCtrlPressed && event.key == Key.P -> {
                            if (uiState.previewPdfUri != null) {
                                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                                val jobName = "PDDF_Document"
                                printManager?.print(jobName, object : PrintDocumentAdapter() {
                                    override fun onLayout(
                                        oldAttributes: PrintAttributes?,
                                        newAttributes: PrintAttributes?,
                                        cancellationSignal: CancellationSignal?,
                                        callback: LayoutResultCallback?,
                                        extras: Bundle?
                                    ) {
                                        if (cancellationSignal?.isCanceled == true) {
                                            callback?.onLayoutCancelled()
                                            return
                                        }
                                        val info = android.print.PrintDocumentInfo.Builder(jobName)
                                            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                            .build()
                                        callback?.onLayoutFinished(info, true)
                                    }

                                    override fun onWrite(
                                        pages: Array<out android.print.PageRange>?,
                                        destination: ParcelFileDescriptor?,
                                        cancellationSignal: CancellationSignal?,
                                        callback: WriteResultCallback?
                                    ) {
                                        if (destination == null || cancellationSignal?.isCanceled == true) {
                                            callback?.onWriteCancelled()
                                            return
                                        }
                                        try {
                                            context.contentResolver.openInputStream(uiState.previewPdfUri)?.use { input ->
                                                FileOutputStream(destination.fileDescriptor).use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                                        } catch (e: Exception) {
                                            callback?.onWriteFailed(e.message)
                                        }
                                    }
                                }, null)
                                true
                            } else false
                        }
                        event.key == Key.Escape -> {
                            if (uiState.previewPdfUri != null) {
                                onAction(MainUiAction.SetPreviewPdfUri(null))
                                true
                            } else if (uiState.showAutoUnlockPasswordPrompt) {
                                onAction(MainUiAction.DismissAutoUnlockPrompt)
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        listPane = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            val dragEvent = event.toAndroidDragEvent()
                            val clipDescription = dragEvent.clipDescription
                            clipDescription != null && clipDescription.hasMimeType("application/pdf")
                        },
                        target = dragAndDropTarget
                    )
                    .background(if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
            ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header Row — App Title & Icon | Action Icons (Saved Passwords, Settings, About)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = openPasswordVault) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.content_desc_saved_passwords),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.content_desc_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.content_desc_about),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isExpandedLayout) {
                // Adaptive Dual-Pane Layout for Tablets & Expanded Screens
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Pane: Document Selection & Details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                }
                                openDocumentLauncher.launch(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_select_pdfs))
                        }

                        AnimatedContent(
                            targetState = uiState.hasSelectedFiles,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) + expandVertically()).togetherWith(
                                    fadeOut(animationSpec = tween(90)) + shrinkVertically()
                                )
                            },
                            label = "LeftPaneContentAnimation"
                        ) { hasFiles ->
                            if (hasFiles) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SelectedFilesCard(
                                        fileNames = uiState.selectedFileNames,
                                        fileCount = uiState.fileCount,
                                        onClear = { onAction(MainUiAction.ClearSelectedFiles) }
                                    )

                                    AnimatedVisibility(
                                        visible = uiState.selectedMetadata != null,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            uiState.selectedMetadata?.let { metadata ->
                                                DocumentDetailsCard(
                                                    metadata = metadata,
                                                    decryptionDurationMs = uiState.lastDecryptionDurationMs,
                                                    onPreview = if (uiState.activePreviewUri != null) {
                                                        { onAction(MainUiAction.SetPreviewPdfUri(uiState.activePreviewUri)) }
                                                    } else null,
                                                    onReversePages = if (uiState.activePreviewUri != null) {
                                                        { onAction(MainUiAction.ReversePages(uiState.activePreviewUri!!)) }
                                                    } else null,
                                                    onStamp = { onAction(MainUiAction.SetStampDialogVisible(true)) },
                                                    onSaveAttachment = { attachmentName ->
                                                        val attachment = metadata.attachments.find { it.name == attachmentName }
                                                        if (attachment != null) {
                                                            pendingAttachmentName = attachment.name
                                                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                                type = attachment.mimeType
                                                                putExtra(Intent.EXTRA_TITLE, attachment.name)
                                                            }
                                                            createAttachmentLauncher.launch(intent)
                                                        }
                                                    },
                                                    onSaveAllAttachments = {
                                                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                                        saveAllAttachmentsLauncher.launch(intent)
                                                    },
                                                    uriHash = uiState.selectedUris.firstOrNull()?.toString()?.hashCode()
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    EmptyStateCard()
                                }
                            }
                        }
                    }

                    // Right Pane: Password Input & Decrypt Actions
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.hasSelectedFiles) {
                            PasswordInputSection(
                                password = uiState.password,
                                passwordVisible = uiState.passwordVisible,
                                onPasswordChange = { onAction(MainUiAction.UpdatePassword(it)) },
                                onTogglePasswordVisible = { onAction(MainUiAction.TogglePasswordVisibility) },
                                onOpenPasswordList = openPasswordVault,
                                onOpenSavePassword = { onAction(MainUiAction.SetSavePasswordDialogVisible(true)) }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (uiState.isProcessing && !uiState.batchState.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                            } else if (!uiState.isProcessing) {
                                DecryptActionButtons(
                                    uiState = uiState,
                                    onAction = onAction,
                                    onSaveAsSingle = {
                                        val fileName = uiState.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_TITLE, fileName)
                                        }
                                        createDocumentLauncher.launch(intent)
                                    },
                                    onSaveAsBatch = {
                                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                        openDocumentTreeLauncher.launch(intent)
                                    },
                                    onSaveAsZip = {
                                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_TITLE, "decrypted_batch.zip")
                                        }
                                        createZipDocumentLauncher.launch(intent)
                                    }
                                )
                            }

                            if (uiState.statusMessage != null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                StatusBannerCard(
                                    statusMessage = uiState.statusMessage!!,
                                    statusLevel = uiState.statusLevel
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                PostDecryptActionSection(
                                    previewUri = uiState.activePreviewUri,
                                    lastDecryptedUri = uiState.lastDecryptedUri,
                                    onPreviewClick = {
                                        uiState.activePreviewUri?.let { previewUri ->
                                            onAction(MainUiAction.SetPreviewPdfUri(previewUri))
                                        }
                                    },
                                    onProtectClick = {
                                        uiState.lastDecryptedUri?.let { uri ->
                                            onAction(MainUiAction.RequestReEncrypt(uri))
                                        }
                                    }
                                )

                                if (uiState.batchState.failedFiles.isNotEmpty() && !uiState.batchState.isProcessing) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { onAction(MainUiAction.SetBatchFailureDialogVisible(true)) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.btn_view_details_retry))
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.title_password_actions),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.desc_password_actions),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Compact / Medium Layout (Single-Column)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }
                            openDocumentLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_select_pdfs))
                    }

                    AnimatedContent(
                        targetState = uiState.hasSelectedFiles,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220, delayMillis = 90)) + expandVertically()).togetherWith(
                                fadeOut(animationSpec = tween(90)) + shrinkVertically()
                            )
                        },
                        label = "CompactContentAnimation"
                    ) { hasFiles ->
                        if (hasFiles) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(16.dp))
                                SelectedFilesCard(
                                    fileNames = uiState.selectedFileNames,
                                    fileCount = uiState.fileCount,
                                    onClear = { onAction(MainUiAction.ClearSelectedFiles) }
                                )

                                AnimatedVisibility(
                                    visible = uiState.selectedMetadata != null,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        uiState.selectedMetadata?.let { metadata ->
                                            DocumentDetailsCard(
                                                metadata = metadata,
                                                decryptionDurationMs = uiState.lastDecryptionDurationMs,
                                                onPreview = if (uiState.activePreviewUri != null) {
                                                    { onAction(MainUiAction.SetPreviewPdfUri(uiState.activePreviewUri)) }
                                                } else null,
                                                onReversePages = if (uiState.activePreviewUri != null) {
                                                    { onAction(MainUiAction.ReversePages(uiState.activePreviewUri!!)) }
                                                } else null,
                                                onStamp = { onAction(MainUiAction.SetStampDialogVisible(true)) },
                                                onSaveAttachment = { attachmentName ->
                                                    val attachment = metadata.attachments.find { it.name == attachmentName }
                                                    if (attachment != null) {
                                                        pendingAttachmentName = attachment.name
                                                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                            addCategory(Intent.CATEGORY_OPENABLE)
                                                            type = attachment.mimeType
                                                            putExtra(Intent.EXTRA_TITLE, attachment.name)
                                                        }
                                                        createAttachmentLauncher.launch(intent)
                                                    }
                                                },
                                                onSaveAllAttachments = {
                                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                                    saveAllAttachmentsLauncher.launch(intent)
                                                },
                                                uriHash = uiState.selectedUris.firstOrNull()?.toString()?.hashCode()
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(16.dp))
                                EmptyStateCard()
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.hasSelectedFiles,
                        enter = fadeIn(animationSpec = tween(220, delayMillis = 90)) + expandVertically(),
                        exit = fadeOut(animationSpec = tween(90)) + shrinkVertically()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(12.dp))

                            PasswordInputSection(
                                password = uiState.password,
                                passwordVisible = uiState.passwordVisible,
                                onPasswordChange = { onAction(MainUiAction.UpdatePassword(it)) },
                                onTogglePasswordVisible = { onAction(MainUiAction.TogglePasswordVisibility) },
                                onOpenPasswordList = openPasswordVault,
                                onOpenSavePassword = { onAction(MainUiAction.SetSavePasswordDialogVisible(true)) }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (uiState.isProcessing && !uiState.batchState.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                            } else if (!uiState.isProcessing) {
                                DecryptActionButtons(
                                    uiState = uiState,
                                    onAction = onAction,
                                    onSaveAsSingle = {
                                        val fileName = uiState.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_TITLE, fileName)
                                        }
                                        createDocumentLauncher.launch(intent)
                                    },
                                    onSaveAsBatch = {
                                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                        openDocumentTreeLauncher.launch(intent)
                                    },
                                    onSaveAsZip = {
                                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_TITLE, "decrypted_batch.zip")
                                        }
                                        createZipDocumentLauncher.launch(intent)
                                    }
                                )
                            }

                            if (uiState.statusMessage != null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                StatusBannerCard(
                                    statusMessage = uiState.statusMessage!!,
                                    statusLevel = uiState.statusLevel
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                PostDecryptActionSection(
                                    previewUri = uiState.activePreviewUri,
                                    lastDecryptedUri = uiState.lastDecryptedUri,
                                    onPreviewClick = {
                                        uiState.activePreviewUri?.let { previewUri ->
                                            onAction(MainUiAction.SetPreviewPdfUri(previewUri))
                                        }
                                    },
                                    onProtectClick = {
                                        uiState.lastDecryptedUri?.let { uri ->
                                            onAction(MainUiAction.RequestReEncrypt(uri))
                                        }
                                    }
                                )

                                if (uiState.batchState.failedFiles.isNotEmpty() && !uiState.batchState.isProcessing) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { onAction(MainUiAction.SetBatchFailureDialogVisible(true)) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.btn_view_details_retry))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        PrivacyShieldOverlay(
            isVisible = uiState.screenshotProtectionEnabled && isAppPaused
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            currentTheme = uiState.themeMode,
            currentDensity = uiState.densityMode,
            currentBatchNotificationPreference = uiState.batchNotificationPreference,
            screenshotProtectionEnabled = uiState.screenshotProtectionEnabled,
            currentAutoLockDuration = uiState.autoLockDuration,
            onThemeSelected = { mode -> onAction(MainUiAction.SetTheme(mode)) },
            onDensitySelected = { mode -> onAction(MainUiAction.SetDensityMode(mode)) },
            onBatchNotificationSelected = { pref -> onAction(MainUiAction.SetBatchNotificationPreference(pref)) },
            onScreenshotProtectionToggled = { enabled ->
                onAction(MainUiAction.SetScreenshotProtection(enabled))
            },
            onExportCsv = { onAction(MainUiAction.RequestExportCsv) },
            onExportJson = { onAction(MainUiAction.RequestExportJson) },
            onAutoLockDurationSelected = { duration ->
                onAction(MainUiAction.SetAutoLockDuration(duration))
            },
            onDismiss = { showSettingsSheet = false }
        )
    }    },
        detailPane = {
            if (uiState.previewPdfUri != null) {
                val shareFileTitle = stringResource(R.string.btn_share_file)
                PdfViewerScreen(
                    uri = uiState.previewPdfUri,
                    modifier = Modifier.fillMaxSize(),
                    title = uiState.selectedFileNames.firstOrNull() ?: "",
                    onClose = { onAction(MainUiAction.SetPreviewPdfUri(null)) },
                    onRotateAll = { degrees -> onAction(MainUiAction.RotatePreview(degrees)) },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uiState.previewPdfUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, shareFileTitle))
                        } catch (_: Exception) {}
                    },
                    onSaveAs = {
                        val fileName = uiState.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_TITLE, fileName)
                        }
                        savePreviewDocumentLauncher.launch(intent)
                    },
                    onAddWatermark = { text ->
                        onAction(MainUiAction.AddWatermark(uiState.previewPdfUri, text))
                    },
                    onExtract = { showExtractPagesDialog = true }
                )
                if (showExtractPagesDialog && viewModel != null) {
                    val totalPages = uiState.selectedMetadata?.pageCount ?: 0
                    com.max97k.pddf.feature.viewer.PdfExtractPagesDialog(
                        totalPages = totalPages,
                        onDismiss = { showExtractPagesDialog = false },
                        onSaveAs = { pageRange ->
                            showExtractPagesDialog = false
                            pageRangeToExtract = pageRange
                            val fileName = "extracted_${uiState.selectedFileNames.firstOrNull() ?: "document"}.pdf"
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_TITLE, fileName)
                            }
                            extractPdfDocumentLauncher.launch(intent)
                        },
                        onShare = { pageRange ->
                            showExtractPagesDialog = false
                            scope.launch {
                                try {
                                    val tempUri = viewModel.createTempFile("extracted_", ".pdf")
                                    val extractResult = viewModel.extractPages(
                                        sourceUri = uiState.previewPdfUri.toString(),
                                        destUri = tempUri,
                                        pageRange = pageRange
                                    )
                                    if (extractResult.isSuccess) {
                                        // Convert raw cache path to FileProvider content:// URI before sharing
                                        val shareableUri = viewModel.getShareableUri(tempUri)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(shareableUri))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, shareFileTitle))
                                    } else {
                                        android.widget.Toast.makeText(context, "Extraction failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Extraction error", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    )



    if (uiState.showReEncryptDialog) {
        ReEncryptPasswordDialog(
            onConfirm = { chars -> onAction(MainUiAction.SubmitReEncrypt(chars)) },
            onDismiss = { onAction(MainUiAction.DismissReEncryptDialog) }
        )
    }

    if (uiState.showSavePasswordDialog) {
        SavePasswordDialog(
            currentPassword = uiState.password,
            onDismiss = { onAction(MainUiAction.SetSavePasswordDialogVisible(false)) },
            onSave = { name, pass ->
                onAction(MainUiAction.SavePassword(name, pass))
            }
        )
    }

    if (uiState.showPasswordListDialog) {
        SavedPasswordListDialog(
            savedPasswords = uiState.savedPasswords,
            densityMode = uiState.densityMode,
            onDismiss = { onAction(MainUiAction.SetPasswordListDialogVisible(false)) },
            onSelectPassword = { selectedPass ->
                onAction(MainUiAction.SelectSavedPassword(selectedPass))
            },
            onExportClick = {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "vault.json")
                }
                exportVaultLauncher.launch(intent)
            },
            onImportClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "application/octet-stream"))
                }
                importVaultLauncher.launch(intent)
            },
            onDeletePassword = { savedPass ->
                onAction(MainUiAction.DeletePassword(savedPass))
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getString(R.string.msg_password_deleted),
                        actionLabel = context.resources.getString(R.string.btn_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onAction(MainUiAction.RestorePassword(savedPass))
                    }
                }
            }
        )
    }

    if (uiState.batchState.isProcessing) {
        BatchProgressDialog(
            progress = uiState.batchState.progress,
            total = uiState.batchState.total,
            onCancel = { onAction(MainUiAction.CancelBatch) }
        )
    }

    if (uiState.showBatchFailureDialog) {
        BatchFailureDetailsDialog(
            failedFiles = uiState.batchState.failedFiles,
            onRetry = { selectedUris, alternativePassword ->
                onAction(MainUiAction.RetryFailedBatchItems(selectedUris, alternativePassword))
            },
            onDismiss = { onAction(MainUiAction.SetBatchFailureDialogVisible(false)) }
        )
    }

    if (uiState.showAutoUnlockPasswordPrompt) {
        AutoUnlockPasswordDialog(
            fileName = uiState.autoUnlockFileName,
            errorMessage = uiState.autoUnlockErrorMessage,
            onUnlock = { pass, rememberPass ->
                onAction(MainUiAction.UnlockWithManualPassword(pass, rememberPass))
            },
            onDismiss = { onAction(MainUiAction.DismissAutoUnlockPrompt) }
        )
    }



    if (uiState.showStampDialog) {
        com.max97k.pddf.ui.components.StampConfigDialog(
            onDismiss = { onAction(MainUiAction.SetStampDialogVisible(false)) },
            onConfirm = { config ->
                onAction(MainUiAction.SetStampDialogVisible(false))
                onAction(MainUiAction.RequestStampSaveAsPicker(config))
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            versionName = versionName,
            appUpdateStatus = uiState.appUpdateStatus,
            onCheckForUpdates = { onAction(MainUiAction.CheckForUpdates) },
            onStartUpdate = { onAction(MainUiAction.StartFlexibleUpdate(updateLauncher)) },
            onCompleteUpdate = { onAction(MainUiAction.CompleteAppUpdate) },
            onDismiss = {
                showAboutDialog = false
                if (uiState.appUpdateStatus is com.max97k.pddf.domain.model.AppUpdateStatus.Failed ||
                    uiState.appUpdateStatus is com.max97k.pddf.domain.model.AppUpdateStatus.UpToDate
                ) {
                    onAction(MainUiAction.ResetAppUpdateStatus)
                }
            }
        )
    }
}

@Composable
private fun StatusBannerCard(
    statusMessage: String,
    statusLevel: com.max97k.pddf.StatusLevel,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, icon) = when (statusLevel) {
        com.max97k.pddf.StatusLevel.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Error
        )
        com.max97k.pddf.StatusLevel.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning
        )
        else -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.CheckCircle
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = statusMessage,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DecryptActionButtons(
    uiState: MainUiState,
    onAction: (MainUiAction) -> Unit,
    onSaveAsSingle: () -> Unit,
    onSaveAsBatch: () -> Unit,
    onSaveAsZip: () -> Unit
) {
    val view = LocalView.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.isSingleFile) {
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    uiState.selectedUris.firstOrNull()?.let { uri ->
                        onAction(MainUiAction.DecryptAndPreview(uri, uiState.password))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = uiState.password.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_decrypt_and_preview),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onAction(MainUiAction.DecryptInPlace)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                enabled = uiState.password.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(
                    1.dp,
                    if (uiState.password.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.btn_overwrite),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }

            FilledTonalButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    if (uiState.isBatch) {
                        onSaveAsBatch()
                    } else {
                        onSaveAsSingle()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                enabled = uiState.password.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.btn_save_as),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (uiState.isBatch) {
                FilledTonalButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onSaveAsZip()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = uiState.password.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_save_as_zip),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun PDFDecryptorRoute(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    PDFDecryptorScreen(
        viewModel = viewModel,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        windowWidthSizeClass = windowWidthSizeClass
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PDFDecryptorScreenPreview() {
    com.max97k.pddf.ui.theme.MyApplicationTheme {
        PDFDecryptorScreen(
            uiState = com.max97k.pddf.MainUiState(),
            onAction = {}
        )
    }
}
