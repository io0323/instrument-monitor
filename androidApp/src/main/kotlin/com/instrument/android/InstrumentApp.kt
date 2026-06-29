package com.instrument.android

import android.app.Application
import com.instrument.android.di.appModule
import com.instrument.di.bleModule
import com.instrument.di.domainModule
import com.instrument.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class InstrumentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@InstrumentApp)
            modules(bleModule, domainModule, viewModelModule, appModule)
        }
    }
}
