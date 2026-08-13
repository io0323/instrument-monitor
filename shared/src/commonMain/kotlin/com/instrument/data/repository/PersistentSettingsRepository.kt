package com.instrument.data.repository

import com.instrument.domain.model.AppSettings
import com.instrument.domain.repository.SettingsRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// multiplatform-settings を使ってアプリ再起動後も設定値を保持する実装
class PersistentSettingsRepository(private val store: Settings) : SettingsRepository {

    // 各設定キーを定数として管理し、誤記を防ぐ
    private object Keys {
        const val GPS_LOGGING_ENABLED = "gps_logging_enabled"
        const val RETENTION_DAYS = "retention_days"
        const val ALARM_SUPPRESS_INTERVAL_SEC = "alarm_suppress_interval_sec"
        const val SOUND_ENABLED = "sound_enabled"
        const val VIBRATION_ENABLED = "vibration_enabled"
        // アラーム閾値 (ppm)
        const val WARNING_THRESHOLD_PPM = "warning_threshold_ppm"
        const val DANGER_THRESHOLD_PPM = "danger_threshold_ppm"
        const val CRITICAL_THRESHOLD_PPM = "critical_threshold_ppm"
    }

    // 起動時にストレージから読み込んだ初期値で StateFlow を生成する
    private val _settings = MutableStateFlow(loadFromStorage())

    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(settings: AppSettings) {
        // ストレージへ書き込んでから StateFlow を更新する
        store.putBoolean(Keys.GPS_LOGGING_ENABLED, settings.gpsLoggingEnabled)
        store.putInt(Keys.RETENTION_DAYS, settings.retentionDays)
        store.putInt(Keys.ALARM_SUPPRESS_INTERVAL_SEC, settings.alarmSuppressIntervalSec)
        store.putBoolean(Keys.SOUND_ENABLED, settings.soundEnabled)
        store.putBoolean(Keys.VIBRATION_ENABLED, settings.vibrationEnabled)
        store.putInt(Keys.WARNING_THRESHOLD_PPM, settings.warningThresholdPpm)
        store.putInt(Keys.DANGER_THRESHOLD_PPM, settings.dangerThresholdPpm)
        store.putInt(Keys.CRITICAL_THRESHOLD_PPM, settings.criticalThresholdPpm)
        _settings.value = settings
    }

    // ストレージから読み込んでデフォルト値を補完した AppSettings を返す
    private fun loadFromStorage(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            gpsLoggingEnabled = store.getBoolean(
                key = Keys.GPS_LOGGING_ENABLED,
                defaultValue = defaults.gpsLoggingEnabled,
            ),
            retentionDays = store.getInt(
                key = Keys.RETENTION_DAYS,
                defaultValue = defaults.retentionDays,
            ),
            alarmSuppressIntervalSec = store.getInt(
                key = Keys.ALARM_SUPPRESS_INTERVAL_SEC,
                defaultValue = defaults.alarmSuppressIntervalSec,
            ),
            soundEnabled = store.getBoolean(
                key = Keys.SOUND_ENABLED,
                defaultValue = defaults.soundEnabled,
            ),
            vibrationEnabled = store.getBoolean(
                key = Keys.VIBRATION_ENABLED,
                defaultValue = defaults.vibrationEnabled,
            ),
            warningThresholdPpm = store.getInt(
                key = Keys.WARNING_THRESHOLD_PPM,
                defaultValue = defaults.warningThresholdPpm,
            ),
            dangerThresholdPpm = store.getInt(
                key = Keys.DANGER_THRESHOLD_PPM,
                defaultValue = defaults.dangerThresholdPpm,
            ),
            criticalThresholdPpm = store.getInt(
                key = Keys.CRITICAL_THRESHOLD_PPM,
                defaultValue = defaults.criticalThresholdPpm,
            ),
        )
    }
}
