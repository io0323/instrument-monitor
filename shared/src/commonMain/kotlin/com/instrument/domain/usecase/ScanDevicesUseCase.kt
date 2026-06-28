package com.instrument.domain.usecase

import com.instrument.domain.model.GasDevice
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow

class ScanDevicesUseCase(private val repo: BleRepository) {
    operator fun invoke(): Flow<List<GasDevice>> = repo.scanDevices()
}
