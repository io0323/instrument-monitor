package com.instrument.domain.repository

import com.instrument.domain.model.GeoTaggedReading
import kotlinx.coroutines.flow.Flow

// 計測ログの永続化インターフェース
// Phase 7 で実装: SQLDelightLogRepository
interface LogRepository {
    // 全ログを Flow で返す
    fun observeAll(): Flow<List<GeoTaggedReading>>

    // ログを保存する
    suspend fun save(reading: GeoTaggedReading)

    // 全ログを削除する
    suspend fun clearAll()
}
