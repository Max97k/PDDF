package com.max97k.pddf.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.R
import java.util.Locale

@Composable
fun DocumentDetailsCard(
    metadata: PdfMetadata,
    modifier: Modifier = Modifier,
    decryptionDurationMs: Long? = null,
    onPreview: (() -> Unit)? = null,
    onFlattenForms: (() -> Unit)? = null,
    onReversePages: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_RELEASE)
                expanded = !expanded
            },
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
                    if (onReversePages != null) {
                        TextButton(onClick = onReversePages) {
                            Text("🔄 " + stringResource(R.string.btn_reverse_pages))
                        }
                    }
                    if (onPreview != null) {
                        TextButton(onClick = onPreview) {
                            Text("👁️ " + stringResource(R.string.btn_preview_pdf))
                        }
                    }
                    if (onFlattenForms != null) {
                        IconButton(onClick = { exportMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Export options"
                            )
                        }
                        DropdownMenu(
                            expanded = exportMenuExpanded,
                            onDismissRequest = { exportMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Flatten Forms") },
                                onClick = {
                                    exportMenuExpanded = false
                                    onFlattenForms()
                                }
                            )
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

                if (decryptionDurationMs != null) {
                    val durationSec = decryptionDurationMs / 1000.0
                    val mbps = if (durationSec > 0) metadata.fileSizeMb / durationSec else 0.0
                    val mbpsFormatted = String.format(Locale.US, "%.2f", mbps)
                    Text(
                        text = "Performance: ${decryptionDurationMs}ms ($mbpsFormatted MB/s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.label_signatures),
                    style = MaterialTheme.typography.titleSmall
                )
                if (metadata.signatures.isEmpty()) {
                    Text(
                        text = stringResource(R.string.label_unsigned),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    metadata.signatures.forEach { sig ->
                        val validText = if (sig.isValid) stringResource(R.string.label_valid_signature) else stringResource(R.string.label_invalid_signature)
                        Text(
                            text = stringResource(R.string.label_validity, validText),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.label_signer, sig.signerName),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.label_sign_date, sig.signingDate),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.label_reason, sig.reason),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.label_location, sig.location),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.label_cert_subject, sig.certificateSubject),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.label_cert_issuer, sig.certificateIssuer),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
