package com.max97k.pddf.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import android.widget.Toast
import com.max97k.pddf.util.ClipboardUtils
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.max97k.pddf.R
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.data.DensityMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Download

@Composable
fun SavedPasswordListDialog(
    savedPasswords: List<PasswordEntity>,
    densityMode: DensityMode,
    onDismiss: () -> Unit,
    onSelectPassword: (String) -> Unit,
    onDeletePassword: (PasswordEntity) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val verticalPadding = when (densityMode) {
        DensityMode.COMPACT -> 4.dp
        DensityMode.STANDARD -> 12.dp
        DensityMode.COMFORTABLE -> 20.dp
    }

    val minRowHeight = when (densityMode) {
        DensityMode.COMPACT -> 36.dp
        DensityMode.STANDARD -> 48.dp
        DensityMode.COMFORTABLE -> 64.dp
    }

    val textStyle = when (densityMode) {
        DensityMode.COMPACT -> MaterialTheme.typography.bodyMedium
        DensityMode.STANDARD -> MaterialTheme.typography.bodyLarge
        DensityMode.COMFORTABLE -> MaterialTheme.typography.titleMedium
    }

    val iconSize = when (densityMode) {
        DensityMode.COMPACT -> 20.dp
        DensityMode.STANDARD -> 24.dp
        DensityMode.COMFORTABLE -> 32.dp
    }

    AlertDialog(
        modifier = modifier
            .imePadding()
            .padding(24.dp)
            .wrapContentWidth()
            .wrapContentHeight(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOn
        ),
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dialog_title_saved_passwords),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Row {
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Default.UploadFile, contentDescription = stringResource(R.string.content_desc_export_vault))
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.content_desc_import_vault))
                    }
                }
            }
        },
        text = {
            if (savedPasswords.isEmpty()) {
                Text(stringResource(R.string.msg_no_saved_passwords))
            } else {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text(stringResource(R.string.label_search)) },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.content_desc_clear_search)
                                    )
                                }
                            }
                        }
                    )

                    val filteredPasswords = savedPasswords.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredPasswords.isEmpty()) {
                        Text(
                            text = stringResource(R.string.msg_no_matching_passwords),
                            modifier = Modifier
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredPasswords, key = { it.id }) { savedPass ->
                                androidx.compose.material3.Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = minRowHeight)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelectPassword(savedPass.passwordValue)
                                            }
                                            .padding(vertical = verticalPadding, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val name = savedPass.name
                                        val highlightIndex = name.indexOf(searchQuery, ignoreCase = true)
                                        val annotatedString = if (searchQuery.isNotEmpty() && highlightIndex != -1) {
                                            buildAnnotatedString {
                                                append(name.substring(0, highlightIndex))
                                                withStyle(
                                                    style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)
                                                ) {
                                                    append(name.substring(highlightIndex, highlightIndex + searchQuery.length))
                                                }
                                                append(name.substring(highlightIndex + searchQuery.length))
                                            }
                                        } else {
                                            buildAnnotatedString { append(name) }
                                        }

                                        Text(
                                            text = annotatedString,
                                            modifier = Modifier.weight(1f),
                                            style = textStyle
                                        )
                                        val copyMsg = stringResource(R.string.msg_password_copied)
                                        IconButton(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            ClipboardUtils.copyPasswordToClipboard(context, "Password", savedPass.passwordValue)
                                            Toast.makeText(context, copyMsg, Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = stringResource(R.string.content_desc_copy_password)
                                            )
                                        }
                                        IconButton(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeletePassword(savedPass)
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.content_desc_delete_named, savedPass.name),
                                                modifier = Modifier.size(iconSize)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }) { Text(stringResource(R.string.btn_close)) }
        }
    )
}
