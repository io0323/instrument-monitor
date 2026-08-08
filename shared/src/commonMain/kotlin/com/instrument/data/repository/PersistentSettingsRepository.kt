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
    }

    // 起動時にストレージから読み込んだ初期値で StateFlow を生成する
    private val _settings = MutableStateFlow(loadFromStorage())

    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(settings: AppSettings) {
        // ストレージへ書き込んでから StateFlow を更新する
        store.putBoolean(Keys.GPS_LOGGING_ENABLED, settings.gpsLoggingEnabled)
        store.putInt(Keys.RETENTION_DAYS, settings.retentionDays)
        store.putInt(Keys.ALARM_SUPPRESS_INTERVAL_SEC, settings.alarmSuppressIntervalSec)
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
        )
    }
}
