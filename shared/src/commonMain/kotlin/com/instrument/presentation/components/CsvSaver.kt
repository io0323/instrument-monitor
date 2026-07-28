package com.instrument.presentation.components

import androidx.compose.runtime.Composable

// CSV 保存操作を抽象化する関数インタフェース
// expect/actual で suspend ラムダ型を返す際の KMP 型推論問題を回避するために使用
fun interface CsvSaver {
    suspend fun save(csv: String): Result<String>
}

// プラットフォームごとの CSV 保存実装を返す Composable
// Android: MediaStore (Downloads フォルダ)、iOS: Documents ディレクトリ
@Composable
expect fun rememberCsvSaver(): CsvSaver
