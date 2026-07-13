package com.instrument.presentation.viewmodel

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.ScanDevicesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun startScanでスキャン状態になり結果を受け取る() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()

        vm.startScan()
        fixture.emitScanResults(
            listOf(
                GasDevice(id = "A1", name = "device-1", rssi = -55),
                GasDevice(id = "A2", name = "device-2", rssi = -65),
            )
        )
        advanceUntilIdle()

        assertTrue(vm.isScanning.value)
        assertEquals(2, vm.scanResults.value.size)
        assertEquals("device-1", vm.scanResults.value.first().name)
    }

    @Test
    fun stopScan後は結果の更新を受け取らない() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()

        vm.startScan()
        fixture.emitScanResults(listOf(GasDevice(id = "A1", name = "before-stop", rssi = -50)))
        advanceUntilIdle()

        vm.stopScan()
        fixture.emitScanResults(listOf(GasDevice(id = "A2", name = "after-stop", rssi = -40)))
        advanceUntilIdle()

        assertFalse(vm.isScanning.value)
        assertEquals(listOf("before-stop"), vm.scanResults.value.map { it.name })
    }

    @Test
    fun selectDeviceで接続状態が更新される() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()

        vm.selectDevice(GasDevice(id = "TARGET", name = "target", rssi = -60))
        fixture.emitConnectionState(BleConnectionState.Connecting)
        fixture.emitConnectionState(BleConnectionState.Connected)
        advanceUntilIdle()

        assertEquals(BleConnectionState.Connected, vm.connectionState.value)
        assertEquals("TARGET", fixture.lastConnectedDeviceId)
    }

    @Test
    fun navigateToDashboardはConnected時のみtrue() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()

        assertFalse(vm.navigateToDashboard())

        vm.selectDevice(GasDevice(id = "TARGET", name = "target", rssi = -60))
        fixture.emitConnectionState(BleConnectionState.Connected)
        advanceUntilIdle()

        assertTrue(vm.navigateToDashboard())
    }

    @Test
    fun startScanを連続呼び出ししてもscanDevicesは1回だけ実行される() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()

        vm.startScan()
        vm.startScan()
        advanceUntilIdle()

        assertTrue(vm.isScanning.value)
        assertEquals(1, fixture.scanInvocationCount)
    }

    private class Fixture {
        private val scanFlow = MutableStateFlow<List<GasDevice>>(emptyList())
        private val connectionFlow = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        var scanInvocationCount: Int = 0
            private set
        var lastConnectedDeviceId: String? = null
            private set

        private val bleRepo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> {
                scanInvocationCount += 1
                return scanFlow
            }

            override fun connect(deviceId: String): Flow<BleConnectionState> {
                lastConnectedDeviceId = deviceId
                return connectionFlow
            }

            override fun observeSensorData(): Flow<SensorReading> = flowOf()
            override suspend fun disconnect() {}
        }

        private val scanUseCase = ScanDevicesUseCase(bleRepo)
        private val connectUseCase = ConnectDeviceUseCase(bleRepo)

        fun createViewModel(): DeviceListViewModel = DeviceListViewModel(scanUseCase, connectUseCase)

        suspend fun emitScanResults(results: List<GasDevice>) {
            scanFlow.value = results
        }

        suspend fun emitConnectionState(state: BleConnectionState) {
            connectionFlow.value = state
        }
    }
}
