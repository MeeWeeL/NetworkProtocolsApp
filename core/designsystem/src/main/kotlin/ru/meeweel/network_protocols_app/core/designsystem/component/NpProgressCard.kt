package ru.meeweel.network_protocols_app.core.designsystem.component

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import ru.meeweel.network_protocols_app.core.designsystem.compose.NpPreview
import ru.meeweel.network_protocols_app.core.designsystem.theme.NpTheme

private const val TELEGRAM_PACKAGE = "org.telegram.messenger"

@Composable
fun NpProgressCard(
    title: String,
    stateLabel: String,
    details: String,
    progress: Float,
    progressLabel: String,
    elapsedTimeLabel: String? = null,
    summary: String? = null,
    reportNotes: List<String> = emptyList(),
    reportItems: List<NpProgressReportItem> = emptyList(),
    reportActionsEnabled: Boolean = true,
    exportMeasuredRuns: Int? = null,
    exportReusePersistentConnections: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val telegramInstalled = remember(context) { context.isPackageInstalled(TELEGRAM_PACKAGE) }
    var showReportItems by remember(reportItems.size, reportActionsEnabled) { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "np_progress_card",
    )
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
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = NpTheme.typography.sectionTitle,
            color = NpTheme.colorScheme.textPrimary,
        )
        Text(
            text = stateLabel,
            style = NpTheme.typography.body,
            color = NpTheme.colorScheme.textPrimary,
        )
        if (!summary.isNullOrBlank()) {
            Text(
                text = "Краткий вывод",
                style = NpTheme.typography.caption,
                color = NpTheme.colorScheme.textPrimary,
            )
            SelectionContainer {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = summary,
                    style = NpTheme.typography.body,
                    color = NpTheme.colorScheme.textSecondary,
                )
            }
        }
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = { animatedProgress },
            color = NpTheme.colorScheme.success,
            trackColor = NpTheme.colorScheme.accentMuted,
        )
        Text(
            text = progressLabel,
            style = NpTheme.typography.caption,
            color = NpTheme.colorScheme.textSecondary,
        )
        if (!elapsedTimeLabel.isNullOrBlank()) {
            Text(
                text = "Длительность: $elapsedTimeLabel",
                style = NpTheme.typography.caption,
                color = NpTheme.colorScheme.textSecondary,
            )
        }
        SelectionContainer {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = details,
                style = NpTheme.typography.body,
                color = NpTheme.colorScheme.textSecondary,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NpChipButton(
                text = "Скопировать отчет",
                enabled = reportActionsEnabled,
                onClick = {
                    clipboardManager.setText(
                        AnnotatedString(
                            buildCompactReportText(
                                title = title,
                                stateLabel = stateLabel,
                                progressLabel = progressLabel,
                                elapsedTimeLabel = elapsedTimeLabel,
                                details = details,
                                summary = summary,
                                reportNotes = reportNotes,
                                reportItems = reportItems,
                            ),
                        ),
                    )
                },
            )
            NpChipButton(
                text = "Выгрузить отчет",
                enabled = reportActionsEnabled,
                onClick = {
                    context.shareReportFile(
                        title = title,
                        reportText = buildCompactReportText(
                            title = title,
                            stateLabel = stateLabel,
                            progressLabel = progressLabel,
                            elapsedTimeLabel = elapsedTimeLabel,
                            details = details,
                            summary = summary,
                            reportNotes = reportNotes,
                            reportItems = reportItems,
                        ),
                        filePrefix = "report",
                        chooserTitle = "Выгрузить отчет",
                        measuredRuns = exportMeasuredRuns,
                        reusePersistentConnections = exportReusePersistentConnections,
                    )
                },
            )
            NpChipButton(
                text = "Выгрузить полный отчет",
                enabled = reportActionsEnabled,
                onClick = {
                    context.shareReportFile(
                        title = title,
                        reportText = buildFullReportText(
                            title = title,
                            stateLabel = stateLabel,
                            progressLabel = progressLabel,
                            elapsedTimeLabel = elapsedTimeLabel,
                            details = details,
                            summary = summary,
                            reportNotes = reportNotes,
                            reportItems = reportItems,
                        ),
                        filePrefix = "full_report",
                        chooserTitle = "Выгрузить полный отчет",
                        measuredRuns = exportMeasuredRuns,
                        reusePersistentConnections = exportReusePersistentConnections,
                    )
                },
            )
            if (telegramInstalled) {
                NpChipButton(
                    text = "Полный отчет в Telegram",
                    enabled = reportActionsEnabled,
                    onClick = {
                        context.shareReportToTelegram(
                            title = title,
                            reportText = buildFullReportText(
                                title = title,
                                stateLabel = stateLabel,
                                progressLabel = progressLabel,
                                elapsedTimeLabel = elapsedTimeLabel,
                                details = details,
                                summary = summary,
                                reportNotes = reportNotes,
                                reportItems = reportItems,
                            ),
                            filePrefix = "full_report",
                            measuredRuns = exportMeasuredRuns,
                            reusePersistentConnections = exportReusePersistentConnections,
                        )
                    },
                )
            }
        }
        if (reportNotes.isNotEmpty()) {
            Text(
                text = "Порядок и заметки",
                style = NpTheme.typography.caption,
                color = NpTheme.colorScheme.textPrimary,
            )
            SelectionContainer {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = reportNotes.joinToString(separator = "\n\n"),
                    style = NpTheme.typography.body,
                    color = NpTheme.colorScheme.textSecondary,
                )
            }
        }
        if (reportItems.isNotEmpty() && reportActionsEnabled) {
            NpChipButton(
                text = if (showReportItems) {
                    "Скрыть завершенные шаги"
                } else {
                    "Показать завершенные шаги (${reportItems.size})"
                },
                onClick = {
                    showReportItems = !showReportItems
                },
            )
        }
        if (showReportItems && reportItems.isNotEmpty() && reportActionsEnabled) {
            Text(
                text = "Завершенные шаги",
                style = NpTheme.typography.caption,
                color = NpTheme.colorScheme.textPrimary,
            )
            val expandedStates = remember(reportItems) { mutableStateMapOf<Int, Boolean>() }
            reportItems.forEachIndexed { index, item ->
                val isExpanded = expandedStates[index] == true
                ExpandableReportItem(
                    index = index + 1,
                    item = item,
                    isExpanded = isExpanded,
                    onToggle = {
                        expandedStates[index] = !isExpanded
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpandableReportItem(
    index: Int,
    item: NpProgressReportItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = NpTheme.colorScheme.background,
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = 1.dp,
                color = NpTheme.colorScheme.border,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$index. ${item.title}",
            style = NpTheme.typography.body,
            color = NpTheme.colorScheme.textPrimary,
        )
        Text(
            text = item.summary,
            style = NpTheme.typography.caption,
            color = NpTheme.colorScheme.textSecondary,
        )
        NpChipButton(
            text = if (isExpanded) "Скрыть детали" else "Показать детали",
            onClick = onToggle,
        )
        if (isExpanded) {
            SelectionContainer {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = item.detailsProvider(),
                    style = NpTheme.typography.body,
                    color = NpTheme.colorScheme.textSecondary,
                )
            }
        }
    }
}

private fun buildCompactReportText(
    title: String,
    stateLabel: String,
    progressLabel: String,
    elapsedTimeLabel: String?,
    details: String,
    summary: String?,
    reportNotes: List<String>,
    reportItems: List<NpProgressReportItem>,
): String {
    return buildString {
        appendLine("Отчет")
        appendLine("Тест: $title")
        appendLine("Статус: $stateLabel")
        appendLine("Устройство: ${buildDeviceLabel()}")
        appendLine("Android: ${buildAndroidLabel()}")
        appendLine("Прогресс: $progressLabel")
        if (!elapsedTimeLabel.isNullOrBlank()) {
            appendLine("Длительность: $elapsedTimeLabel")
        }
        appendLine("Итог: $details")
        if (!summary.isNullOrBlank()) {
            appendLine("Краткий вывод: $summary")
        }
        if (reportNotes.isNotEmpty()) {
            appendLine()
            appendLine("Порядок:")
            reportNotes.forEachIndexed { index, note ->
                if (index > 0) {
                    appendLine()
                }
                appendLine(note)
            }
        }
        if (reportItems.isNotEmpty()) {
            appendLine()
            appendLine("Шаги:")
            reportItems.forEachIndexed { index, item ->
                appendLine("${index + 1}. ${item.title} — ${item.summary}")
                if (index != reportItems.lastIndex) {
                    appendLine()
                }
            }
        }
    }
}

private fun buildFullReportText(
    title: String,
    stateLabel: String,
    progressLabel: String,
    elapsedTimeLabel: String?,
    details: String,
    summary: String?,
    reportNotes: List<String>,
    reportItems: List<NpProgressReportItem>,
): String {
    return buildString {
        appendLine("Отчет")
        appendLine("Заголовок: $title")
        appendLine("Статус: $stateLabel")
        appendLine("Устройство: ${buildDeviceLabel()}")
        appendLine("Android: ${buildAndroidLabel()}")
        appendLine("Прогресс: $progressLabel")
        if (!elapsedTimeLabel.isNullOrBlank()) {
            appendLine("Длительность: $elapsedTimeLabel")
        }
        appendLine("Детали: $details")
        if (!summary.isNullOrBlank()) {
            appendLine("Краткий вывод: $summary")
        }
        if (reportNotes.isNotEmpty()) {
            appendLine()
            appendLine("Порядок и заметки:")
            reportNotes.forEachIndexed { index, note ->
                if (index > 0) {
                    appendLine()
                }
                appendLine(note)
            }
        }
        if (reportItems.isNotEmpty()) {
            appendLine()
            appendLine("Завершенные шаги:")
            reportItems.forEachIndexed { index, item ->
                append(index + 1)
                append(". ")
                appendLine(item.title)
                appendLine(item.fullDetailsProvider?.invoke() ?: item.detailsProvider())
                if (index != reportItems.lastIndex) {
                    appendLine()
                }
            }
        }
    }
}

private fun buildDeviceLabel(): String {
    val manufacturer = Build.MANUFACTURER?.trim().takeUnless { it.isNullOrEmpty() } ?: "Неизвестный производитель"
    val model = Build.MODEL?.trim().takeUnless { it.isNullOrEmpty() } ?: "Неизвестная модель"
    return if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "$manufacturer $model"
    }
}

