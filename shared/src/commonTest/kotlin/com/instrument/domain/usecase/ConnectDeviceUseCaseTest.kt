package com.instrument.domain.usecase

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.ReconnectState
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
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
        override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(true)
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
        override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(false)
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
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(true)
            override fun observeSensorData(): Flow<SensorReading> = flowOf()
            override suspend fun disconnect() {}
        }
        ConnectDeviceUseCase(capturingRepo).invoke("AA:BB:CC:DD:EE:01").toList()
        assertEquals("AA:BB:CC:DD:EE:01", capturedId)
    }

    // ---- reconnect のテスト ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reconnect_各試行でReconnecting状態がemitされる() = runTest {
        // 常に失敗するリポジトリ (maxAttempts 回の Reconnecting を確認するため)
        val alwaysFailRepo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(false)
            override fun observeSensorData(): Flow<SensorReading> = flowOf()
            override suspend fun disconnect() {}
        }

        val states = mutableListOf<ReconnectState>()
        val maxAttempts = 3
        // runTest は仮想時間で delay をスキップするため、実時間を消費しない
        ConnectDeviceUseCase(alwaysFailRepo).reconnect("device-id", maxAttempts).collect { state ->
            states += state
            // delay を進めて次の試行へ移行する
            advanceTimeBy(100_000L)
        }

        // Reconnecting(1,3), Reconnecting(2,3), Reconnecting(3,3), Failed の合計 maxAttempts+1 件
        val reconnectingStates = states.filterIsInstance<ReconnectState.Reconnecting>()
        assertEquals(maxAttempts, reconnectingStates.size, "Reconnecting が maxAttempts 回 emit されるべき")
        assertEquals(1, reconnectingStates[0].attempt)
        assertEquals(maxAttempts, reconnectingStates[0].maxAttempts)
        assertEquals(maxAttempts, reconnectingStates.last().attempt)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reconnect_成功時にConnectedがemitされる() = runTest {
        // 常に成功するリポジトリ
        val successRepo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(true)
            override fun observeSensorData(): Flow<SensorReading> = flowOf()
            override suspend fun disconnect() {}
        }

        val states = mutableListOf<ReconnectState>()
        ConnectDeviceUseCase(successRepo).reconnect("device-id", maxAttempts = 5).collect { state ->
            states += state
            advanceTimeBy(10_000L)
        }

        // 最初の試行で成功するので Reconnecting(1,5) → Connected の2件
        assertEquals(2, states.size, "1回の Reconnecting 後に Connected が emit されるべき")
        assertIs<ReconnectState.Reconnecting>(states[0])
        assertIs<ReconnectState.Connected>(states[1])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reconnect_最大試行回数超過後にFailedがemitされる() = runTest {
        // 常に失敗するリポジトリ
        val alwaysFailRepo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(false)
            override fun observeSensorData(): Flow<SensorReading> = flowOf()
            override suspend fun disconnect() {}
        }

        val states = mutableListOf<ReconnectState>()
        val maxAttempts = 3
        ConnectDeviceUseCase(alwaysFailRepo).reconnect("device-id", maxAttempts).collect { state ->
            states += state
            advanceTimeBy(100_000L)
        }

        // 最後の状態は Failed であるべき
        assertIs<ReconnectState.Failed>(states.last(), "最大試行回数超過後は Failed が emit されるべき")
    }
}

