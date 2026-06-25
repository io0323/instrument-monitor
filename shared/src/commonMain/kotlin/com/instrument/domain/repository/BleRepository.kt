package com.instrument.domain.repository

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow

// BLEデータソースの抽象化インターフェース
// Phase 2 で実装: AndroidBleRepository / MockBleRepository
interface BleRepository {
    // 周辺BLEデバイスをスキャンして Flow で返す
    fun scanDevices(): Flow<List<GasDevice>>

    // 指定デバイスに接続し、センサー値を Flow で返す
    fun connectAndObserve(device: GasDevice): Flow<Result<SensorReading>>

    // 接続を切断する
    suspend fun disconnect()
}
