package com.instrument.domain.usecase

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ScanDevicesUseCase のスキャン結果フローを検証するテスト
class ScanDevicesUseCaseTest {

    private val mockDevices = listOf(
        GasDevice(id = "AA:BB:CC:DD:EE:01", name = "instrument-G100", rssi = -62),
        GasDevice(id = "AA:BB:CC:DD:EE:02", name = "instrument-G200", rssi = -78),
        GasDevice(id = "AA:BB:CC:DD:EE:03", name = "instrument-G300", rssi = -85),
    )

    private val fakeRepo = object : BleRepository {
        override fun scanDevices(): Flow<List<GasDevice>> = flowOf(mockDevices)
        override fun connect(deviceId: String): Flow<BleConnectionState> =
            flowOf(BleConnectionState.Connected)
        override fun observeSensorData(): Flow<SensorReading> = flowOf()
        override suspend fun disconnect() {}
    }

    private val emptyRepo = object : BleRepository {
        override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
        override fun connect(deviceId: String): Flow<BleConnectionState> =
            flowOf(BleConnectionState.Connected)
        override fun observeSensorData(): Flow<SensorReading> = flowOf()
        override suspend fun disconnect() {}
    }

    @Test
    fun スキャン結果のデバイス数が正しい() = runTest {
        val result = ScanDevicesUseCase(fakeRepo).invoke().first()
        assertEquals(3, result.size)
    }

    @Test
    fun 先頭デバイス名が正しい() = runTest {
        val result = ScanDevicesUseCase(fakeRepo).invoke().first()
        assertEquals("instrument-G100", result[0].name)
    }

    @Test
    fun デバイスIDが一致する() = runTest {
        val result = ScanDevicesUseCase(fakeRepo).invoke().first()
        assertEquals("AA:BB:CC:DD:EE:01", result[0].id)
        assertEquals("AA:BB:CC:DD:EE:02", result[1].id)
    }

    @Test
    fun RSSIが正しく保持される() = runTest {
        val result = ScanDevicesUseCase(fakeRepo).invoke().first()
        assertEquals(-62, result[0].rssi)
        assertEquals(-78, result[1].rssi)
        assertEquals(-85, result[2].rssi)
    }

    @Test
    fun スキャン結果が空の場合も正常に処理される() = runTest {
        val result = ScanDevicesUseCase(emptyRepo).invoke().first()
        assertTrue(result.isEmpty())
    }
}

