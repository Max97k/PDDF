package com.example.feature.vault

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R

@Composable
fun SavePasswordDialog(
    currentPassword: String,
    onDismiss: () -> Unit,
    onSave: (name: String, pass: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordName by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        modifier = modifier
            .imePadding()
            .padding(24.dp)
            .wrapContentWidth()
            .wrapContentHeight(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
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
