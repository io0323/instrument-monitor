package com.instrument.data.repository

import com.instrument.domain.model.AppSettings
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// MapSettings (インメモリ実装) を使って PersistentSettingsRepository を検証する
class PersistentSettingsRepositoryTest {

    private lateinit var mapSettings: MapSettings
    private lateinit var repository: PersistentSettingsRepository

    @BeforeTest
    fun setUp() {
        mapSettings = MapSettings()
        repository = PersistentSettingsRepository(mapSettings)
    }

    @Test
    fun `初回起動時は AppSettings のデフォルト値が返される`() {
        val defaults = AppSettings()
        val actual = repository.settings.value

        assertEquals(defaults.gpsLoggingEnabled, actual.gpsLoggingEnabled)
        assertEquals(defaults.retentionDays, actual.retentionDays)
        assertEquals(defaults.alarmSuppressIntervalSec, actual.alarmSuppressIntervalSec)
    }

    @Test
    fun `update を呼ぶと StateFlow の値が即座に反映される`() {
        val updated = AppSettings(
            gpsLoggingEnabled = false,
            retentionDays = 60,
            alarmSuppressIntervalSec = 15,
        )

        repository.update(updated)

        val actual = repository.settings.value
        assertFalse(actual.gpsLoggingEnabled)
        assertEquals(60, actual.retentionDays)
        assertEquals(15, actual.alarmSuppressIntervalSec)
    }

    @Test
    fun `update した値がストレージに書き込まれ、再生成後も読み込まれる`() {
        val updated = AppSettings(
            gpsLoggingEnabled = false,
            retentionDays = 90,
            alarmSuppressIntervalSec = 60,
        )

        repository.update(updated)

        // 同じ MapSettings インスタンスを使って別のリポジトリを生成し、再起動をシミュレート
        val reloaded = PersistentSettingsRepository(mapSettings)
        val actual = reloaded.settings.value

        assertFalse(actual.gpsLoggingEnabled)
        assertEquals(90, actual.retentionDays)
        assertEquals(60, actual.alarmSuppressIntervalSec)
    }

    @Test
    fun `gpsLoggingEnabled を true から false に切り替えられる`() {
        // デフォルト true を false に変更
        repository.update(repository.settings.value.copy(gpsLoggingEnabled = false))
        assertFalse(repository.settings.value.gpsLoggingEnabled)

        // false から true に戻す
        repository.update(repository.settings.value.copy(gpsLoggingEnabled = true))
        assertTrue(repository.settings.value.gpsLoggingEnabled)
    }

    @Test
    fun `retentionDays の変更が永続化される`() {
        AppSettings.RETENTION_OPTIONS.forEach { days ->
            repository.update(repository.settings.value.copy(retentionDays = days))
            val reloaded = PersistentSettingsRepository(mapSettings)
            assertEquals(days, reloaded.settings.value.retentionDays)
        }
    }

    @Test
    fun `alarmSuppressIntervalSec の変更が永続化される`() {
        AppSettings.ALARM_INTERVAL_OPTIONS.forEach { interval ->
            repository.update(repository.settings.value.copy(alarmSuppressIntervalSec = interval))
            val reloaded = PersistentSettingsRepository(mapSettings)
            assertEquals(interval, reloaded.settings.value.alarmSuppressIntervalSec)
        }
    }
}
