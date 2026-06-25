package com.instrument.android.di

import org.koin.dsl.module

// Phase 2以降で各ViewModel・Repository・DataSourceを登録する
val appModule = module {
    // 例: viewModel { DashboardViewModel(get()) }
}
