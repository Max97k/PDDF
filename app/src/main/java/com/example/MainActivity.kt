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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PasswordEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.util.FileUtils

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PDFDecryptorScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
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
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            uri?.let { uris.add(it) }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uriList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            uriList?.let { uris.addAll(it) }
        }

        if (uris.isNotEmpty()) {
            viewModel.setSelectedUris(this, uris)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDFDecryptorScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    val selectedUris by viewModel.selectedUris.collectAsStateWithLifecycle()
    val selectedFileNames by viewModel.selectedFileNames.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val savedPasswords by viewModel.savedPasswords.collectAsStateWithLifecycle()
    val lastDecryptedUri by viewModel.lastDecryptedUri.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showSavePasswordDialog by remember { mutableStateOf(false) }
    var showPasswordListDialog by remember { mutableStateOf(false) }

    val isSecureModeActive = selectedUris.isNotEmpty() || showPasswordListDialog || showSavePasswordDialog
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
                val uri = result.data?.data
                if (uri != null) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    viewModel.setSelectedUris(context, listOf(uri))
                }
            }
        }
    )

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
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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

            Spacer(modifier = Modifier.height(12.dp))

            PasswordInputSection(
                password = password,
                passwordVisible = passwordVisible,
                onPasswordChange = { password = it },
                onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                onOpenPasswordList = { showPasswordListDialog = true },
                onOpenSavePassword = { showSavePasswordDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.decryptAndOverwrite(context, selectedUris.firstOrNull(), password) 
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
                            val fileName = selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_TITLE, fileName)
                            }
                            createDocumentLauncher.launch(intent)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = password.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.btn_save_as), textAlign = TextAlign.Center)
                    }
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
                    Button(
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

                    Button(
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
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_share_file), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        } // Close Column

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "0.1.0"
        }

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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            onDismiss = { showSavePasswordDialog = false },
            onSave = { name, pass ->
                viewModel.savePassword(name, pass)
                showSavePasswordDialog = false
            }
        )
    }

    if (showPasswordListDialog) {
        SavedPasswordListDialog(
            savedPasswords = savedPasswords,
            onDismiss = { showPasswordListDialog = false },
            onSelectPassword = { selectedPass ->
                password = selectedPass
                showPasswordListDialog = false
            },
            onDeletePassword = { id -> viewModel.deletePassword(id) }
        )
    }
}

@Composable
private fun SelectedFilesCard(
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
private fun PasswordInputSection(
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
private fun SavePasswordDialog(
    currentPassword: String,
    onDismiss: () -> Unit,
    onSave: (name: String, pass: String) -> Unit
) {
    var passwordName by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    AlertDialog(
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
    onDeletePassword: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_saved_passwords)) },
        text = {
            if (savedPasswords.isEmpty()) {
                Text(stringResource(R.string.msg_no_saved_passwords))
            } else {
                LazyColumn {
                    items(savedPasswords, key = { it.id }) { savedPass ->
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
                            Text(
                                text = savedPass.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            IconButton(onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeletePassword(savedPass.id) 
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
