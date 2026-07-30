package com.instrument.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.instrument.data.export.CsvExporter

@Composable
actual fun rememberCsvSaver(): CsvSaver {
    val context = LocalContext.current
    val exporter = remember(context) { CsvExporter(context) }
    return remember(exporter) {
        // Uri → String 変換して ViewModel の onSave 型 (Result<String>) に合わせる
        CsvSaver { csv -> exporter.export(csv).map { it.toString() } }
    }
}
