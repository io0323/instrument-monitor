package com.instrument.domain.usecase

import com.instrument.data.alarm.AlarmController
import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmUseCaseTest {

    private fun reading(ppm: Float) = SensorReading(ppm, 25f, 50f, 0L)

    private fun fakeMonitor(vararg ppms: Float): MonitorGasUseCase {
        val repo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun observeSensorData(): Flow<SensorReading> = flowOf(*ppms.map { reading(it) }.toTypedArray())
            override suspend fun disconnect() {}
        }
        return MonitorGasUseCase(repo)
    }

    @Test
    fun safeレベルはdismissが呼ばれ_triggerは呼ばれない() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        var dismissCount = 0
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() { dismissCount++ }
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(30f), controller).observe().toList()

        assertTrue(triggeredLevels.isEmpty())
        assertEquals(1, dismissCount)
    }

    @Test
    fun 同一レベルは30秒以内に連続発報しない() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(100f, 120f), controller).observe().toList()

        assertEquals(listOf(GasLevel.WARNING), triggeredLevels)
    }

    @Test
    fun レベルが変われば30秒以内でも再発報する() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(100f, 250f), controller).observe().toList()

        assertEquals(listOf(GasLevel.WARNING, GasLevel.DANGER), triggeredLevels)
    }

    @Test
    fun CRITICALレベルでtriggerが呼ばれる() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(380f), controller).observe().toList()

        assertEquals(listOf(GasLevel.CRITICAL), triggeredLevels)
    }

    @Test
    fun SAFE後に再度WARNINGが来るとtriggerが呼ばれる() = runTest {
        // SAFE → WARNING の順で来た場合、lastAlarmLevel がリセットされて再発報すべき
        val triggeredLevels = mutableListOf<GasLevel>()
        var dismissCount = 0
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() { dismissCount++ }
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(30f, 100f), controller).observe().toList()

        // SAFE → dismiss が1回、その後の WARNING で trigger が1回
        assertEquals(1, dismissCount)
        assertEquals(listOf(GasLevel.WARNING), triggeredLevels)
    }

    @Test
    fun すべての危険レベルが順に発報される() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        // WARNING → DANGER → CRITICAL と段階的に上昇
        AlarmUseCase(fakeMonitor(100f, 250f, 380f), controller).observe().toList()

        assertEquals(
            listOf(GasLevel.WARNING, GasLevel.DANGER, GasLevel.CRITICAL),
            triggeredLevels
        )
    }
}
