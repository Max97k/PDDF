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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.max97k.pddf.data.AppDatabase
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import com.max97k.pddf.R
import java.util.Locale

@Composable
fun DocumentDetailsCard(
    metadata: PdfMetadata,
    modifier: Modifier = Modifier,
    decryptionDurationMs: Long? = null,
    onPreview: (() -> Unit)? = null,
    onFlattenForms: (() -> Unit)? = null,
    onReversePages: (() -> Unit)? = null,
    onStamp: (() -> Unit)? = null,
    onExportText: (() -> Unit)? = null,
    onSaveAttachment: ((String) -> Unit)? = null,
    onSaveAllAttachments: (() -> Unit)? = null,
    uriHash: Int? = null,
    suggestedTitle: String? = null,
    onSaveAsSuggested: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var attachmentsExpanded by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    var readingProgress by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uriHash) {
        if (uriHash != null) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val progress = db.readingProgressDao().getProgress(uriHash)
                if (progress != null && progress.totalPages > 0) {
                    val percent = ((progress.lastViewedPage.toFloat() / (progress.totalPages - 1).coerceAtLeast(1)) * 100).roundToInt().coerceIn(0, 100)
                    withContext(Dispatchers.Main) {
                        readingProgress = percent
                    }
                }
            }
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_RELEASE)
                expanded = !expanded
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.title_document_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onFlattenForms != null) {
                        IconButton(onClick = { exportMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_export_options)
                            )
                        }
                        DropdownMenu(
                            expanded = exportMenuExpanded,
                            onDismissRequest = { exportMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_flatten_forms)) },
                                onClick = {
                                    exportMenuExpanded = false
                                    onFlattenForms()
                                }
                            )
                        }
                    }
                    IconButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_RELEASE)
                        expanded = !expanded
                    }) {
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
            }

            // Quick action chips row if actions are provided
            if (onPreview != null || onReversePages != null || onStamp != null || onExportText != null || (suggestedTitle != null && onSaveAsSuggested != null)) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onPreview != null) {
                        AssistChip(
                            onClick = onPreview,
                            label = {
                                Text(
                                    text = stringResource(R.string.btn_preview_pdf),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (onReversePages != null) {
                        AssistChip(
                            onClick = onReversePages,
                            label = {
                                Text(
                                    text = stringResource(R.string.btn_reverse_pages),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (onStamp != null) {
                        AssistChip(
                            onClick = onStamp,
                            label = {
                                Text(
                                    text = stringResource(R.string.btn_stamp_pages),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FormatListNumbered,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (onExportText != null) {
                        AssistChip(
                            onClick = onExportText,
                            label = {
                                Text(
                                    text = stringResource(R.string.btn_export_text),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                    if (!suggestedTitle.isNullOrBlank() && onSaveAsSuggested != null) {
                        AssistChip(
                            onClick = onSaveAsSuggested,
                            label = {
                                Text(
                                    text = "${stringResource(R.string.btn_save_as)}: $suggestedTitle",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            if (readingProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reading progress: $readingProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (readingProgress ?: 0) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(14.dp))
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.label_doc_title, metadata.title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.label_doc_author, metadata.author),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val pagesText = if (metadata.pageCount > 0) {
                            metadata.pageCount.toString()
                        } else {
                            stringResource(R.string.label_unknown)
                        }
                        Text(
                            text = stringResource(R.string.label_doc_pages, pagesText),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sizeText = "${String.format(Locale.US, "%.2f", metadata.fileSizeMb)} MB"
                        Text(
                            text = stringResource(R.string.label_doc_file_size, sizeText),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.label_doc_encryption, metadata.encryptionMethod),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (decryptionDurationMs != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val durationSec = decryptionDurationMs / 1000.0
                            val mbps = if (durationSec > 0) metadata.fileSizeMb / durationSec else 0.0
                            val mbpsFormatted = String.format(Locale.US, "%.2f", mbps)
                            Text(
                                text = "Performance: ${decryptionDurationMs}ms ($mbpsFormatted MB/s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        }
                    }
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

                if (metadata.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_RELEASE)
                            attachmentsExpanded = !attachmentsExpanded
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attachments (${metadata.attachments.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onSaveAllAttachments != null) {
                                TextButton(onClick = { onSaveAllAttachments() }) {
                                    Text(stringResource(R.string.btn_save_all))
                                }
                            }
                            Icon(
                                imageVector = if (attachmentsExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (attachmentsExpanded) {
                                    stringResource(R.string.content_desc_collapse)
                                } else {
                                    stringResource(R.string.content_desc_expand)
                                }
                            )
                        }
                    }

                    if (attachmentsExpanded) {
                        metadata.attachments.forEach { attachment ->
                            val attSizeText = if (attachment.sizeBytes > 1024 * 1024) {
                                String.format(Locale.US, "%.2f MB", attachment.sizeBytes / (1024.0 * 1024.0))
                            } else if (attachment.sizeBytes > 1024) {
                                String.format(Locale.US, "%.2f KB", attachment.sizeBytes / 1024.0)
                            } else {
                                "${attachment.sizeBytes} B"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = attachment.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "$attSizeText, ${attachment.mimeType}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (onSaveAttachment != null) {
                                    IconButton(onClick = { onSaveAttachment(attachment.name) }) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Save ${attachment.name}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (metadata.fonts.isNotEmpty() || metadata.hasMissingNonEmbeddedSystemFonts) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var fontsExpanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_RELEASE)
                                fontsExpanded = !fontsExpanded
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.title_font_diagnostics),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (fontsExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (fontsExpanded) {
                                        stringResource(R.string.content_desc_collapse)
                                    } else {
                                        stringResource(R.string.content_desc_expand)
                                    }
                                )
                            }
                            if (fontsExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (metadata.hasMissingNonEmbeddedSystemFonts) {
                                    Text(
                                        text = stringResource(R.string.warning_missing_fonts),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                metadata.fonts.forEach { font ->
                                    val subsetText = if (font.isSubset) stringResource(R.string.label_subset) else ""
                                    val embedText = if (font.isEmbedded) stringResource(R.string.label_embedded) else stringResource(R.string.label_not_embedded)
                                    Text(
                                        text = stringResource(R.string.label_font_item, font.name, font.subType, embedText, subsetText),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
