package com.max97k.pddf.feature.decrypt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.max97k.pddf.R

@Composable
fun ReEncryptPasswordDialog(
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Note: Window-level FLAG_SECURE is centrally managed by PDFDecryptorScreen via
    // uiState.isSecureModeActive (which includes showReEncryptDialog), and Dialog-window
    // protection is enforced below via SecureFlagPolicy.SecureOn.

    val isConfirmEnabled = password.isNotEmpty() && password == confirmPassword

    AlertDialog(
        onDismissRequest = {
            password = ""
            confirmPassword = ""
            onDismiss()
        },
        properties = DialogProperties(
            securePolicy = SecureFlagPolicy.SecureOn
        ),
        title = { Text(stringResource(R.string.title_protect_pdf)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                val visibilityIcon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val visibilityDesc = if (passwordVisible) {
                    stringResource(R.string.hide_password)
                } else {
                    stringResource(R.string.show_password)
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.label_new_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.semantics { stateDescription = visibilityDesc }
                        ) {
                            Icon(imageVector = visibilityIcon, contentDescription = visibilityDesc)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.label_confirm_password)) },
                    singleLine = true,
                    isError = password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.semantics { stateDescription = visibilityDesc }
                        ) {
                            Icon(imageVector = visibilityIcon, contentDescription = visibilityDesc)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isConfirmEnabled) {
                        val chars = password.toCharArray()
                        onConfirm(chars)
                        password = ""
                        confirmPassword = ""
                    }
                },
                enabled = isConfirmEnabled
            ) {
                Text(stringResource(R.string.btn_protect))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    password = ""
                    confirmPassword = ""
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
