package com.example

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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
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
    var filePrefix by remember { mutableStateOf("") }
    var deleteOriginal by remember { mutableStateOf(true) }

    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val savedPasswords by viewModel.savedPasswords.collectAsStateWithLifecycle()
    val conflictMode by viewModel.conflictMode.collectAsStateWithLifecycle()
    val rememberConflictChoice by viewModel.rememberConflictChoice.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showSavePasswordDialog by remember { mutableStateOf(false) }
    var showPasswordListDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.setSelectedUris(context, uris)
            }
        }
    )

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.decryptMultiplePdfs(
                    context = context,
                    inputUris = selectedUris,
                    outputDirectoryUri = uri,
                    passwordValue = password,
                    prefix = filePrefix,
                    deleteOriginal = deleteOriginal
                )
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
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
            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.content_desc_select_pdfs))
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

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = filePrefix,
                onValueChange = { filePrefix = it },
                label = { Text(stringResource(R.string.label_prefix)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { deleteOriginal = !deleteOriginal }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = deleteOriginal,
                    onCheckedChange = { deleteOriginal = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.checkbox_delete_original),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ConflictSettingsCard(
                conflictMode = conflictMode,
                rememberChoice = rememberConflictChoice,
                onConflictModeChanged = { mode, remember ->
                    viewModel.updateConflictSettings(mode, remember)
                }
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

            Button(
                onClick = { directoryPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isProcessing && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSecondary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.content_desc_select_output))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_decrypt))
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
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear_selection))
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
private fun ConflictSettingsCard(
    conflictMode: ConflictMode,
    rememberChoice: Boolean,
    onConflictModeChanged: (ConflictMode, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.title_conflict_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onConflictModeChanged(ConflictMode.SAVE_AS_COPY, rememberChoice) }
            ) {
                RadioButton(
                    selected = conflictMode == ConflictMode.SAVE_AS_COPY,
                    onClick = { onConflictModeChanged(ConflictMode.SAVE_AS_COPY, rememberChoice) }
                )
                Text(
                    text = stringResource(R.string.option_save_as_copy),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onConflictModeChanged(ConflictMode.OVERWRITE, rememberChoice) }
            ) {
                RadioButton(
                    selected = conflictMode == ConflictMode.OVERWRITE,
                    onClick = { onConflictModeChanged(ConflictMode.OVERWRITE, rememberChoice) }
                )
                Text(
                    text = stringResource(R.string.option_overwrite),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onConflictModeChanged(conflictMode, !rememberChoice) }
                    .padding(top = 2.dp)
            ) {
                Checkbox(
                    checked = rememberChoice,
                    onCheckedChange = { checked -> onConflictModeChanged(conflictMode, checked) }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.checkbox_remember_choice),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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
                IconButton(onClick = onTogglePasswordVisible) {
                    Icon(imageVector = image, contentDescription = stringResource(R.string.content_desc_toggle_password))
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onOpenPasswordList) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.content_desc_saved_passwords))
        }
        IconButton(
            onClick = onOpenSavePassword,
            enabled = password.isNotBlank()
        ) {
            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.content_desc_save_password))
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
                if (passwordName.isNotBlank()) {
                    onSave(passwordName, currentPassword)
                }
            }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_saved_passwords)) },
        text = {
            if (savedPasswords.isEmpty()) {
                Text(stringResource(R.string.msg_no_saved_passwords))
            } else {
                LazyColumn {
                    items(savedPasswords) { savedPass ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPassword(savedPass.passwordValue) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = savedPass.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            IconButton(onClick = { onDeletePassword(savedPass.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.content_desc_delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
        }
    )
}

fun getFileName(context: Context, uri: Uri): String {
    return FileUtils.getFileName(context, uri)
}
