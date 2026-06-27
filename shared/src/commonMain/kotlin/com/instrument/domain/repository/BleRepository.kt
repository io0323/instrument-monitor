package com.instrument.domain.repository

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow

interface BleRepository {
    fun scanDevices(): Flow<List<GasDevice>>
    fun connect(deviceId: String): Flow<BleConnectionState>
    fun observeSensorData(): Flow<SensorReading>
    suspend fun disconnect()
}

sealed class BleConnectionState {
    object Scanning     : BleConnectionState()
    object Connecting   : BleConnectionState()
    object Connected    : BleConnectionState()
    object Disconnected : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}
