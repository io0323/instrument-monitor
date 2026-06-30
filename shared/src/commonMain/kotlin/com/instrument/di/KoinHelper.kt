package com.instrument.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(bleModule, domainModule, viewModelModule)
    }
}
