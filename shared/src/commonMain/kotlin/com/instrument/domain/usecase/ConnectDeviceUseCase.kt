package com.instrument.domain.usecase

import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow

class ConnectDeviceUseCase(private val repo: BleRepository) {
    operator fun invoke(deviceId: String): Flow<BleConnectionState> = repo.connect(deviceId)
}
