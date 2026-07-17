package com.instrument.domain.usecase

import com.instrument.domain.repository.LogRepository
import kotlinx.datetime.Clock

// 指定日数より古い計測ログを一括削除するユースケース
class DeleteOldLogsUseCase(
    private val logRepo: LogRepository,
    private val clock: Clock = Clock.System,
) {
    /**
     * [daysToKeep] 日分のログを保持し、それより古いレコードを削除する。
     * デフォルトは 30 日。
     *
     * @return 削除成功時は [Result.success(Unit)]、失敗時は [Result.failure]
     */
    suspend operator fun invoke(daysToKeep: Int = 30): Result<Unit> {
        require(daysToKeep > 0) { "daysToKeep は 1 以上でなければなりません: $daysToKeep" }
        val cutoffMs = clock.now().toEpochMilliseconds() - daysToKeep.toLong() * 24 * 60 * 60 * 1000
        return logRepo.deleteOlderThan(cutoffMs)
    }
}

