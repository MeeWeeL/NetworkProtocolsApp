package ru.meeweel.network_protocols_app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme

@Composable
fun NpChipButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NpTheme.colorScheme.border),
    ) {
        Text(
            text = text,
            style = NpTheme.typography.caption,
            color = NpTheme.colorScheme.textPrimary,
        )
    }
}

@Composable
@NpPreview
@OptIn(ExperimentalLayoutApi::class)
private fun PreviewNpChipButton() {
    NpTheme {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NpChipButton(
                text = "S1 Короткий ответ",
                onClick = {},
            )
            NpChipButton(
                text = "gRPC",
                onClick = {},
            )
        }
    }
}
