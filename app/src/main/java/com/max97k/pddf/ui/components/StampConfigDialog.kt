package com.max97k.pddf.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.max97k.pddf.domain.model.StampConfig

@Composable
fun StampConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (StampConfig) -> Unit
) {
    var format by remember { mutableStateOf("Page %d of %d") }
    var position by remember { mutableStateOf("bottom-center") }
    var fontSizeStr by remember { mutableStateOf("12.0") }
    var marginStr by remember { mutableStateOf("20.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Page Numbers") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = format,
                    onValueChange = { format = it },
                    label = { Text("Format (e.g. Page %d of %d)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Position (bottom-center or bottom-right)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fontSizeStr,
                    onValueChange = { fontSizeStr = it },
                    label = { Text("Font Size") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = marginStr,
                    onValueChange = { marginStr = it },
                    label = { Text("Margin") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val config = StampConfig(
                    format = format,
                    position = position,
                    fontSize = fontSizeStr.toFloatOrNull() ?: 12f,
                    margin = marginStr.toFloatOrNull() ?: 20f
                )
                onConfirm(config)
            }) {
                Text("Next")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
