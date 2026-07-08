package com.instrument.domain.usecase

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

// ConnectDeviceUseCase の接続フロー遷移を検証するテスト
class ConnectDeviceUseCaseTest {

    // 正常系: Connecting → Connected の順にemitするフェイクリポジトリ
    private val fakeRepo = object : BleRepository {
        override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
        override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(
            BleConnectionState.Connecting,
            BleConnectionState.Connected,
        )
        override fun observeSensorData(): Flow<SensorReading> = flowOf()
        override suspend fun disconnect() {}
    }

    // エラー系: エラー状態をemitするフェイクリポジトリ
    private val errorRepo = object : BleRepository {
        override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
        override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(
            BleConnectionState.Connecting,
            BleConnectionState.Error("接続タイムアウト"),
        )
        override fun observeSensorData(): Flow<SensorReading> = flowOf()
        override suspend fun disconnect() {}
    }

    @Test
    fun connect_はConnecting_Connected_の順にemitする() = runTest {
        val result = ConnectDeviceUseCase(fakeRepo).invoke("test-id").toList()
        assertEquals(2, result.size)
        assertEquals(BleConnectionState.Connecting, result[0])
        assertEquals(BleConnectionState.Connected, result[1])
    }

    @Test
    fun connect_はConnected状態を含む() = runTest {
        val result = ConnectDeviceUseCase(fakeRepo).invoke("test-id").toList()
        assertContains(result, BleConnectionState.Connected)
    }

    @Test
    fun connect_エラー時はError状態をemitする() = runTest {
        val result = ConnectDeviceUseCase(errorRepo).invoke("bad-id").toList()
        val lastState = result.last()
        assertIs<BleConnectionState.Error>(lastState)
        assertEquals("接続タイムアウト", lastState.message)
    }

    @Test
    fun connect_デバイスIDが伝達される() = runTest {
        var capturedId = ""
        val capturingRepo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> {
                capturedId = deviceId
                return flowOf(BleConnectionState.Connected)
            }
            override fun observeSensorData(): Flow<SensorReading> = flowOf()
            override suspend fun disconnect() {}
        }
        ConnectDeviceUseCase(capturingRepo).invoke("AA:BB:CC:DD:EE:01").toList()
        assertEquals("AA:BB:CC:DD:EE:01", capturedId)
    }
}

