package com.example.ui.components

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Post-decryption action section following Material 3 button emphasis hierarchy:
 * 1. High Emphasis (Filled Button): Preview PDF in built-in viewer
 * 2. Medium Emphasis (Outlined Buttons, 50/50 Symmetrical): Share file & Open in external app
 * 3. Low Emphasis (Text Button): Open Downloads / File Manager
 */
@Composable
fun PostDecryptActionSection(
    previewUri: Uri?,
    lastDecryptedUri: Uri?,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. High Emphasis: In-app PDF Preview (Full width)
        if (previewUri != null) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPreviewClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_preview_pdf),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 2. Medium Emphasis: Share & Open Externally (50/50 Symmetrical Row)
        if (lastDecryptedUri != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, lastDecryptedUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(
                                Intent.createChooser(intent, context.getString(R.string.btn_share_file))
                            )
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_share_file),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(lastDecryptedUri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(
                                Intent.createChooser(intent, context.getString(R.string.btn_open_pdf))
                            )
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_open_pdf),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // 3. Low Emphasis: Open File Manager (Centered TextButton)
        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallback = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse("content://"), "*/*")
                    }
                    try {
                        context.startActivity(fallback)
                    } catch (_: Exception) {}
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.btn_open_file_manager),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
