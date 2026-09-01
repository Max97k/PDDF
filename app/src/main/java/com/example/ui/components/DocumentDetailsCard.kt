package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.PdfMetadata
import com.example.R
import java.util.Locale

@Composable
fun DocumentDetailsCard(
    metadata: PdfMetadata,
    modifier: Modifier = Modifier,
    onPreview: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.title_document_details),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onPreview != null) {
                        TextButton(onClick = onPreview) {
                            Text("👁️ " + stringResource(R.string.btn_preview_pdf))
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (expanded) {
                            stringResource(R.string.content_desc_collapse)
                        } else {
                            stringResource(R.string.content_desc_expand)
                        }
                    )
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.label_doc_title, metadata.title),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.label_doc_author, metadata.author),
                    style = MaterialTheme.typography.bodySmall
                )
                val pagesText = if (metadata.pageCount > 0) {
                    metadata.pageCount.toString()
                } else {
                    stringResource(R.string.label_unknown)
                }
                Text(
                    text = stringResource(R.string.label_doc_pages, pagesText),
                    style = MaterialTheme.typography.bodySmall
                )
                val sizeText = "${String.format(Locale.US, "%.2f", metadata.fileSizeMb)} MB"
                Text(
                    text = stringResource(R.string.label_doc_file_size, sizeText),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.label_doc_encryption, metadata.encryptionMethod),
                    style = MaterialTheme.typography.bodySmall
                )
                val printStatus = if (metadata.canPrint) {
                    stringResource(R.string.label_printing_allowed)
                } else {
                    stringResource(R.string.label_printing_denied)
                }
                val copyStatus = if (metadata.canCopy) {
                    stringResource(R.string.label_copying_allowed)
                } else {
                    stringResource(R.string.label_copying_denied)
                }
                val permText = "$printStatus, $copyStatus"
                Text(
                    text = stringResource(R.string.label_doc_permissions, permText),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
