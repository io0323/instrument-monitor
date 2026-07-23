package com.instrument.presentation.viewmodel

import com.instrument.data.alarm.AlarmController
import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import com.instrument.domain.repository.GpsRepository
import com.instrument.domain.repository.LogRepository
import com.instrument.domain.usecase.AlarmUseCase
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.LogMeasurementUseCase
import com.instrument.domain.usecase.MonitorGasUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

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
    fun dismissAlarmでisAlarmActiveとalarmLevelがクリアされる() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        fixture.emitPpm(380f)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isAlarmActive)
        assertEquals(GasLevel.CRITICAL, vm.uiState.value.alarmLevel)

        vm.dismissAlarm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAlarmActive)
        assertNull(vm.uiState.value.alarmLevel)
    }

    @Test
    fun SAFE遷移時にalarmLevelが残留しない() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        fixture.emitPpm(380f)
        fixture.emitPpm(30f)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAlarmActive)
        assertNull(vm.uiState.value.alarmLevel)
        assertEquals(GasLevel.SAFE, vm.uiState.value.gasStatus?.level)
    }

    @Test
    fun DANGER以上のみ自動ログ保存される() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        fixture.emitPpm(120f)
        fixture.emitPpm(250f)
        advanceUntilIdle()

        assertEquals(1, fixture.savedReadings.value.size)
        assertEquals(GasLevel.DANGER, fixture.savedReadings.value.first().level)
        assertEquals(GasLevel.DANGER, vm.uiState.value.gasStatus?.level)
    }

    @Test
    fun recentHistoryは最大60件を維持する() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        repeat(65) { idx -> fixture.emitPpm(idx.toFloat()) }
        advanceUntilIdle()

        assertEquals(60, vm.recentHistory.value.size)
        assertEquals(5f, vm.recentHistory.value.first().ppm)
        assertEquals(64f, vm.recentHistory.value.last().ppm)
    }

    @Test
    fun connectDeviceでError時にerrorMessageへ反映される() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        vm.connectDevice("broken-device")
        fixture.emitConnectionState(BleConnectionState.Error("接続に失敗しました"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.connectionState is BleConnectionState.Error)
        assertEquals("接続に失敗しました", vm.uiState.value.errorMessage)
    }

    @Test
    fun startMockModeでconnectionStateがConnectedになりgasStatusが更新される() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        // init で startMockMode() が呼ばれる
        advanceUntilIdle()

        assertEquals(BleConnectionState.Connected, vm.uiState.value.connectionState)

        // センサー値を1件流す
        fixture.emitPpm(80f)
        advanceUntilIdle()

        assertEquals(80f, vm.uiState.value.gasStatus?.reading?.ppm)
    }

    @Test
    fun WARNINGレベルはalarmActiveだがlogRepoへの保存は行われない() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        // WARNING: 50 ≤ ppm < 200
        fixture.emitPpm(100f)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAlarmActive, "WARNING でアラームが有効になるべき")
        assertEquals(GasLevel.WARNING, vm.uiState.value.alarmLevel)
        // WARNING は DANGER 未満なので自動ログ保存されない
        assertEquals(0, fixture.savedReadings.value.size, "WARNING 時はログ保存されない")
    }

    @Test
    fun connectDeviceでScanningConnectingConnectedの状態遷移が反映される() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        val states = mutableListOf<BleConnectionState>()

        vm.connectDevice("target-device")
        fixture.emitConnectionState(BleConnectionState.Scanning)
        advanceUntilIdle()
        states += vm.uiState.value.connectionState

        fixture.emitConnectionState(BleConnectionState.Connecting)
        advanceUntilIdle()
        states += vm.uiState.value.connectionState

        fixture.emitConnectionState(BleConnectionState.Connected)
        advanceUntilIdle()
        states += vm.uiState.value.connectionState

        assertEquals(
            listOf(
                BleConnectionState.Scanning,
                BleConnectionState.Connecting,
                BleConnectionState.Connected,
            ),
            states,
        )
    }

    @Test
    fun connectDeviceでConnected後にセンサーデータが受信できる() = runTest {
        val fixture = Fixture()
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        vm.connectDevice("target-device")
        fixture.emitConnectionState(BleConnectionState.Connected)
        advanceUntilIdle()

        // Connected 後にセンサー値を流す
        fixture.emitPpm(250f)
        advanceUntilIdle()

        assertEquals(250f, vm.uiState.value.gasStatus?.reading?.ppm)
        assertEquals(GasLevel.DANGER, vm.uiState.value.gasStatus?.level)
    }

    @Test
    fun logMeasurement失敗時にerrorMessageへ反映される() = runTest {
        val fixture = Fixture(logSaveShouldFail = true)
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        // DANGER 以上でログ保存が試みられる
        fixture.emitPpm(250f)
        advanceUntilIdle()

        assertTrue(
            vm.uiState.value.errorMessage?.contains("ログ保存に失敗しました") == true,
            "ログ保存失敗時は errorMessage にメッセージが設定されるべき。実際: ${vm.uiState.value.errorMessage}",
        )
    }

    @Test
    fun logMeasurement失敗後も次のセンサーデータを受信できる() = runTest {
        val fixture = Fixture(logSaveShouldFail = true)
        val vm = fixture.createViewModel()
        advanceUntilIdle()

        // DANGER でログ保存失敗
        fixture.emitPpm(250f)
        advanceUntilIdle()

        // その後も SAFE データを受信できる（監視が止まっていない）
        fixture.emitPpm(30f)
        advanceUntilIdle()

        assertEquals(30f, vm.uiState.value.gasStatus?.reading?.ppm)
        assertEquals(GasLevel.SAFE, vm.uiState.value.gasStatus?.level)
    }

    private class Fixture(private val logSaveShouldFail: Boolean = false) {
        private val sensorFlow = MutableSharedFlow<SensorReading>(replay = 1, extraBufferCapacity = 128)
        private val connectionFlow = MutableSharedFlow<BleConnectionState>(replay = 1, extraBufferCapacity = 16)
        val savedReadings = MutableStateFlow<List<GeoTaggedReading>>(emptyList())

        private val bleRepo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = connectionFlow
            override fun observeSensorData(): Flow<SensorReading> = sensorFlow
            override suspend fun disconnect() {}
        }

        private val alarmController = object : AlarmController {
            override fun trigger(level: GasLevel) {}
            override fun dismiss() {}
            override fun release() {}
        }

        private val logRepo = object : LogRepository {
            override suspend fun save(reading: GeoTaggedReading): Result<Long> {
                if (logSaveShouldFail) return Result.failure(RuntimeException("DB write error"))
                savedReadings.value = savedReadings.value + reading
                return Result.success(savedReadings.value.size.toLong())
            }
            override fun getAllReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
            override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
            override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
            override suspend fun exportCsv(): Result<String> = Result.success("")
        }

        private val gpsRepo = object : GpsRepository {
            override fun observeLocation(): Flow<Pair<Double, Double>> = flowOf(35.6812 to 139.7671)
        }

        private val monitorGasUseCase = MonitorGasUseCase(bleRepo)
        private val alarmUseCase = AlarmUseCase(monitorGasUseCase, alarmController)
        private val connectDeviceUseCase = ConnectDeviceUseCase(bleRepo)
        private val logMeasurementUseCase = LogMeasurementUseCase(logRepo, gpsRepo)

        fun createViewModel(): DashboardViewModel = DashboardViewModel(
            alarmUseCase = alarmUseCase,
            connectDevice = connectDeviceUseCase,
            logMeasurement = logMeasurementUseCase,
        )

        suspend fun emitPpm(ppm: Float) {
            sensorFlow.emit(
                SensorReading(
                    ppm = ppm,
                    temperature = 25f,
                    humidity = 50f,
                    timestamp = 1L,
                )
            )
        }

        suspend fun emitConnectionState(state: BleConnectionState) {
            connectionFlow.emit(state)
        }
    }
}
