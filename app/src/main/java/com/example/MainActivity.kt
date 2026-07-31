package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PDFDecryptorScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PDFDecryptorScreen(modifier: Modifier = Modifier) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                selectedFileUri = uri
                selectedFileName = getFileName(context, uri) ?: "Unknown PDF"
                statusMessage = null
            }
        }
    )

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            if (uri != null && selectedFileUri != null) {
                isProcessing = true
                statusMessage = "Decrypting..."
                coroutineScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            decryptPdf(context, selectedFileUri!!, uri, password)
                        }
                        if (result) {
                            statusMessage = "Decrypted and saved successfully!"
                            password = ""
                        } else {
                            statusMessage = "Failed to decrypt. File may not be encrypted, or error occurred."
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val msg = e.message?.lowercase() ?: ""
                        if (msg.contains("password") || msg.contains("decrypt")) {
                            statusMessage = "Error: Incorrect password or corrupted file."
                        } else {
                            statusMessage = "Error: ${e.message}"
                        }
                    } finally {
                        isProcessing = false
                    }
                }
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
            text = "PDF Decryptor",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = "Select PDF")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Encrypted PDF")
        }

        if (selectedFileUri != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Selected: $selectedFileName",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("PDF Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val newName = if (selectedFileName.endsWith(".pdf", ignoreCase = true)) {
                        selectedFileName.substringBeforeLast(".") + "_decrypted.pdf"
                    } else {
                        "decrypted_$selectedFileName.pdf"
                    }
                    saveFileLauncher.launch(newName)
                },
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
                    Icon(Icons.Default.LockOpen, contentDescription = "Decrypt")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Decrypt & Save")
                }
            }
        }

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = statusMessage!!,
                color = if (statusMessage!!.startsWith("Error") || statusMessage!!.startsWith("Failed")) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun decryptPdf(context: Context, inputUri: Uri, outputUri: Uri, password: String): Boolean {
    context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
        val document = PDDocument.load(inputStream, password)
        try {
            if (document.isEncrypted) {
                document.setAllSecurityToBeRemoved(true)
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    document.save(outputStream)
                    return true
                }
            }
        } finally {
            document.close()
        }
    }
    return false
}
