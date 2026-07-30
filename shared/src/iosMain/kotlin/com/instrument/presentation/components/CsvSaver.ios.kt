package com.instrument.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.Clock
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

// iOS: Documents ディレクトリへ CSV を書き出す
// Phase 8 で UIActivityViewController による共有シートへの置き換えを予定
@Composable
actual fun rememberCsvSaver(): CsvSaver {
    return remember {
        CsvSaver { csv ->
            runCatching {
                val docsDir = NSSearchPathForDirectoriesInDomains(
                    NSDocumentDirectory, NSUserDomainMask, true
                ).first() as String
                val fileName = "instrument_${Clock.System.now().toEpochMilliseconds()}.csv"
                val filePath = "$docsDir/$fileName"
                @Suppress("CAST_NEVER_SUCCEEDS")
                val nsStr = csv as NSString
                nsStr.writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
                filePath
            }
        }
    }
}
