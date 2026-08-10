package com.instrument.data.repository

import com.instrument.data.mock.MockBleSource
import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class MockBleRepository(private val source: MockBleSource) : BleRepository {

    override fun scanDevices(): Flow<List<GasDevice>> = source.scanDevices()

    override fun connect(deviceId: String): Flow<BleConnectionState> = flow {
        emit(BleConnectionState.Connecting)
        delay(500)
        emit(BleConnectionState.Connected)
    }

    /**
     * モック再接続: 1〜2秒の遅延後に常に true (成功) を返す。
     * 実機では AndroidBleDataSource / IosBleDataSource が実装する。
     */
    override fun reconnect(deviceId: String): Flow<Boolean> = flow {
        // モックは 1.5 秒後に再接続成功とする
        delay(1500)
        emit(true)
    }

    override fun observeSensorData(): Flow<SensorReading> = source.observeSensorData()

    override suspend fun disconnect() {}
}
