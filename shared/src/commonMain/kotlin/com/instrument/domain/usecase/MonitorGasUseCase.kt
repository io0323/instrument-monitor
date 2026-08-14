package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.model.Trend
import com.instrument.domain.repository.BleRepository
import com.instrument.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan

class MonitorGasUseCase(
    private val repo: BleRepository,
    // テストやデフォルト用途でも動作するようデフォルト実装を使用する
    private val settingsRepo: SettingsRepository = SettingsRepository.default(),
) {

    operator fun invoke(): Flow<GasStatus> = repo.observeSensorData()
        .scan(Pair(ArrayDeque<Float>(), null as GasStatus?)) { (deque, _), reading ->
            if (deque.size >= 5) deque.removeFirst()
            deque.addLast(reading.ppm)
            val trend = if (deque.size < 5) Trend.STABLE else {
                val diff = deque.last() - deque.first()
                when {
                    diff > +10f -> Trend.RISING
                    diff < -10f -> Trend.FALLING
                    else        -> Trend.STABLE
                }
            }
            // 設定に保存されたカスタム閾値を毎回読み取り、動的に GasLevel を決定する
            val settings = settingsRepo.settings.value
            val level = GasLevel.fromPpm(
                reading.ppm,
                settings.warningThresholdPpm.toFloat(),
                settings.dangerThresholdPpm.toFloat(),
                settings.criticalThresholdPpm.toFloat(),
            )
            Pair(deque, GasStatus(reading, level, trend))
        }
        .mapNotNull { it.second }
}

