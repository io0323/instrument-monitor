package com.instrument.di

import com.instrument.domain.usecase.AlarmUseCase
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.DeleteOldLogsUseCase
import com.instrument.domain.usecase.ExportCsvUseCase
import com.instrument.domain.usecase.LogMeasurementUseCase
import com.instrument.domain.usecase.MonitorGasUseCase
import com.instrument.domain.usecase.ScanDevicesUseCase
import com.instrument.domain.usecase.SessionStatsUseCase
import com.instrument.presentation.viewmodel.DashboardViewModel
import com.instrument.presentation.viewmodel.DeviceListViewModel
import com.instrument.presentation.viewmodel.HistoryViewModel
import com.instrument.presentation.viewmodel.SettingsViewModel
import org.koin.dsl.module

val domainModule = module {
    // SettingsRepository は各プラットフォームの bleModule で登録する
    // (Android: SharedPreferencesSettings / iOS: NSUserDefaultsSettings)

    // SettingsRepository を注入してカスタム閾値をリアルタイムに適用する
    factory { MonitorGasUseCase(get(), get()) }
    factory { ConnectDeviceUseCase(get()) }
    factory { ScanDevicesUseCase(get()) }
    factory { LogMeasurementUseCase(get(), get()) }
    // シングルトンにしてBroadcastReceiverとViewModelで同一インスタンスを共有する
    single { AlarmUseCase(get(), get(), get()) }
    factory { DeleteOldLogsUseCase(get()) }
    // ExportCsvUseCase を Koin 管理下に置き、HistoryViewModel へ適切に注入する
    factory { ExportCsvUseCase(get()) }
    // 依存なしの純粋ユースケース
    factory { SessionStatsUseCase() }
}

val viewModelModule = module {
    factory { DashboardViewModel(get(), get(), get(), get(), get()) }
    factory { DeviceListViewModel(get(), get()) }
    // ExportCsvUseCase・DeleteOldLogsUseCase を Koin から受け取るよう明示的に注入する
    factory { HistoryViewModel(get(), get(), get()) }
    factory { SettingsViewModel(get()) }
}
