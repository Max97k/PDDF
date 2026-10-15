package com.max97k.pddf.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.max97k.pddf.R
import com.max97k.pddf.data.ThemeMode

/**
 * Settings ModalBottomSheet — consolidates all user-configurable preferences.
 *
 * Sections:
 *  1. Appearance — Theme selection (moved here from ThemeDropdownMenu)
 *  2. Privacy    — Screenshot protection toggle (FLAG_SECURE)
 *
 * To add future settings append more [ListItem] / section blocks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentTheme: ThemeMode,
    screenshotProtectionEnabled: Boolean,
    onThemeSelected: (ThemeMode) -> Unit,
    onScreenshotProtectionToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.title_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // ── Appearance Section ─────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            val themeEntries = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system_default),
                ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                ThemeMode.DARK   to stringResource(R.string.theme_dark),
                ThemeMode.OLED_BLACK to stringResource(R.string.theme_oled_black)
            )
            themeEntries.forEach { (mode, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = {
                        if (currentTheme == mode) {
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
                        .clickable(
                            onClick = {
                                onThemeSelected(mode)
                                onDismiss()
                            }
                        )
                        .semantics { role = Role.RadioButton }
                )
            }


            // ── Language Section ─────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_section_language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val currentLanguage = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.toLanguageTag() ?: ""

            val languageEntries = listOf(
                "" to stringResource(R.string.language_system_default),
                "en" to stringResource(R.string.language_en),
                "zh-TW" to stringResource(R.string.language_zh_tw),
                "zh-CN" to stringResource(R.string.language_zh_cn),
                "ja" to stringResource(R.string.language_ja),
                "es" to stringResource(R.string.language_es),
                "de" to stringResource(R.string.language_de),
                "fr" to stringResource(R.string.language_fr)
            )

            languageEntries.forEach { (languageTag, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = {
                        val isSelected = if (languageTag.isEmpty()) currentLanguage.isEmpty() else currentLanguage.equals(languageTag, ignoreCase = true) || (currentLanguage.startsWith("zh", ignoreCase = true) && languageTag.startsWith("zh", ignoreCase = true) && currentLanguage.contains("CN", ignoreCase = true) == languageTag.contains("CN", ignoreCase = true))
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
                        .clickable(
                            onClick = {
                                val locales = if (languageTag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(languageTag)
                                AppCompatDelegate.setApplicationLocales(locales)
                            }
                        )
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Privacy Section ────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

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
        }
    }
}
