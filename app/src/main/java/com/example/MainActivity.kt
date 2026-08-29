package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import kotlinx.coroutines.launch
import com.example.data.PasswordEntity
import com.example.data.ThemeMode
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.PdfViewerDialog
import com.example.util.FileUtils

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeMode) {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    PDFDecryptorScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        windowWidthSizeClass = windowSizeClass.widthSizeClass
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uris = mutableListOf<Uri>()

        if (Intent.ACTION_VIEW == action) {
            intent.data?.let { uris.add(it) }
        } else if (Intent.ACTION_SEND == action) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { uris.add(it) }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val streamUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            streamUris?.let { uris.addAll(it) }
        }

        if (uris.isNotEmpty()) {
            viewModel.setSelectedUris(this, uris)
            if (uris.size == 1 && (Intent.ACTION_VIEW == action || Intent.ACTION_SEND == action)) {
                viewModel.startAutoUnlockFlow(this, uris.first()) {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PDFDecryptorScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val scope = rememberCoroutineScope()
    val selectedUris by viewModel.selectedUris.collectAsStateWithLifecycle()
    val selectedFileNames by viewModel.selectedFileNames.collectAsStateWithLifecycle()
    val selectedMetadata by viewModel.selectedMetadata.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val passwordVisible by viewModel.passwordVisible.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val savedPasswords by viewModel.savedPasswords.collectAsStateWithLifecycle()
    val lastDecryptedUri by viewModel.lastDecryptedUri.collectAsStateWithLifecycle()
    val previewPdfUri by viewModel.previewPdfUri.collectAsStateWithLifecycle()
    val isAutoUnlocking by viewModel.isAutoUnlocking.collectAsStateWithLifecycle()
    val showAutoUnlockPasswordPrompt by viewModel.showAutoUnlockPasswordPrompt.collectAsStateWithLifecycle()
    val autoUnlockTargetUri by viewModel.autoUnlockTargetUri.collectAsStateWithLifecycle()
    val autoUnlockFileName by viewModel.autoUnlockFileName.collectAsStateWithLifecycle()
    val autoUnlockErrorMessage by viewModel.autoUnlockErrorMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val showSavePasswordDialog by viewModel.showSavePasswordDialog.collectAsStateWithLifecycle()
    val showPasswordListDialog by viewModel.showPasswordListDialog.collectAsStateWithLifecycle()
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
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

    val isSecureModeActive = showPasswordListDialog || showSavePasswordDialog
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

    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

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
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    takeFlags
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    viewModel.setSelectedUris(context, uris)
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
                    viewModel.decryptMultiplePdfs(context, selectedUris, destUri, password, "", false)
                }
            }
        }
    )

    var isDragging by remember { mutableStateOf(false) }

    val saveDecryptedPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val destUri = result.data?.data
                val sourceUri = previewPdfUri ?: lastDecryptedUri
                if (destUri != null && sourceUri != null) {
                    viewModel.copyUriStream(context, sourceUri, destUri)
                }
            }
        }
    )

    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {}
            override fun onEntered(event: DragAndDropEvent) {
                isDragging = true
            }
            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }
            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }
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
                    viewModel.setSelectedUris(context, uris)
                    return true
                }
                return false
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
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

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val destUri = result.data?.data
                if (destUri != null) {
                    viewModel.decryptAndSaveAs(context, selectedUris.firstOrNull(), destUri, password)
                }
            }
        }
    )

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
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Theme Settings")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System Default") },
                            onClick = {
                                viewModel.setTheme(ThemeMode.SYSTEM)
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Light") },
                            onClick = {
                                viewModel.setTheme(ThemeMode.LIGHT)
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dark") },
                            onClick = {
                                viewModel.setTheme(ThemeMode.DARK)
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("AMOLED Black") },
                            onClick = {
                                viewModel.setTheme(ThemeMode.AMOLED)
                                expanded = false
                            }
                        )
                    }
                }
            }

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
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_select_pdfs))
        }

        if (selectedUris.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SelectedFilesCard(
                fileNames = selectedFileNames,
                fileCount = selectedUris.size,
                onClear = { viewModel.setSelectedUris(context, emptyList()) }
            )

            if (selectedMetadata != null) {
                Spacer(modifier = Modifier.height(12.dp))
                DocumentDetailsCard(
                    metadata = selectedMetadata!!,
                    onPreview = if (selectedUris.isNotEmpty()) {
                        { viewModel.previewPdfUri.value = selectedUris.first() }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PasswordInputSection(
                password = password,
                passwordVisible = passwordVisible,
                onPasswordChange = { viewModel.password.value = it },
                onTogglePasswordVisible = { viewModel.passwordVisible.value = !passwordVisible },
                onOpenPasswordList = { 
                    val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                    if (fragmentActivity != null) {
                        val biometricManager = androidx.biometric.BiometricManager.from(context)
                        val canAuthenticate = biometricManager.canAuthenticate(
                            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        if (canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                            val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                            val biometricPrompt = androidx.biometric.BiometricPrompt(
                                fragmentActivity,
                                executor,
                                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        super.onAuthenticationError(errorCode, errString)
                                        if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                            errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                            android.widget.Toast.makeText(context, errString, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        viewModel.showPasswordListDialog.value = true
                                    }
                                }
                            )

                            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                .setTitle(context.getString(R.string.biometric_prompt_title))
                                .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
                                .setAllowedAuthenticators(
                                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )
                                .build()

                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            viewModel.showPasswordListDialog.value = true
                        }
                    } else {
                        viewModel.showPasswordListDialog.value = true
                    }
                },
                onOpenSavePassword = { viewModel.showSavePasswordDialog.value = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isProcessing && !batchState.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            } else if (!isProcessing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.decryptAndOverwrite(context, selectedUris, password) 
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = password.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Text(stringResource(R.string.btn_overwrite), textAlign = TextAlign.Center)
                    }

                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (selectedUris.size > 1) {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                openDocumentTreeLauncher.launch(intent)
                            } else {
                                val fileName = selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_TITLE, fileName)
                                }
                                createDocumentLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = password.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.btn_save_as), textAlign = TextAlign.Center)
                    }
                }
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = statusMessage!!,
                    color = if (statusMessage!!.startsWith("Error") || statusMessage!!.startsWith("Failed") || statusMessage!!.contains("❌"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                val previewUri = lastDecryptedUri ?: (if (selectedMetadata?.isEncrypted == false) selectedUris.firstOrNull() else null)
                if (previewUri != null) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.previewPdfUri.value = previewUri
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("👁️")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_preview_pdf), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                            } catch (e: Exception) {
                                val fallback = Intent(Intent.ACTION_VIEW)
                                fallback.setDataAndType(Uri.parse("content://"), "*/*")
                                try {
                                    context.startActivity(fallback)
                                } catch (e2: Exception) {}
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("📂")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_open_file_manager), textAlign = TextAlign.Center)
                    }

                    if (lastDecryptedUri != null) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(lastDecryptedUri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_open_pdf)))
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
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
                                    putExtra(Intent.EXTRA_STREAM, lastDecryptedUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share_file)))
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.btn_share_file))
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(32.dp))
            Surface(
                modifier = Modifier.size(80.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.title_empty_state),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.desc_empty_state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        } // Close Column

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.version_info, versionName ?: "0.1.0"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { showWhatsNewDialog = true }
            )
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Max97k/PDDF"))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {}
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

    if (showSavePasswordDialog) {
        SavePasswordDialog(
            currentPassword = password,
            onDismiss = { viewModel.showSavePasswordDialog.value = false },
            onSave = { name, pass ->
                viewModel.savePassword(name, pass)
                viewModel.showSavePasswordDialog.value = false
            }
        )
    }

    if (showPasswordListDialog) {
        SavedPasswordListDialog(
            savedPasswords = savedPasswords,
            onDismiss = { viewModel.showPasswordListDialog.value = false },
            onSelectPassword = { selectedPass ->
                viewModel.password.value = selectedPass
                viewModel.showPasswordListDialog.value = false
            },
            onDeletePassword = { savedPass ->
                viewModel.deletePassword(savedPass.id)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.msg_password_deleted),
                        actionLabel = context.getString(R.string.btn_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restorePassword(savedPass)
                    }
                }
            }
        )
    }

    if (batchState.isProcessing) {
        AlertDialog(
            onDismissRequest = { /* No dismiss by clicking outside */ },
            title = { Text("Processing Batch") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${batchState.progress} of ${batchState.total} completed",
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (batchState.total > 0) batchState.progress.toFloat() / batchState.total else 0f,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelBatch() }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showWhatsNewDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsNewDialog = false },
            title = { Text(stringResource(R.string.dialog_title_whats_new)) },
            text = { Text(stringResource(R.string.changelog_content)) },
            confirmButton = {
                TextButton(onClick = { showWhatsNewDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    if (isAutoUnlocking) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.label_auto_unlocking),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showAutoUnlockPasswordPrompt && autoUnlockTargetUri != null) {
        var inputPass by remember { mutableStateOf("") }
        var passVisible by remember { mutableStateOf(false) }
        var rememberPass by remember { mutableStateOf(true) }

        val unlockAction = {
            if (inputPass.isNotBlank()) {
                viewModel.unlockWithManualPassword(
                    context = context,
                    uri = autoUnlockTargetUri!!,
                    enteredPassword = inputPass,
                    rememberPassword = rememberPass
                )
            }
        }

        AlertDialog(
            onDismissRequest = { viewModel.dismissAutoUnlockPrompt() },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.title_unlock_pdf)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = autoUnlockFileName.ifBlank { "Document" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputPass,
                        onValueChange = { inputPass = it },
                        label = { Text(stringResource(R.string.label_password)) },
                        singleLine = true,
                        isError = autoUnlockErrorMessage != null,
                        supportingText = if (autoUnlockErrorMessage != null) {
                            { Text(autoUnlockErrorMessage!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { unlockAction() }),
                        trailingIcon = {
                            val img = if (passVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(img, contentDescription = if (passVisible) "Hide password" else "Show password")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberPass = !rememberPass }
                    ) {
                        Checkbox(
                            checked = rememberPass,
                            onCheckedChange = { rememberPass = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.label_remember_password),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = unlockAction,
                    enabled = inputPass.isNotBlank()
                ) {
                    Text(stringResource(R.string.btn_unlock_pdf))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAutoUnlockPrompt() }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (previewPdfUri != null) {
        val displayTitle = autoUnlockFileName.ifBlank {
            selectedFileNames.firstOrNull() ?: previewPdfUri?.lastPathSegment?.substringAfterLast('/') ?: stringResource(R.string.app_name)
        }
        PdfViewerDialog(
            uri = previewPdfUri!!,
            title = displayTitle,
            onDismiss = { viewModel.previewPdfUri.value = null },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, previewPdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share_file)))
                } catch (_: Exception) {}
            },
            onSaveAs = {
                val fileName = "decrypted_${displayTitle.ifBlank { "document.pdf" }}"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                saveDecryptedPdfLauncher.launch(intent)
            }
        )
    }
}

@Composable
fun SelectedFilesCard(
    fileNames: List<String>,
    fileCount: Int,
    onClear: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_selected_files_count, fileCount),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClear() 
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.content_desc_clear_selection)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (fileNames.isNotEmpty()) fileNames.joinToString(", ") else stringResource(R.string.btn_select_pdfs),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PasswordInputSection(
    password: String,
    passwordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onOpenPasswordList: () -> Unit,
    onOpenSavePassword: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.label_password)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTogglePasswordVisible() 
                }) {
                    Icon(
                        imageVector = image,
                        contentDescription = description
                    )
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { 
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onOpenPasswordList() 
        }) {
            Icon(
                Icons.Default.List,
                contentDescription = stringResource(R.string.content_desc_saved_passwords)
            )
        }
        IconButton(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenSavePassword() 
            },
            enabled = password.isNotBlank()
        ) {
            Icon(
                Icons.Default.Save,
                contentDescription = stringResource(R.string.content_desc_save_password)
            )
        }
    }
}

