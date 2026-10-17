package com.max97k.pddf.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import com.max97k.pddf.R
import com.max97k.pddf.data.ThemeMode
import com.max97k.pddf.data.DensityMode

/**
 * Settings ModalBottomSheet — consolidates all user-configurable preferences.
 *
 * Sections:
 *  1. Appearance — Theme & Density selection with M3 Segmented Buttons
 *  2. Notifications — Batch completion preferences
 *  3. Language — System default and 7 supported locales
 *  4. Privacy & Storage — Screenshot protection and SAF permissions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentTheme: ThemeMode,
    currentDensity: DensityMode,
    currentBatchNotificationPreference: com.max97k.pddf.data.NotificationPreference,
    screenshotProtectionEnabled: Boolean,
    currentAutoLockDuration: Long,
    onThemeSelected: (ThemeMode) -> Unit,
    onDensitySelected: (DensityMode) -> Unit,
    onBatchNotificationSelected: (com.max97k.pddf.data.NotificationPreference) -> Unit,
    onScreenshotProtectionToggled: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onAutoLockDurationSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.title_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // ── Appearance Section ─────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val themeEntries = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system_default),
                ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                ThemeMode.DARK   to stringResource(R.string.theme_dark),
                ThemeMode.AMOLED to stringResource(R.string.theme_amoled),
                ThemeMode.OLED_BLACK to stringResource(R.string.theme_oled_black)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                themeEntries.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = currentTheme == mode,
                        onClick = { onThemeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themeEntries.size),
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Density Section ─────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_section_density),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val densityEntries = listOf(
                DensityMode.COMPACT to stringResource(R.string.density_compact),
                DensityMode.STANDARD  to stringResource(R.string.density_standard),
                DensityMode.COMFORTABLE   to stringResource(R.string.density_comfortable)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                densityEntries.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = currentDensity == mode,
                        onClick = { onDensitySelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = densityEntries.size),
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Notifications Section ──────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_section_notifications),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.settings_notify_batch_completion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val notificationEntries = listOf(
                com.max97k.pddf.data.NotificationPreference.SOUND to stringResource(R.string.settings_notify_sound),
                com.max97k.pddf.data.NotificationPreference.VIBRATION to stringResource(R.string.settings_notify_vibration),
                com.max97k.pddf.data.NotificationPreference.SILENT to stringResource(R.string.settings_notify_silent)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                notificationEntries.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = currentBatchNotificationPreference == mode,
                        onClick = { onBatchNotificationSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = notificationEntries.size),
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Export Section ────────────────────────────────────────────
            Text(
                text = "Export Metadata",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_export_csv_title)) },
                supportingContent = { Text(stringResource(R.string.setting_export_csv_desc)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onExportCsv()
                        onDismiss()
                    }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_export_json_title)) },
                supportingContent = { Text(stringResource(R.string.setting_export_json_desc)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onExportJson()
                        onDismiss()
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // ── Language Section ───────────────────────────────────────────
            var expandedLanguage by remember { mutableStateOf(false) }
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val currentLangTag = if (currentLocales.isEmpty) "" else (currentLocales.toLanguageTags().split(",").firstOrNull() ?: "")
            val languageOptions = listOf(
                "" to stringResource(R.string.language_system_default),
                "en" to stringResource(R.string.language_en),
                "zh-TW" to stringResource(R.string.language_zh_tw),
                "zh-CN" to stringResource(R.string.language_zh_cn),
                "ja" to stringResource(R.string.language_ja),
                "es" to stringResource(R.string.language_es),
                "de" to stringResource(R.string.language_de),
                "fr" to stringResource(R.string.language_fr)
            )
            val currentLanguageLabel = languageOptions.firstOrNull { (tag, _) ->
                tag.equals(currentLangTag, ignoreCase = true)
            }?.second ?: stringResource(R.string.language_system_default)

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_section_language)) },
                supportingContent = { Text(currentLanguageLabel) },
                trailingContent = {
                    androidx.compose.material3.Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.settings_section_language)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedLanguage = !expandedLanguage }
            )

            if (expandedLanguage) {
                languageOptions.forEach { (tag, label) ->
                    val isSelected = tag.equals(currentLangTag, ignoreCase = true)
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            if (isSelected) {
                                Text(
                                    text = stringResource(R.string.symbol_checkmark),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp)
                            .clickable {
                                val localeList = if (tag.isEmpty()) {
                                    LocaleListCompat.getEmptyLocaleList()
                                } else {
                                    LocaleListCompat.forLanguageTags(tag)
                                }
                                AppCompatDelegate.setApplicationLocales(localeList)
                                expandedLanguage = false
                            }
                            .semantics { role = Role.RadioButton }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // ── Privacy Section ────────────────────────────────────────────

            Text(
                text = stringResource(R.string.settings_section_privacy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_screenshot_protection)) },
                supportingContent = { Text(stringResource(R.string.settings_screenshot_protection_desc)) },
                trailingContent = {
                    Switch(
                        checked = screenshotProtectionEnabled,
                        onCheckedChange = onScreenshotProtectionToggled
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onScreenshotProtectionToggled(!screenshotProtectionEnabled) }
            )

            // ── Storage Permissions Section ────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_section_storage),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            val context = LocalContext.current
            var showSafDialog by remember { mutableStateOf(false) }
            val persistedCount = remember(showSafDialog) {
                context.contentResolver.persistedUriPermissions.size
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_granted_folders_title)) },
                supportingContent = { Text("Total active permissions: $persistedCount") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSafDialog = true }
            )

            if (showSafDialog) {
                SafPermissionsDialog(
                    onDismiss = { showSafDialog = false }
                )
            }

            // Auto-Lock Section
            var expandedAutoLock by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_auto_lock_title)) },
                supportingContent = {
                    val durationText = when (currentAutoLockDuration) {
                        60000L -> "1 min"
                        300000L -> "5 min"
                        900000L -> "15 min"
                        else -> "Never"
                    }
                    Text(durationText)
                },
                trailingContent = {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.cd_select_auto_lock)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedAutoLock = !expandedAutoLock }
            )

            if (expandedAutoLock) {
                val autoLockOptions = listOf(
                    60000L to "1 min",
                    300000L to "5 min",
                    900000L to "15 min",
                    -1L to "Never"
                )

                autoLockOptions.forEach { (duration, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            if (currentAutoLockDuration == duration || (duration == -1L && currentAutoLockDuration <= 0)) {
                                Text(
                                    text = stringResource(R.string.symbol_checkmark),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp)
                            .clickable(
                                onClick = {
                                    onAutoLockDurationSelected(duration)
                                    expandedAutoLock = false
                                }
                            )
                            .semantics { role = Role.RadioButton }
                    )
                }
            }
        }
    }
}
