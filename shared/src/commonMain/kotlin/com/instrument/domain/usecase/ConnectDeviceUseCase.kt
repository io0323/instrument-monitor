package com.instrument.domain.usecase

import com.instrument.domain.model.ReconnectState
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class ConnectDeviceUseCase(private val repo: BleRepository) {

    /** 通常接続: BleConnectionState を Flow で返す */
    operator fun invoke(deviceId: String): Flow<BleConnectionState> = repo.connect(deviceId)

    /**
     * 自動再接続: 指数バックオフで最大 [maxAttempts] 回まで再接続を試みる。
     * - 各試行前に 2^attempt × 1000ms のディレイを挿入する (1s, 2s, 4s, 8s, 16s)
     * - 成功時: [ReconnectState.Connected] を emit して終了
     * - 全試行失敗時: [ReconnectState.Failed] を emit して終了
     */
    fun reconnect(deviceId: String, maxAttempts: Int = 5): Flow<ReconnectState> = flow {
        for (attempt in 1..maxAttempts) {
            // 試行中状態を通知
            emit(ReconnectState.Reconnecting(attempt = attempt, maxAttempts = maxAttempts))

            // 指数バックオフ: 2^attempt × 1000ms (attempt=1→2s, 2→4s, ...)
            // ただし最初の試行は 1s (2^0 × 1000ms) とする
            val delayMs = (1L shl (attempt - 1)) * 1000L
            delay(delayMs)

            // リポジトリへ再接続を要求し、結果を取得する
            val success = repo.reconnect(deviceId).first()
            if (success) {
                emit(ReconnectState.Connected)
                return@flow
            }
        }
        // 最大試行回数に達しても成功しなかった場合
        emit(ReconnectState.Failed)
    }
}
