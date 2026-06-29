package com.instrument.di

import com.instrument.data.alarm.AlarmController
import com.instrument.data.alarm.AndroidAlarmController
import com.instrument.data.ble.AndroidBleDataSource
import com.instrument.data.db.DatabaseDriverFactory
import com.instrument.data.export.CsvExporter
import com.instrument.data.gps.AndroidGpsSource
import com.instrument.data.mock.MockBleSource
import com.instrument.data.repository.MockBleRepository
import com.instrument.data.repository.RealBleRepository
import com.instrument.data.repository.SqlDelightLogRepository
import com.instrument.domain.repository.BleRepository
import com.instrument.domain.repository.GpsRepository
import com.instrument.domain.repository.LogRepository
import com.instrument.shared.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val bleModule: Module = module {
    single<BleRepository> {
        if (BuildConfig.USE_MOCK) MockBleRepository(MockBleSource())
        else RealBleRepository(AndroidBleDataSource(androidContext()))
    }
    single<AlarmController> { AndroidAlarmController(androidContext()) }
    single<GpsRepository> { AndroidGpsSource(androidContext()) }
    single { DatabaseDriverFactory(androidContext()) }
    single<LogRepository> { SqlDelightLogRepository(get()) }
    single { CsvExporter(androidContext()) }
}
