package com.max97k.pddf.feature.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PdfPrintOptionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (isGrayscale: Boolean, pageRange: String?) -> Unit
) {
    var isGrayscale by remember { mutableStateOf(false) }
    var printAllPages by remember { mutableStateOf(true) }
    var customPageRange by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print Options") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Checkbox(
                        checked = isGrayscale,
                        onCheckedChange = { isGrayscale = it }
                    )
                    Text("Eco-friendly Grayscale")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = printAllPages,
                        onClick = { printAllPages = true }
                    )
                    Text("All Pages")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !printAllPages,
                        onClick = { printAllPages = false }
                    )
                    Text("Custom Range")
                }

                if (!printAllPages) {
                    OutlinedTextField(
                        value = customPageRange,
                        onValueChange = { customPageRange = it },
                        label = { Text("e.g. 1-5, 8, 11-13") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(isGrayscale, if (printAllPages) null else customPageRange)
                }
            ) {
                Text("Print")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
