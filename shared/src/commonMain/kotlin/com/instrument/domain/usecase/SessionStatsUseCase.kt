package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.SensorReading
import com.instrument.domain.model.SessionStats

/**
 * セッション中の [SensorReading] リストから統計サマリを計算するユースケース。
 * 外部依存なしの純粋関数として実装し、テスト容易性を確保する。
 */
class SessionStatsUseCase {

    /**
     * [readings] が空の場合は null を返す。
     * それ以外は min / max / avg / peakLevel / readingCount を計算して返す。
     */
    fun compute(readings: List<SensorReading>): SessionStats? {
        if (readings.isEmpty()) return null

        val ppms     = readings.map { it.ppm }
        val minPpm   = ppms.min()
        val maxPpm   = ppms.max()
        val avgPpm   = ppms.average().toFloat()
        val peakLevel = GasLevel.fromPpm(maxPpm)

        return SessionStats(
            minPpm       = minPpm,
            maxPpm       = maxPpm,
            avgPpm       = avgPpm,
            peakLevel    = peakLevel,
            readingCount = readings.size,
        )
    }
}
