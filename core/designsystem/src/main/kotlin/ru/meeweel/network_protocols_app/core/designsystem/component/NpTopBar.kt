package ru.meeweel.network_protocols_app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme

@Composable
fun NpTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onClickBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (onClickBack != null) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp),
                onClick = onClickBack,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = NpTheme.colorScheme.textPrimary,
                )
            }
        }

        Text(
            modifier = Modifier.align(Alignment.Center),
            text = title,
            style = NpTheme.typography.screenTitle,
            color = NpTheme.colorScheme.textPrimary,
        )
    }
}

@Composable
@NpPreview
private fun PreviewNpTopBar() {
    NpTheme {
        NpTopBar(
            title = "По протоколам",
            onClickBack = {},
        )
    }
}
