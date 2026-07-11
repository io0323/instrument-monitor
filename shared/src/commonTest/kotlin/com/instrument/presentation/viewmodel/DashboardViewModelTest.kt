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

    private class Fixture {
        private val sensorFlow = MutableSharedFlow<SensorReading>(replay = 1, extraBufferCapacity = 128)
        val savedReadings = MutableStateFlow<List<GeoTaggedReading>>(emptyList())

        private val bleRepo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
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
    }
}



