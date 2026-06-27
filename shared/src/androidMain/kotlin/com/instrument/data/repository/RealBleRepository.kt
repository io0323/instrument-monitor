package com.instrument.data.repository

import com.instrument.data.ble.AndroidBleDataSource
import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow

class RealBleRepository(private val dataSource: AndroidBleDataSource) : BleRepository {
    override fun scanDevices(): Flow<List<GasDevice>> = dataSource.scanDevices()
    override fun connect(deviceId: String): Flow<BleConnectionState> = dataSource.connect(deviceId)
    override fun observeSensorData(): Flow<SensorReading> = dataSource.observeSensorData()
    override suspend fun disconnect() = dataSource.disconnect()
}
