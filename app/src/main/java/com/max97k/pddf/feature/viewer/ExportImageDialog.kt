package com.max97k.pddf.feature.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.max97k.pddf.R

@Composable
fun ExportImageDialog(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onDismissRequest: () -> Unit,
    onExport: (pageIndex: Int, format: Bitmap.CompressFormat, quality: Int) -> Unit
) {
    var pageNumberText by remember { mutableStateOf("1") }
    var selectedFormat by remember { mutableStateOf(Bitmap.CompressFormat.PNG) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.dialog_title_export_image))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = pageNumberText,
                    onValueChange = { pageNumberText = it },
                    label = { Text(stringResource(R.string.label_page_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFormat == Bitmap.CompressFormat.PNG,
                        onClick = { selectedFormat = Bitmap.CompressFormat.PNG }
                    )
                    Text(text = stringResource(R.string.format_png), modifier = Modifier.padding(start = 8.dp))

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = selectedFormat == Bitmap.CompressFormat.JPEG,
                        onClick = { selectedFormat = Bitmap.CompressFormat.JPEG }
                    )
                    Text(text = stringResource(R.string.format_jpeg), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pageNumber = pageNumberText.toIntOrNull() ?: 1
                    val pageIndex = (pageNumber - 1).coerceAtLeast(0)
                    onExport(pageIndex, selectedFormat, 100)
                }
            ) {
                Text(stringResource(R.string.btn_export_image))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
