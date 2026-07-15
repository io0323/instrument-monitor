package com.instrument.domain.usecase

import com.instrument.domain.repository.LogRepository

// CSV 書き出しを担当するユースケース
// LogRepository.exportCsv() をラップし、Result<String> で呼び出し元に返す
class ExportCsvUseCase(private val logRepo: LogRepository) {

    // CSV 文字列を取得して返す。
    // 取得後の「保存先への書き込み」は onSave コールバックに委譲する（プラットフォーム差異を吸収）
    suspend operator fun invoke(onSave: suspend (csv: String) -> Result<String>): Result<String> =
        logRepo.exportCsv()
            .mapCatching { csv -> onSave(csv).getOrThrow() }
}

