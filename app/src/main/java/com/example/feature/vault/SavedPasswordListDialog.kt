package com.example.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.PasswordEntity

@Composable
fun SavedPasswordListDialog(
    savedPasswords: List<PasswordEntity>,
    onDismiss: () -> Unit,
    onSelectPassword: (String) -> Unit,
    onDeletePassword: (PasswordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        modifier = modifier
            .imePadding()
            .padding(24.dp)
            .wrapContentWidth()
            .wrapContentHeight(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_saved_passwords)) },
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
                            .padding(bottom = 8.dp),
                        label = { Text(stringResource(R.string.label_search)) },
                        singleLine = true,
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
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(filteredPasswords, key = { it.id }) { savedPass ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectPassword(savedPass.passwordValue)
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
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
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeletePassword(savedPass)
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.content_desc_delete_named, savedPass.name)
                                        )
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
