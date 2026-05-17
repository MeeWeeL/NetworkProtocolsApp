package ru.meeweel.network_protocols_app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme

enum class NpButtonType {
    Primary,
    Secondary,
}

@Composable
fun NpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: NpButtonType = NpButtonType.Primary,
    enabled: Boolean = true,
) {
    val containerColor = when (type) {
        NpButtonType.Primary -> NpTheme.colorScheme.accent
        NpButtonType.Secondary -> NpTheme.colorScheme.surfacePrimary
    }
    val contentColor = when (type) {
        NpButtonType.Primary -> NpTheme.colorScheme.textOnAccent
        NpButtonType.Secondary -> NpTheme.colorScheme.textPrimary
    }

    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = NpTheme.colorScheme.accentMuted,
            disabledContentColor = NpTheme.colorScheme.textSecondary,
        ),
        border = when (type) {
            NpButtonType.Primary -> null
            NpButtonType.Secondary -> BorderStroke(1.dp, NpTheme.colorScheme.border)
        },
    ) {
        Text(
            text = text,
            style = NpTheme.typography.button,
        )
    }
}

@Composable
@NpPreview
private fun PreviewNpButton() {
    NpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NpButton(
                text = "Полный тест",
                onClick = {},
            )
            NpButton(
                text = "По протоколам",
                onClick = {},
                type = NpButtonType.Secondary,
            )
        }
    }
}
