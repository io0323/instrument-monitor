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

    /**
     * WARNING 閾値を変更する。[AppSettings.WARNING_THRESHOLD_RANGE] 内の値のみ受け付ける。
     * WARNING < DANGER の制約も検証する。
     */
    fun setWarningThreshold(ppm: Int) {
        require(ppm in AppSettings.WARNING_THRESHOLD_RANGE) {
            "warningThresholdPpm は ${AppSettings.WARNING_THRESHOLD_RANGE} の範囲内でなければなりません"
        }
        require(ppm < settings.value.dangerThresholdPpm) {
            "WARNING 閾値 ($ppm) は DANGER 閾値 (${settings.value.dangerThresholdPpm}) より小さくなければなりません"
        }
        settingsRepo.update(settings.value.copy(warningThresholdPpm = ppm))
    }

    /**
     * DANGER 閾値を変更する。[AppSettings.DANGER_THRESHOLD_RANGE] 内の値のみ受け付ける。
     * WARNING < DANGER < CRITICAL の制約も検証する。
     */
    fun setDangerThreshold(ppm: Int) {
        require(ppm in AppSettings.DANGER_THRESHOLD_RANGE) {
            "dangerThresholdPpm は ${AppSettings.DANGER_THRESHOLD_RANGE} の範囲内でなければなりません"
        }
        require(ppm > settings.value.warningThresholdPpm) {
            "DANGER 閾値 ($ppm) は WARNING 閾値 (${settings.value.warningThresholdPpm}) より大きくなければなりません"
        }
        require(ppm < settings.value.criticalThresholdPpm) {
            "DANGER 閾値 ($ppm) は CRITICAL 閾値 (${settings.value.criticalThresholdPpm}) より小さくなければなりません"
        }
        settingsRepo.update(settings.value.copy(dangerThresholdPpm = ppm))
    }

    /**
     * CRITICAL 閾値を変更する。[AppSettings.CRITICAL_THRESHOLD_RANGE] 内の値のみ受け付ける。
     * DANGER < CRITICAL の制約も検証する。
     */
    fun setCriticalThreshold(ppm: Int) {
        require(ppm in AppSettings.CRITICAL_THRESHOLD_RANGE) {
            "criticalThresholdPpm は ${AppSettings.CRITICAL_THRESHOLD_RANGE} の範囲内でなければなりません"
        }
        require(ppm > settings.value.dangerThresholdPpm) {
            "CRITICAL 閾値 ($ppm) は DANGER 閾値 (${settings.value.dangerThresholdPpm}) より大きくなければなりません"
        }
        settingsRepo.update(settings.value.copy(criticalThresholdPpm = ppm))
    }

    /**
     * WARNING 閾値変更 (UI スライダー向け安全版)。
     * 順序制約に違反する場合は DANGER 閾値を自動調整して保存する。
     */
    fun setWarningThresholdSafe(ppm: Int) {
        val clamped = ppm.coerceIn(AppSettings.WARNING_THRESHOLD_RANGE)
        val current = settings.value
        // WARNING >= DANGER になる場合は DANGER を WARNING+1 へ引き上げる
        val newDanger = if (clamped >= current.dangerThresholdPpm) {
            (clamped + 1).coerceIn(AppSettings.DANGER_THRESHOLD_RANGE)
        } else {
            current.dangerThresholdPpm
        }
        // DANGER >= CRITICAL になる場合は CRITICAL を DANGER+1 へ引き上げる
        val newCritical = if (newDanger >= current.criticalThresholdPpm) {
            (newDanger + 1).coerceIn(AppSettings.CRITICAL_THRESHOLD_RANGE)
        } else {
            current.criticalThresholdPpm
        }
        settingsRepo.update(
            current.copy(
                warningThresholdPpm  = clamped,
                dangerThresholdPpm   = newDanger,
                criticalThresholdPpm = newCritical,
            )
        )
    }

    /**
     * DANGER 閾値変更 (UI スライダー向け安全版)。
     * 順序制約に違反する場合は隣接閾値を自動調整して保存する。
     */
    fun setDangerThresholdSafe(ppm: Int) {
        val clamped = ppm.coerceIn(AppSettings.DANGER_THRESHOLD_RANGE)
        val current = settings.value
        // WARNING >= DANGER になる場合は WARNING を DANGER-1 へ引き下げる
        val newWarning = if (current.warningThresholdPpm >= clamped) {
            (clamped - 1).coerceIn(AppSettings.WARNING_THRESHOLD_RANGE)
        } else {
            current.warningThresholdPpm
        }
        // DANGER >= CRITICAL になる場合は CRITICAL を DANGER+1 へ引き上げる
        val newCritical = if (clamped >= current.criticalThresholdPpm) {
            (clamped + 1).coerceIn(AppSettings.CRITICAL_THRESHOLD_RANGE)
        } else {
            current.criticalThresholdPpm
        }
        settingsRepo.update(
            current.copy(
                warningThresholdPpm  = newWarning,
                dangerThresholdPpm   = clamped,
                criticalThresholdPpm = newCritical,
            )
        )
    }

    /**
     * CRITICAL 閾値変更 (UI スライダー向け安全版)。
     * 順序制約に違反する場合は DANGER 閾値を自動調整して保存する。
     */
    fun setCriticalThresholdSafe(ppm: Int) {
        val clamped = ppm.coerceIn(AppSettings.CRITICAL_THRESHOLD_RANGE)
        val current = settings.value
        // DANGER >= CRITICAL になる場合は DANGER を CRITICAL-1 へ引き下げる
        val newDanger = if (current.dangerThresholdPpm >= clamped) {
            (clamped - 1).coerceIn(AppSettings.DANGER_THRESHOLD_RANGE)
        } else {
            current.dangerThresholdPpm
        }
        // WARNING >= DANGER になる場合は WARNING を DANGER-1 へ引き下げる
        val newWarning = if (current.warningThresholdPpm >= newDanger) {
            (newDanger - 1).coerceIn(AppSettings.WARNING_THRESHOLD_RANGE)
        } else {
            current.warningThresholdPpm
        }
        settingsRepo.update(
            current.copy(
                warningThresholdPpm  = newWarning,
                dangerThresholdPpm   = newDanger,
                criticalThresholdPpm = clamped,
            )
        )
    }

    /** 記憶していた最終接続デバイス情報を消去する */
    fun forgetLastDevice() {
        settingsRepo.update(
            settings.value.copy(
                lastConnectedDeviceId   = null,
                lastConnectedDeviceName = null,
            )
        )
    }

    /** 全閾値をデフォルト値 (50 / 200 / 350 ppm) にリセットする */
    fun resetThresholdsToDefault() {
        val defaults = AppSettings()
        settingsRepo.update(
            settings.value.copy(
                warningThresholdPpm  = defaults.warningThresholdPpm,
                dangerThresholdPpm   = defaults.dangerThresholdPpm,
                criticalThresholdPpm = defaults.criticalThresholdPpm,
            )
        )
    }
}