@Composable
fun SavePasswordDialog(
    currentPassword: String,
    onDismiss: () -> Unit,
    onSave: (name: String, pass: String) -> Unit
) {
    var passwordName by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        modifier = Modifier.padding(24.dp).wrapContentWidth().wrapContentHeight(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_save_password)) },
        text = {
            OutlinedTextField(
                value = passwordName,
                onValueChange = { passwordName = it },
                label = { Text(stringResource(R.string.label_password_name_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (passwordName.isNotBlank()) {
                    onSave(passwordName, currentPassword)
                }
            }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss() 
            }) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
private fun SavedPasswordListDialog(
    savedPasswords: List<PasswordEntity>,
    onDismiss: () -> Unit,
    onSelectPassword: (String) -> Unit,
    onDeletePassword: (PasswordEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        modifier = Modifier.padding(24.dp).wrapContentWidth().wrapContentHeight(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_saved_passwords)) },
        text = {
            if (savedPasswords.isEmpty()) {
                Text(stringResource(R.string.msg_no_saved_passwords))
            } else {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        label = { Text("Search") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )

                    val filteredPasswords = savedPasswords.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredPasswords.isEmpty()) {
                        Text(
                            text = "No matching passwords",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(filteredPasswords, key = { it.id }) { savedPass ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectPassword(savedPass.passwordValue) 
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val name = savedPass.name
                                    val highlightIndex = name.indexOf(searchQuery, ignoreCase = true)
                                    val annotatedString = if (searchQuery.isNotEmpty() && highlightIndex != -1) {
                                        androidx.compose.ui.text.buildAnnotatedString {
                                            append(name.substring(0, highlightIndex))
                                            withStyle(
                                                style = androidx.compose.ui.text.SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                append(name.substring(highlightIndex, highlightIndex + searchQuery.length))
                                            }
                                            append(name.substring(highlightIndex + searchQuery.length))
                                        }
                                    } else {
                                        androidx.compose.ui.text.buildAnnotatedString { append(name) }
                                    }

                                    Text(
                                        text = annotatedString,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    IconButton(onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeletePassword(savedPass) 
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${savedPass.name}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss() 
            }) { Text(stringResource(R.string.btn_close)) }
        }
    )
}

fun getFileName(context: Context, uri: Uri): String {
    return FileUtils.getFileName(context, uri)
}

@Composable
fun DocumentDetailsCard(metadata: PdfMetadata, onPreview: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Document Details", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onPreview != null) {
                        TextButton(onClick = onPreview) {
                            Text("👁️ " + stringResource(R.string.btn_preview_pdf))
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Title: ${metadata.title}", style = MaterialTheme.typography.bodySmall)
                Text("Author: ${metadata.author}", style = MaterialTheme.typography.bodySmall)
                Text("Pages: ${if (metadata.pageCount > 0) metadata.pageCount else "Unknown"}", style = MaterialTheme.typography.bodySmall)
                Text("File Size: ${String.format(java.util.Locale.US, "%.2f", metadata.fileSizeMb)} MB", style = MaterialTheme.typography.bodySmall)
                Text("Encryption: ${metadata.encryptionMethod}", style = MaterialTheme.typography.bodySmall)
                Text("Permissions: ${if (metadata.canPrint) "Printing allowed" else "Printing denied"}, ${if (metadata.canCopy) "Copying allowed" else "Copying denied"}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
