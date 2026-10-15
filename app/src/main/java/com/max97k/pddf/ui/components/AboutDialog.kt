package com.max97k.pddf.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.max97k.pddf.R
import com.max97k.pddf.domain.model.AppUpdateStatus

/**
 * About & What's New dialog — displays application identity, version,
 * Google Play in-app update checker, open-source project repository link, and release changelog.
 */
@Composable
fun AboutDialog(
    versionName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    appUpdateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    onCheckForUpdates: () -> Unit = {},
    onStartUpdate: () -> Unit = {},
    onCompleteUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.version_info, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // In-App Update Checking Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.version_info, versionName),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            when (appUpdateStatus) {
                                is AppUpdateStatus.Checking -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.status_checking_update),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                is AppUpdateStatus.Available -> {
                                    FilledTonalButton(
                                        onClick = onStartUpdate,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.btn_update_now),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                is AppUpdateStatus.Downloading -> {
                                    Text(
                                        text = stringResource(R.string.status_update_downloading, appUpdateStatus.progressPercent),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is AppUpdateStatus.Downloaded -> {
                                    Button(
                                        onClick = onCompleteUpdate,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.btn_restart_update),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                is AppUpdateStatus.UpToDate -> {
                                    Text(
                                        text = stringResource(R.string.status_app_up_to_date, versionName),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is AppUpdateStatus.Failed -> {
                                    TextButton(
                                        onClick = onCheckForUpdates,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.status_update_failed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                is AppUpdateStatus.Idle -> {
                                    OutlinedButton(
                                        onClick = onCheckForUpdates,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.btn_check_update),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                        if (appUpdateStatus is AppUpdateStatus.Available) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.status_update_available, appUpdateStatus.availableVersionCode.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (appUpdateStatus is AppUpdateStatus.Downloaded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.status_update_downloaded),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Max97k/PDDF"))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.github_link))
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.dialog_title_whats_new),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.changelog_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
        modifier = modifier
    )
}
