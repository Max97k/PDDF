@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.feature.decrypt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainUiAction
import com.example.MainUiState
import com.example.MainViewModel
import com.example.R
import com.example.UiEffect
import com.example.feature.vault.BiometricHelper
import com.example.feature.vault.SavePasswordDialog
import com.example.feature.vault.SavedPasswordListDialog
import com.example.feature.viewer.PdfViewerDialog
import com.example.ui.components.DocumentDetailsCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PasswordInputSection
import com.example.ui.components.SelectedFilesCard
import com.example.ui.components.ThemeDropdownMenu
import com.example.ui.components.WhatsNewDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
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
                else -> { /* Other effects handled in UI branches */ }
            }
        }
    }

    PDFDecryptorScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        windowWidthSizeClass = windowWidthSizeClass
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PDFDecryptorScreen(
    uiState: MainUiState,
    onAction: (MainUiAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var showWhatsNewDialog by remember { mutableStateOf(false) }

    // Predictive Back Handling
    BackHandler(
        enabled = uiState.hasSelectedFiles ||
                uiState.showPasswordListDialog ||
                uiState.showSavePasswordDialog ||
                uiState.showAutoUnlockPasswordPrompt ||
                uiState.previewPdfUri != null ||
                showWhatsNewDialog
    ) {
        when {
            showWhatsNewDialog -> showWhatsNewDialog = false
            uiState.previewPdfUri != null -> onAction(MainUiAction.SetPreviewPdfUri(null))
            uiState.showPasswordListDialog -> onAction(MainUiAction.SetPasswordListDialogVisible(false))
            uiState.showSavePasswordDialog -> onAction(MainUiAction.SetSavePasswordDialogVisible(false))
            uiState.showAutoUnlockPasswordPrompt -> onAction(MainUiAction.DismissAutoUnlockPrompt)
            uiState.hasSelectedFiles -> onAction(MainUiAction.ClearSelectedFiles(context))
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
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastSeenVersion = sharedPrefs.getString("last_seen_version", null)
        if (lastSeenVersion != versionName) {
            showWhatsNewDialog = true
            sharedPrefs.edit().putString("last_seen_version", versionName).apply()
        }
    }

    val isSecureModeActive = uiState.isSecureModeActive
    val activity = context as? Activity
    DisposableEffect(isSecureModeActive) {
        if (isSecureModeActive) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
                            try {
                                @Suppress("WrongConstant")
                                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    onAction(MainUiAction.SelectFiles(context, uris))
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
                    onAction(MainUiAction.DecryptToDirectory(context, destUri))
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
                    onAction(MainUiAction.DecryptToUri(context, destUri))
                }
            }
        }
    )

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    act?.requestDragAndDropPermissions(dragEvent)
                }
                val uris = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) {
                        uris.add(uri)
                    }
                }
                if (uris.isNotEmpty()) {
                    onAction(MainUiAction.SelectFiles(context, uris))
                    return true
                }
                return false
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            if (msg.startsWith("Error") || msg.startsWith("Failed") || msg.contains("❌")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            } else if (msg.contains("✅") || msg.contains("Decrypted & Saved")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
    }

    val isExpandedLayout = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    Box(
        modifier = modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    val dragEvent = event.toAndroidDragEvent()
                    val clipDescription = dragEvent.clipDescription
                    clipDescription != null && clipDescription.hasMimeType("application/pdf")
                },
                target = dragAndDropTarget
            )
            .background(if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
        ) {
            // Header Row (App Title + Theme Switcher)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                ThemeDropdownMenu(
                    onThemeSelected = { mode -> onAction(MainUiAction.SetTheme(mode)) }
                )
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
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

                        if (uiState.hasSelectedFiles) {
                            Spacer(modifier = Modifier.height(16.dp))
                            SelectedFilesCard(
                                fileNames = uiState.selectedFileNames,
                                fileCount = uiState.fileCount,
                                onClear = { onAction(MainUiAction.ClearSelectedFiles(context)) }
                            )

                            if (uiState.selectedMetadata != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                DocumentDetailsCard(
                                    metadata = uiState.selectedMetadata,
                                    onPreview = if (uiState.hasSelectedFiles) {
                                        { onAction(MainUiAction.SetPreviewPdfUri(uiState.selectedUris.first())) }
                                    } else null
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            EmptyStateCard()
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
                                onOpenPasswordList = {
                                    val fragmentActivity = context as? FragmentActivity
                                    if (fragmentActivity != null) {
                                        BiometricHelper.authenticate(
                                            activity = fragmentActivity,
                                            title = context.getString(R.string.biometric_prompt_title),
                                            subtitle = context.getString(R.string.biometric_prompt_subtitle),
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
                                },
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onAction(MainUiAction.DecryptInPlace(context))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                        enabled = uiState.password.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Text(stringResource(R.string.btn_overwrite), textAlign = TextAlign.Center)
                                    }

                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (uiState.isBatch) {
                                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                                openDocumentTreeLauncher.launch(intent)
                                            } else {
                                                val fileName = uiState.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                    addCategory(Intent.CATEGORY_OPENABLE)
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_TITLE, fileName)
                                                }
                                                createDocumentLauncher.launch(intent)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                        enabled = uiState.password.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Text(stringResource(R.string.btn_save_as), textAlign = TextAlign.Center)
                                    }
                                }
                            }

                            if (uiState.statusMessage != null) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = uiState.statusMessage,
                                    color = if (uiState.statusMessage.startsWith("Error") || uiState.statusMessage.startsWith("Failed") || uiState.statusMessage.contains("❌"))
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                val previewUri = uiState.activePreviewUri
                                if (previewUri != null) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onAction(MainUiAction.SetPreviewPdfUri(previewUri))
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("👁️")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.btn_preview_pdf), fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                val fallback = Intent(Intent.ACTION_VIEW)
                                                fallback.setDataAndType(Uri.parse("content://"), "*/*")
                                                try {
                                                    context.startActivity(fallback)
                                                } catch (_: Exception) {}
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("📂")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.btn_open_file_manager), textAlign = TextAlign.Center)
                                    }

                                    if (uiState.lastDecryptedUri != null) {
                                        OutlinedButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uiState.lastDecryptedUri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_open_pdf)))
                                                } catch (_: Exception) {}
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Text("📄")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.btn_open_pdf), textAlign = TextAlign.Center)
                                        }

                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, uiState.lastDecryptedUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share_file)))
                                                } catch (_: Exception) {}
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.btn_share_file))
                                        }
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
                                        text = "🔒 Password & Decryption Actions",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Select a PDF file on the left pane to enter password and decrypt.",
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
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

                    if (uiState.hasSelectedFiles) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SelectedFilesCard(
                            fileNames = uiState.selectedFileNames,
                            fileCount = uiState.fileCount,
                            onClear = { onAction(MainUiAction.ClearSelectedFiles(context)) }
                        )

                        if (uiState.selectedMetadata != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            DocumentDetailsCard(
                                metadata = uiState.selectedMetadata,
                                onPreview = if (uiState.hasSelectedFiles) {
                                    { onAction(MainUiAction.SetPreviewPdfUri(uiState.selectedUris.first())) }
                                } else null
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        PasswordInputSection(
                            password = uiState.password,
                            passwordVisible = uiState.passwordVisible,
                            onPasswordChange = { onAction(MainUiAction.UpdatePassword(it)) },
                            onTogglePasswordVisible = { onAction(MainUiAction.TogglePasswordVisibility) },
                            onOpenPasswordList = {
                                val fragmentActivity = context as? FragmentActivity
                                if (fragmentActivity != null) {
                                    BiometricHelper.authenticate(
                                        activity = fragmentActivity,
                                        title = context.getString(R.string.biometric_prompt_title),
                                        subtitle = context.getString(R.string.biometric_prompt_subtitle),
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
                            },
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onAction(MainUiAction.DecryptInPlace(context))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    enabled = uiState.password.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Text(stringResource(R.string.btn_overwrite), textAlign = TextAlign.Center)
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (uiState.isBatch) {
                                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                            openDocumentTreeLauncher.launch(intent)
                                        } else {
                                            val fileName = uiState.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_TITLE, fileName)
                                            }
                                            createDocumentLauncher.launch(intent)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    enabled = uiState.password.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text(stringResource(R.string.btn_save_as), textAlign = TextAlign.Center)
                                }
                            }
                        }

                        if (uiState.statusMessage != null) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = uiState.statusMessage,
                                color = if (uiState.statusMessage.startsWith("Error") || uiState.statusMessage.startsWith("Failed") || uiState.statusMessage.contains("❌"))
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            val previewUri = uiState.activePreviewUri
                            if (previewUri != null) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onAction(MainUiAction.SetPreviewPdfUri(previewUri))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("👁️")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_preview_pdf), fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            val fallback = Intent(Intent.ACTION_VIEW)
                                            fallback.setDataAndType(Uri.parse("content://"), "*/*")
                                            try {
                                                context.startActivity(fallback)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("📂")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.btn_open_file_manager), textAlign = TextAlign.Center)
                                }

                                if (uiState.lastDecryptedUri != null) {
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uiState.lastDecryptedUri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_open_pdf)))
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("📄")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.btn_open_pdf), textAlign = TextAlign.Center)
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uiState.lastDecryptedUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share_file)))
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.btn_share_file))
                                    }
                                }
                            }
                        }
                    } else {
                        EmptyStateCard()
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.version_info, versionName ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { showWhatsNewDialog = true }
            )
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Max97k/PDDF"))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.github_link),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
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
            onDismiss = { onAction(MainUiAction.SetPasswordListDialogVisible(false)) },
            onSelectPassword = { selectedPass ->
                onAction(MainUiAction.SelectSavedPassword(selectedPass))
            },
            onDeletePassword = { savedPass ->
                onAction(MainUiAction.DeletePassword(savedPass))
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.msg_password_deleted),
                        actionLabel = context.getString(R.string.btn_undo),
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

    if (uiState.showAutoUnlockPasswordPrompt) {
        AutoUnlockPasswordDialog(
            fileName = uiState.autoUnlockFileName,
            errorMessage = uiState.autoUnlockErrorMessage,
            onUnlock = { pass, rememberPass ->
                onAction(MainUiAction.UnlockWithManualPassword(context, pass, rememberPass))
            },
            onDismiss = { onAction(MainUiAction.DismissAutoUnlockPrompt) }
        )
    }

    if (uiState.previewPdfUri != null) {
        PdfViewerDialog(
            uri = uiState.previewPdfUri,
            title = uiState.selectedFileNames.firstOrNull() ?: "",
            onDismiss = { onAction(MainUiAction.SetPreviewPdfUri(null)) },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uiState.previewPdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share_file)))
                } catch (_: Exception) {}
            },
            onSaveAs = {
                val fileName = uiState.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                createDocumentLauncher.launch(intent)
            }
        )
    }

    if (showWhatsNewDialog) {
        WhatsNewDialog(
            onDismiss = { showWhatsNewDialog = false }
        )
    }
}
