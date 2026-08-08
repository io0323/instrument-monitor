package com.instrument.di

import com.instrument.data.alarm.AlarmController
import com.instrument.data.alarm.IosAlarmController
import com.instrument.data.db.DatabaseDriverFactory
import com.instrument.data.mock.MockBleSource
import com.instrument.data.repository.MockBleRepository
import com.instrument.data.repository.PersistentSettingsRepository
import com.instrument.data.repository.SqlDelightLogRepository
import com.instrument.domain.repository.BleRepository
import com.instrument.domain.repository.GpsRepository
import com.instrument.domain.repository.LogRepository
import com.instrument.domain.repository.SettingsRepository
import com.russhwolf.settings.NSUserDefaultsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val bleModule: Module = module {
    single<BleRepository> { MockBleRepository(MockBleSource()) }
    // 実機接続時: IosRealBleRepository(IosBleDataSource())

    single<AlarmController> { IosAlarmController() }

    // iOS GPS: Phase 8 で CoreLocation 実装に置き換え
    single<GpsRepository> {
        object : GpsRepository {
            override fun observeLocation(): Flow<Pair<Double, Double>> = emptyFlow()
        }
    }

    single { DatabaseDriverFactory() }
    single<LogRepository> { SqlDelightLogRepository(get()) }

    // NSUserDefaults をバックエンドとした永続化設定リポジトリ
    single<SettingsRepository> {
        PersistentSettingsRepository(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults))
    }
}
