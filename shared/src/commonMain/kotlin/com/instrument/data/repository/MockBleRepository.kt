package com.instrument.data.repository

import com.instrument.data.mock.MockBleSource
import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockBleRepository(private val source: MockBleSource) : BleRepository {

    override fun scanDevices(): Flow<List<GasDevice>> = source.scanDevices()

    override fun connect(deviceId: String): Flow<BleConnectionState> = flow {
        emit(BleConnectionState.Connecting)
        delay(500)
        emit(BleConnectionState.Connected)
    }

    override fun observeSensorData(): Flow<SensorReading> = source.observeSensorData()

    override suspend fun disconnect() {}
}
