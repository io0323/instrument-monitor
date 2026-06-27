package com.instrument.di

import com.instrument.data.mock.MockBleSource
import com.instrument.data.repository.MockBleRepository
import com.instrument.domain.repository.BleRepository
import org.koin.core.module.Module
import org.koin.dsl.module

actual val bleModule: Module = module {
    single<BleRepository> { MockBleRepository(MockBleSource()) }
    // 実機接続時: IosRealBleRepository(IosBleDataSource())
}
