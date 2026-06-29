package com.instrument.domain.repository

import com.instrument.domain.model.GeoTaggedReading
import kotlinx.coroutines.flow.Flow

// 計測ログの永続化インターフェース（SQLDelight で実装）
interface LogRepository {
    suspend fun save(reading: GeoTaggedReading): Result<Long>
    fun getAllReadings(): Flow<List<GeoTaggedReading>>
    fun getDangerousReadings(): Flow<List<GeoTaggedReading>>
    suspend fun deleteOlderThan(epochMs: Long): Result<Unit>
    suspend fun exportCsv(): Result<String>
}
