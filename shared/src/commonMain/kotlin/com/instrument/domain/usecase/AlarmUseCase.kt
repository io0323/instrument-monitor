package com.instrument.domain.usecase

import com.instrument.data.alarm.AlarmController
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

// ガス状態を監視し、レベルに応じてアラームを制御するユースケース
class AlarmUseCase(
    private val monitor: MonitorGasUseCase,
    private val controller: AlarmController,
) {
    private var lastAlarmTime  = 0L
    private var lastAlarmLevel: GasLevel? = null

    fun observe(): Flow<GasStatus> = monitor().onEach { handleAlarm(it) }

    fun dismiss() = controller.dismiss()

    private fun handleAlarm(status: GasStatus) {
        if (status.level == GasLevel.SAFE) {
            controller.dismiss()
            // SAFE に戻ったらアラーム履歴をリセットし、次の上昇時に即座に再発報できるようにする
            lastAlarmLevel = null
            lastAlarmTime  = 0L
            return
        }
        val now = Clock.System.now().toEpochMilliseconds()
        // 同じレベルのアラームは30秒間隔で抑制
        if (status.level == lastAlarmLevel && now - lastAlarmTime < 30_000L) return
        controller.trigger(status.level)
        lastAlarmTime  = now
        lastAlarmLevel = status.level
    }
}
