package com.instrument.domain.usecase

import com.instrument.data.alarm.AlarmController
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

// ガス状態を監視し、レベルに応じてアラームを制御するユースケース
class AlarmUseCase(
    private val monitor: MonitorGasUseCase,
    private val controller: AlarmController,
    // テスト・デフォルト用にインメモリ実装をフォールバックとして使用する
    private val settingsRepo: SettingsRepository = SettingsRepository.default(),
    // テスト時に固定クロックを差し込めるよう DI 可能にする
    private val clock: Clock = Clock.System,
) {
    private var lastAlarmTime  = 0L
    private var lastAlarmLevel: GasLevel? = null

    // スヌーズ終了時刻 (エポックミリ秒)。0 はスヌーズ未設定を意味する
    private var snoozeUntilMs: Long = 0L

    fun observe(): Flow<GasStatus> = monitor().onEach { handleAlarm(it) }

    fun dismiss() = controller.dismiss()

    /**
     * 指定した時間 (ミリ秒) アラームをスヌーズする。
     * CRITICAL レベルはスヌーズ中でも発報する。
     */
    fun snooze(durationMs: Long) {
        snoozeUntilMs = clock.now().toEpochMilliseconds() + durationMs
        controller.dismiss()
    }

    /** スヌーズを手動解除する */
    fun cancelSnooze() {
        snoozeUntilMs = 0L
    }

    /** 現在スヌーズ中かどうかを返す。スヌーズ未設定 (snoozeUntilMs == 0) は false を返す */
    fun isSnoozed(): Boolean = snoozeUntilMs > 0L && clock.now().toEpochMilliseconds() < snoozeUntilMs

    /** スヌーズの残り時間をミリ秒で返す。スヌーズ未設定・期限切れの場合は 0 を返す */
    fun snoozeRemainingMs(): Long =
        (snoozeUntilMs - clock.now().toEpochMilliseconds()).coerceAtLeast(0L)

    // AlarmController が保持する音声・振動リソースを解放する
    // ViewModel.onCleared() から必ず呼ぶこと
    fun release() = controller.release()

    private fun handleAlarm(status: GasStatus) {
        if (status.level == GasLevel.SAFE) {
            controller.dismiss()
            // SAFE に戻ったらアラーム履歴とスヌーズをリセットし、次の上昇時に即座に再発報できるようにする
            lastAlarmLevel = null
            lastAlarmTime  = 0L
            snoozeUntilMs  = 0L
            return
        }
        // スヌーズ中は CRITICAL 以外を抑制する (安全のため CRITICAL は常に発報する)
        if (isSnoozed() && status.level != GasLevel.CRITICAL) return
        val now     = clock.now().toEpochMilliseconds()
        val elapsed = now - lastAlarmTime
        val suppressMs = settingsRepo.settings.value.alarmSuppressIntervalSec * 1_000L
        // 同じレベルのアラームは設定した間隔で抑制する。
        // elapsed が負値 (時刻巻き戻し) の場合は抑制せず即座に再発報する。
        if (status.level == lastAlarmLevel && elapsed in 0L..<suppressMs) return
        controller.trigger(status.level)
        lastAlarmTime  = now
        lastAlarmLevel = status.level
    }

    companion object {
        /** 同一レベルアラームを抑制する間隔 (ミリ秒) */
        const val SUPPRESS_INTERVAL_MS = 30_000L
        /** スヌーズの選択肢 (分) */
        val SNOOZE_OPTIONS_MINUTES: List<Int> = listOf(5, 10, 15)
    }
}
