package com.max97k.pddf.feature.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.max97k.pddf.R
import com.max97k.pddf.util.PageRangeParser

@Composable
fun PdfExtractPagesDialog(
    totalPages: Int,
    onDismiss: () -> Unit,
    onSaveAs: (String) -> Unit,
    onShare: (String) -> Unit
) {
    var pageRange by rememberSaveable { mutableStateOf("") }

    val parsedPages = PageRangeParser.parse(pageRange, totalPages)
    val isValid = parsedPages.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_extract_pages)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pageRange,
                    onValueChange = { pageRange = it },
                    label = { Text(stringResource(R.string.hint_page_range)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val previewText = if (pageRange.isBlank()) {
                    ""
                } else if (isValid) {
                    stringResource(R.string.msg_extracted_pages_preview, parsedPages.size)
                } else {
                    stringResource(R.string.error_invalid_page_range)
                }

                Text(
                    text = previewText,
                    color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAs(pageRange) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.btn_save_as))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onShare(pageRange) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.btn_share_file))
            }
        }
    )
}
