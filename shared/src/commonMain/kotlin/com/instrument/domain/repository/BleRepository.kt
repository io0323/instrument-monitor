package com.instrument.domain.repository

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow

interface BleRepository {
    fun scanDevices(): Flow<List<GasDevice>>
    fun connect(deviceId: String): Flow<BleConnectionState>
    // 切断後に同一デバイスへ再接続を試みる。成功時は true を emit する
    fun reconnect(deviceId: String): Flow<Boolean>
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
