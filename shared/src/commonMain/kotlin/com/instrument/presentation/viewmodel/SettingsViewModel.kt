package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.instrument.domain.model.AppSettings
import com.instrument.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

// アプリ設定の読み書きを担う ViewModel
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    /** 現在の設定状態 */
    val settings: StateFlow<AppSettings> = settingsRepo.settings

    /** GPS 自動ロギングの ON/OFF を切り替える */
    fun toggleGpsLogging() {
        settingsRepo.update(settings.value.copy(gpsLoggingEnabled = !settings.value.gpsLoggingEnabled))
    }

    /** ログ保持日数を変更する。[AppSettings.RETENTION_OPTIONS] 内の値のみ受け付ける */
    fun setRetentionDays(days: Int) {
        require(days in AppSettings.RETENTION_OPTIONS) {
            "retentionDays は ${AppSettings.RETENTION_OPTIONS} のいずれかでなければなりません"
        }
        settingsRepo.update(settings.value.copy(retentionDays = days))
    }

    /** アラーム再発報間隔を変更する。[AppSettings.ALARM_INTERVAL_OPTIONS] 内の値のみ受け付ける */
    fun setAlarmSuppressIntervalSec(sec: Int) {
        require(sec in AppSettings.ALARM_INTERVAL_OPTIONS) {
            "alarmSuppressIntervalSec は ${AppSettings.ALARM_INTERVAL_OPTIONS} のいずれかでなければなりません"
        }
        settingsRepo.update(settings.value.copy(alarmSuppressIntervalSec = sec))
    }

    /** アラーム音の ON/OFF を切り替える */
    fun toggleSoundEnabled() {
        settingsRepo.update(settings.value.copy(soundEnabled = !settings.value.soundEnabled))
    }

    /** アラーム振動の ON/OFF を切り替える */
    fun toggleVibrationEnabled() {
        settingsRepo.update(settings.value.copy(vibrationEnabled = !settings.value.vibrationEnabled))
    }
}
