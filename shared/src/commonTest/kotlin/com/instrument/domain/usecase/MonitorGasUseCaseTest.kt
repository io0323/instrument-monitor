package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.SensorReading
import com.instrument.domain.model.Trend
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import com.instrument.domain.model.GasDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MonitorGasUseCaseTest {

    private fun reading(ppm: Float) = SensorReading(ppm, 25f, 50f, 0L)

    private fun fakeRepo(vararg ppms: Float): BleRepository = object : BleRepository {
        override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
        override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
        override fun observeSensorData(): Flow<SensorReading> = flowOf(*ppms.map { reading(it) }.toTypedArray())
        override suspend fun disconnect() {}
    }

    @Test
    fun gasLevel_safe() = runTest {
        val result = MonitorGasUseCase(fakeRepo(30f)).invoke().toList()
        assertEquals(GasLevel.SAFE, result.first().level)
    }

    @Test
    fun gasLevel_warning() = runTest {
        val result = MonitorGasUseCase(fakeRepo(100f)).invoke().toList()
        assertEquals(GasLevel.WARNING, result.first().level)
    }

    @Test
    fun gasLevel_danger() = runTest {
        val result = MonitorGasUseCase(fakeRepo(250f)).invoke().toList()
        assertEquals(GasLevel.DANGER, result.first().level)
    }

    @Test
    fun gasLevel_critical() = runTest {
        val result = MonitorGasUseCase(fakeRepo(400f)).invoke().toList()
        assertEquals(GasLevel.CRITICAL, result.first().level)
    }

    @Test
    fun trend_stable_when_fewer_than_5_readings() = runTest {
        val result = MonitorGasUseCase(fakeRepo(100f, 120f, 140f)).invoke().toList()
        result.forEach { assertEquals(Trend.STABLE, it.trend) }
    }

    @Test
    fun trend_rising() = runTest {
        val result = MonitorGasUseCase(fakeRepo(100f, 105f, 110f, 115f, 120f)).invoke().toList()
        assertEquals(Trend.RISING, result.last().trend)
    }

    @Test
    fun trend_falling() = runTest {
        val result = MonitorGasUseCase(fakeRepo(120f, 115f, 110f, 105f, 100f)).invoke().toList()
        assertEquals(Trend.FALLING, result.last().trend)
    }

    // ガスレベル境界値テスト（JIS T 8201準拠の閾値を各境界で検証）

    @Test
    fun gasLevel_境界値_49_9f_はSAFE() = runTest {
        val result = MonitorGasUseCase(fakeRepo(49.9f)).invoke().toList()
        assertEquals(GasLevel.SAFE, result.first().level)
    }

    @Test
    fun gasLevel_境界値_50f_はWARNING() = runTest {
        val result = MonitorGasUseCase(fakeRepo(50f)).invoke().toList()
        assertEquals(GasLevel.WARNING, result.first().level)
    }

    @Test
    fun gasLevel_境界値_199_9f_はWARNING() = runTest {
        val result = MonitorGasUseCase(fakeRepo(199.9f)).invoke().toList()
        assertEquals(GasLevel.WARNING, result.first().level)
    }

    @Test
    fun gasLevel_境界値_200f_はDANGER() = runTest {
        val result = MonitorGasUseCase(fakeRepo(200f)).invoke().toList()
        assertEquals(GasLevel.DANGER, result.first().level)
    }

    @Test
    fun gasLevel_境界値_349_9f_はDANGER() = runTest {
        val result = MonitorGasUseCase(fakeRepo(349.9f)).invoke().toList()
        assertEquals(GasLevel.DANGER, result.first().level)
    }

    @Test
    fun gasLevel_境界値_350f_はCRITICAL() = runTest {
        val result = MonitorGasUseCase(fakeRepo(350f)).invoke().toList()
        assertEquals(GasLevel.CRITICAL, result.first().level)
    }

    @Test
    fun gasLevel_境界値_0f_はSAFE() = runTest {
        val result = MonitorGasUseCase(fakeRepo(0f)).invoke().toList()
        assertEquals(GasLevel.SAFE, result.first().level)
    }

    @Test
    fun trend_diff10f超はRISING() = runTest {
        // 差分 = 110f - 99f = 11f > 10f → RISING
        val result = MonitorGasUseCase(fakeRepo(99f, 101f, 103f, 107f, 110f)).invoke().toList()
        assertEquals(Trend.RISING, result.last().trend)
    }

    @Test
    fun trend_diff10f以下はSTABLE() = runTest {
        // 差分 = 109f - 100f = 9f ≤ 10f → STABLE
        val result = MonitorGasUseCase(fakeRepo(100f, 102f, 104f, 106f, 109f)).invoke().toList()
        assertEquals(Trend.STABLE, result.last().trend)
    }

    @Test
    fun trend_diff丁度10fはSTABLE() = runTest {
        // 差分 = 110f - 100f = 10f ちょうど → STABLE（> 10f が条件）
        val result = MonitorGasUseCase(fakeRepo(100f, 102f, 104f, 106f, 110f)).invoke().toList()
        assertEquals(Trend.STABLE, result.last().trend)
    }

    @Test
    fun trend_diff丁度マイナス10fはSTABLE() = runTest {
        // 差分 = 100f - 110f = -10f ちょうど → STABLE（< -10f が条件）
        val result = MonitorGasUseCase(fakeRepo(110f, 108f, 106f, 104f, 100f)).invoke().toList()
        assertEquals(Trend.STABLE, result.last().trend)
    }
}
