package com.instrument.data.repository

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// LogRepository のインメモリ偽実装。
// 契約テストのリファレンス実装として使用し、実装の正しい振る舞いを定義する。
class FakeLogRepository : LogRepository {

    // 保存された全レコード（挿入順）
    private val store = mutableListOf<GeoTaggedReading>()

    // Flow の更新通知用
    private val storeFlow = MutableStateFlow<List<GeoTaggedReading>>(emptyList())

    // 自動採番カウンタ（SQLite AUTOINCREMENT と同じ単調増加）
    private var nextId = 1L

    override suspend fun save(reading: GeoTaggedReading): Result<Long> = runCatching {
        val id = nextId++
        val withId = reading.copy(id = id)
        store.add(withId)
        storeFlow.value = store.toList()
        id
    }

    // timestamp DESC 順で返す（SQLDelight の selectAll と同一仕様）
    override fun getAllReadings(): Flow<List<GeoTaggedReading>> =
        storeFlow.map { list -> list.sortedByDescending { it.reading.timestamp } }

    // DANGER / CRITICAL のみ timestamp DESC 順で返す（selectDangerous と同一仕様）
    override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> =
        storeFlow.map { list ->
            list.filter { it.level in setOf(GasLevel.DANGER, GasLevel.CRITICAL) }
                .sortedByDescending { it.reading.timestamp }
        }

    override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = runCatching {
        store.removeAll { it.reading.timestamp < epochMs }
        storeFlow.value = store.toList()
    }

    // exportCsv は SqlDelightLogRepository と同一のヘッダ・フォーマットで出力
    override suspend fun exportCsv(): Result<String> = runCatching {
        val rows = store.sortedByDescending { it.reading.timestamp }
        buildString {
            appendLine("timestamp,ppm,temperature,humidity,lat,lng,level")
            rows.forEach { r ->
                appendLine("${r.reading.timestamp},${r.reading.ppm},${r.reading.temperature},${r.reading.humidity},${r.lat},${r.lng},${r.level.name}")
            }
        }
    }

    // テスト用ユーティリティ
    fun clear() {
        store.clear()
        nextId = 1L
        storeFlow.value = emptyList()
    }

    val size: Int get() = store.size
}
