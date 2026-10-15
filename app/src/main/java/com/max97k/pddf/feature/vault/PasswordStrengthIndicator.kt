package com.max97k.pddf.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.max97k.pddf.util.PasswordStrengthEvaluator

@Composable
fun PasswordStrengthIndicator(password: String) {
    if (password.isEmpty()) return

    val strength = PasswordStrengthEvaluator.evaluate(password)
    if (strength == PasswordStrengthEvaluator.Strength.NONE) return

    val color = colorResource(id = strength.colorResId)
    val progress = when (strength) {
        PasswordStrengthEvaluator.Strength.WEAK -> 0.33f
        PasswordStrengthEvaluator.Strength.MEDIUM -> 0.66f
        PasswordStrengthEvaluator.Strength.STRONG -> 1.0f
        else -> 0f
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = strength.tipResId),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
