package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.ThemeMode

@Composable
fun ThemeDropdownMenu(
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.Palette,
                contentDescription = stringResource(R.string.content_desc_theme_settings)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_system_default)) },
                onClick = {
                    onThemeSelected(ThemeMode.SYSTEM)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_light)) },
                onClick = {
                    onThemeSelected(ThemeMode.LIGHT)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_dark)) },
                onClick = {
                    onThemeSelected(ThemeMode.DARK)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_amoled)) },
                onClick = {
                    onThemeSelected(ThemeMode.AMOLED)
                    expanded = false
                }
            )
        }
    }
}
