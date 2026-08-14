package com.instrument.domain.usecase

import com.instrument.domain.model.AppSettings
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
     * [settings] が指定された場合はカスタム閾値で peakLevel を算出する。
     */
    fun compute(readings: List<SensorReading>, settings: AppSettings = AppSettings()): SessionStats? {
        if (readings.isEmpty()) return null

        val ppms      = readings.map { it.ppm }
        val minPpm    = ppms.min()
        val maxPpm    = ppms.max()
        val avgPpm    = ppms.average().toFloat()
        // カスタム閾値を使って peakLevel を決定する
        val peakLevel = GasLevel.fromPpm(
            maxPpm,
            settings.warningThresholdPpm.toFloat(),
            settings.dangerThresholdPpm.toFloat(),
            settings.criticalThresholdPpm.toFloat(),
        )

        return SessionStats(
            minPpm       = minPpm,
            maxPpm       = maxPpm,
            avgPpm       = avgPpm,
            peakLevel    = peakLevel,
            readingCount = readings.size,
        )
    }
}
