package com.max97k.pddf.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.max97k.pddf.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafPermissionsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(context.contentResolver.persistedUriPermissions) }

    fun refreshPermissions() {
        permissions = context.contentResolver.persistedUriPermissions
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Granted Storage Permissions")
        },
        text = {
            if (permissions.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text("No permissions granted.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(permissions, key = { it.uri.toString() }) { permission ->
                        val uri = permission.uri
                        var name by remember(uri) { mutableStateOf("") }

                        LaunchedEffect(uri) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                name = FileUtils.getSafDisplayName(context, uri)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (permission.isReadPermission) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                            Text("Read", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                    if (permission.isWritePermission) {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text("Write", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                FileUtils.releasePersistableUriPermissionSafely(context, uri)
                                refreshPermissions()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Revoke", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (permissions.isNotEmpty()) {
                Button(
                    onClick = {
                        permissions.forEach { permission ->
                            FileUtils.releasePersistableUriPermissionSafely(context, permission.uri)
                        }
                        refreshPermissions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Revoke All")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