private fun buildAndroidLabel(): String {
    val release = Build.VERSION.RELEASE?.trim().takeUnless { it.isNullOrEmpty() } ?: "неизвестно"
    return "$release (API ${Build.VERSION.SDK_INT})"
}

private fun String.toSafeFilePart(fallback: String = "report"): String {
    return lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { fallback }
}

private fun buildReportFileName(
    filePrefix: String,
    measuredRuns: Int?,
    reusePersistentConnections: Boolean?,
): String {
    val deviceTag = Build.MANUFACTURER.toSafeFilePart(
        fallback = Build.MODEL.toSafeFilePart(fallback = "device"),
    )
    val runsTag = measuredRuns?.toString() ?: "unknown"
    val baseName = "${filePrefix}_${deviceTag}_${runsTag}"
    val modeTag = when (reusePersistentConnections) {
        true -> "h_series"
        false -> "h_req"
        null -> null
    }
    return listOfNotNull(baseName, modeTag).joinToString("_") + ".txt"
}

private fun Context.buildReportShareIntent(
    title: String,
    fileName: String,
    uri: Uri,
    mimeType: String,
): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(contentResolver, fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun Context.resolveShareActivityForPackage(
    title: String,
    fileName: String,
    uri: Uri,
    mimeType: String,
    packageName: String,
): String? {
    return packageManager.resolveActivity(
        buildReportShareIntent(
            title = title,
            fileName = fileName,
            uri = uri,
            mimeType = mimeType,
        ).apply {
            `package` = packageName
        },
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.name
}

private fun Context.isPackageInstalled(packageName: String): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }.isSuccess
}

