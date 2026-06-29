package com.instrument.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// CSV データを端末のダウンロードフォルダへ書き出すユーティリティ
class CsvExporter(private val context: Context) {

    suspend fun export(csvContent: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "instrument_${System.currentTimeMillis()}.csv"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                resolver.openOutputStream(uri)!!.use { it.write(csvContent.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                file.writeText(csvContent)
                Uri.fromFile(file)
            }
        }
    }
}
