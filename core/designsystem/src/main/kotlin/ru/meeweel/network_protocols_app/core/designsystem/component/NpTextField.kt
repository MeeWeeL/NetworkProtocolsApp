package ru.meeweel.network_protocols_app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme

@Composable
fun NpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = NpTheme.typography.body,
        label = {
            Text(
                text = label,
                style = NpTheme.typography.caption,
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = NpTheme.colorScheme.surfacePrimary,
            unfocusedContainerColor = NpTheme.colorScheme.surfacePrimary,
            focusedBorderColor = NpTheme.colorScheme.accent,
            unfocusedBorderColor = NpTheme.colorScheme.border,
            focusedTextColor = NpTheme.colorScheme.textPrimary,
            unfocusedTextColor = NpTheme.colorScheme.textPrimary,
            focusedLabelColor = NpTheme.colorScheme.accent,
            unfocusedLabelColor = NpTheme.colorScheme.textSecondary,
            cursorColor = NpTheme.colorScheme.accent,
        ),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
@NpPreview
private fun PreviewNpTextField() {
    NpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NpTextField(
                value = "192.168.1.140",
                onValueChange = {},
                label = "Хост backend",
            )
        }
    }
}
