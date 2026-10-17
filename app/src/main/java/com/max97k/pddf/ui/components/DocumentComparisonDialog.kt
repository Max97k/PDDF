package com.max97k.pddf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.max97k.pddf.R
import com.max97k.pddf.domain.model.ComparisonResult
import java.util.Locale

@Composable
fun DocumentComparisonDialog(
    comparisonResult: ComparisonResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.title_document_comparison))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.label_property), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.label_doc_1), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.label_doc_2), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }

                HorizontalDivider()

                ComparisonRow(
                    label = stringResource(R.string.label_title),
                    val1 = comparisonResult.meta1.title,
                    val2 = comparisonResult.meta2.title,
                    isDifferent = comparisonResult.meta1.title != comparisonResult.meta2.title
                )
                ComparisonRow(
                    label = stringResource(R.string.label_author),
                    val1 = comparisonResult.meta1.author,
                    val2 = comparisonResult.meta2.author,
                    isDifferent = comparisonResult.meta1.author != comparisonResult.meta2.author
                )
                ComparisonRow(
                    label = stringResource(R.string.label_pages),
                    val1 = comparisonResult.meta1.pageCount.toString(),
                    val2 = comparisonResult.meta2.pageCount.toString(),
                    isDifferent = comparisonResult.pageCountDiffers
                )
                ComparisonRow(
                    label = stringResource(R.string.label_size_mb),
                    val1 = String.format(Locale.US, "%.2f", comparisonResult.meta1.fileSizeMb),
                    val2 = String.format(Locale.US, "%.2f", comparisonResult.meta2.fileSizeMb),
                    isDifferent = comparisonResult.fileSizeDiffers
                )
                ComparisonRow(
                    label = stringResource(R.string.label_doc_version),
                    val1 = comparisonResult.meta1.version.toString(),
                    val2 = comparisonResult.meta2.version.toString(),
                    isDifferent = comparisonResult.versionDiffers
                )
                ComparisonRow(
                    label = stringResource(R.string.label_encryption),
                    val1 = comparisonResult.meta1.encryptionMethod,
                    val2 = comparisonResult.meta2.encryptionMethod,
                    isDifferent = comparisonResult.encryptionDiffers
                )
                ComparisonRow(
                    label = stringResource(R.string.label_permissions),
                    val1 = getPermissionsText(comparisonResult.meta1.canPrint, comparisonResult.meta1.canCopy),
                    val2 = getPermissionsText(comparisonResult.meta2.canPrint, comparisonResult.meta2.canCopy),
                    isDifferent = comparisonResult.permissionsDiffer
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
private fun ComparisonRow(
    label: String,
    val1: String,
    val2: String,
    isDifferent: Boolean
) {
    val errorColor = MaterialTheme.colorScheme.error
    val textColor1 = if (isDifferent) errorColor else Color.Unspecified
    val textColor2 = if (isDifferent) errorColor else Color.Unspecified

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(text = val1, modifier = Modifier.weight(1f), color = textColor1, style = MaterialTheme.typography.bodySmall)
        Text(text = val2, modifier = Modifier.weight(1f), color = textColor2, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun getPermissionsText(canPrint: Boolean, canCopy: Boolean): String {
    val printStr = if (canPrint) stringResource(R.string.label_perm_print) else stringResource(R.string.label_perm_no_print)
    val copyStr = if (canCopy) stringResource(R.string.label_perm_copy) else stringResource(R.string.label_perm_no_copy)
    return "$printStr, $copyStr"
}
