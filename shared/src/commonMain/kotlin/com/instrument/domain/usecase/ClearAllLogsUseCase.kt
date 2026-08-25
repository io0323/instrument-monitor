package com.instrument.domain.usecase

import com.instrument.domain.repository.LogRepository

/**
 * 全計測ログを一括削除するユースケース。
 * [LogRepository.deleteOlderThan] に [Long.MAX_VALUE] を渡すことで
 * timestamp が現実的な値を取る全レコードを削除する。
 */
class ClearAllLogsUseCase(private val logRepo: LogRepository) {

    /**
     * すべての計測ログを削除する。
     *
     * @return 削除成功時は [Result.success(Unit)]、失敗時は [Result.failure]
     */
    suspend operator fun invoke(): Result<Unit> =
        logRepo.deleteOlderThan(Long.MAX_VALUE)
}
