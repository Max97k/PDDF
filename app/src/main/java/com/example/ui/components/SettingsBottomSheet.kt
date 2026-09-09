package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ThemeMode

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
                .navigationBarsPadding()
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
                ThemeMode.DARK   to stringResource(R.string.theme_dark)
            )
            themeEntries.forEach { (mode, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = {
                        if (currentTheme == mode) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onScreenshotProtectionToggled(!screenshotProtectionEnabled) }
            )
        }
    }
}
