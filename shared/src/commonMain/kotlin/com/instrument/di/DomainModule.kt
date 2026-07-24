package com.instrument.di

import com.instrument.domain.usecase.AlarmUseCase
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.DeleteOldLogsUseCase
import com.instrument.domain.usecase.ExportCsvUseCase
import com.instrument.domain.usecase.LogMeasurementUseCase
import com.instrument.domain.usecase.MonitorGasUseCase
import com.instrument.domain.usecase.ScanDevicesUseCase
import com.instrument.presentation.viewmodel.DashboardViewModel
import com.instrument.presentation.viewmodel.DeviceListViewModel
import com.instrument.presentation.viewmodel.HistoryViewModel
import org.koin.dsl.module

val domainModule = module {
    factory { MonitorGasUseCase(get()) }
    factory { ConnectDeviceUseCase(get()) }
    factory { ScanDevicesUseCase(get()) }
    factory { LogMeasurementUseCase(get(), get()) }
    factory { AlarmUseCase(get(), get()) }
    factory { DeleteOldLogsUseCase(get()) }
    // ExportCsvUseCase を Koin 管理下に置き、HistoryViewModel へ適切に注入する
    factory { ExportCsvUseCase(get()) }
}

val viewModelModule = module {
    factory { DashboardViewModel(get(), get(), get()) }
    factory { DeviceListViewModel(get(), get()) }
    // ExportCsvUseCase・DeleteOldLogsUseCase を Koin から受け取るよう明示的に注入する
    factory { HistoryViewModel(get(), get(), get()) }
}