private fun Context.shareReportFile(
    title: String,
    reportText: String,
    filePrefix: String,
    chooserTitle: String,
    measuredRuns: Int?,
    reusePersistentConnections: Boolean?,
) {
    runCatching {
        val fileName = buildReportFileName(
            filePrefix = filePrefix,
            measuredRuns = measuredRuns,
            reusePersistentConnections = reusePersistentConnections,
        )
        val uri = createShareUri(
            fileName = fileName,
            reportText = reportText,
        )
        val shareIntent = buildReportShareIntent(
            title = title,
            fileName = fileName,
            uri = uri,
            mimeType = "text/plain",
        )
        val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
            clipData = ClipData.newUri(contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(chooserIntent)
    }.onFailure {
        Toast.makeText(this, "Не удалось выгрузить отчет.", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.shareReportToTelegram(
    title: String,
    reportText: String,
    filePrefix: String,
    measuredRuns: Int?,
    reusePersistentConnections: Boolean?,
) {
    runCatching {
        val fileName = buildReportFileName(
            filePrefix = filePrefix,
            measuredRuns = measuredRuns,
            reusePersistentConnections = reusePersistentConnections,
        )
        val uri = createFileProviderShareUri(
            fileName = fileName,
            reportText = reportText,
        )
        val telegramMimeType = listOf(
            "application/octet-stream",
            "text/plain",
            "*/*",
        ).firstOrNull { mimeType ->
            resolveShareActivityForPackage(
                title = title,
                fileName = fileName,
                uri = uri,
                mimeType = mimeType,
                packageName = TELEGRAM_PACKAGE,
            ) != null
        } ?: "application/octet-stream"
        val telegramActivity = resolveShareActivityForPackage(
            title = title,
            fileName = fileName,
            uri = uri,
            mimeType = telegramMimeType,
            packageName = TELEGRAM_PACKAGE,
        )
        requireNotNull(telegramActivity) { "Telegram activity is not available" }
        val telegramIntent = buildReportShareIntent(
            title = title,
            fileName = fileName,
            uri = uri,
            mimeType = telegramMimeType,
        ).apply {
            setClassName(TELEGRAM_PACKAGE, telegramActivity)
        }
        grantUriPermission(
            TELEGRAM_PACKAGE,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        startActivity(telegramIntent)
    }.onFailure {
        Toast.makeText(this, "Не удалось открыть Telegram.", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.createFileProviderShareUri(
    fileName: String,
    reportText: String,
): Uri {
    val directory = File(cacheDir, "shared_reports").apply {
        mkdirs()
    }
    val file = File(directory, fileName)
    file.writeText(reportText)
    return FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        file,
    )
}

private fun Context.createShareUri(
    fileName: String,
    reportText: String,
): Uri {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/NetworkProtocolsApp",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        )
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                requireNotNull(writer) { "Unable to open output stream for $uri" }
                writer.write(reportText)
            }
            contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
        } catch (error: Throwable) {
            contentResolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    return createFileProviderShareUri(
        fileName = fileName,
        reportText = reportText,
    )
}

@Composable
@NpPreview
private fun PreviewNpProgressCard() {
    NpTheme {
        NpProgressCard(
            modifier = Modifier.padding(16.dp),
            title = "REST",
            stateLabel = "Идет выполнение",
            details = "S2 Большой объект • h_req • 84 мс клиент • 11 мс сервер",
            summary = "Текущий лидер по S2 h_req: SOAP.",
            progress = 0.6f,
            progressLabel = "Запрос 62 из 206 • серия 4 из 12",
            reportNotes = listOf("Случайный порядок серий для REST (seed=42): S2 Большой объект h_req"),
            reportItems = listOf(
                NpProgressReportItem(
                    title = "REST • S2 Большой объект • h_req",
                    summary = "R=100 • медиана 20,50 мс • p95 28,88 мс • TP 37,59 оп/с",
                    detailsProvider = { "Методика: Калибровка таймеров 32 проверок, прогрев 3, измеряемых повторов 100" },
                ),
            ),
        )
    }
}
