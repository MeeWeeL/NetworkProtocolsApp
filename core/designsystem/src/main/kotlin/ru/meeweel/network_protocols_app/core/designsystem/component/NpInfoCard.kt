package ru.meeweel.network_protocols_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme

@Composable
fun NpInfoCard(
    title: String,
    body: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = NpTheme.colorScheme.surfacePrimary,
                shape = RoundedCornerShape(24.dp),
            )
            .border(
                width = 1.dp,
                color = NpTheme.colorScheme.border,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = NpTheme.typography.sectionTitle,
            color = NpTheme.colorScheme.textPrimary,
        )
        Text(
            text = body,
            style = NpTheme.typography.body,
            color = NpTheme.colorScheme.textPrimary,
        )
        caption?.let {
            Text(
                text = it,
                style = NpTheme.typography.caption,
                color = NpTheme.colorScheme.textSecondary,
            )
        }
    }
}

@Composable
@NpPreview
private fun PreviewNpInfoCard() {
    NpTheme {
        NpInfoCard(
            modifier = Modifier.padding(16.dp),
            title = "Стенд доступен",
            body = "HTTP 8080, gRPC 9090",
            caption = "REST, SOAP, GraphQL, WebSocket, gRPC",
        )
    }
}
