package com.instrument.domain.usecase

import com.instrument.data.alarm.AlarmController
import com.instrument.data.repository.InMemorySettingsRepository
import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.BleRepository
import com.instrument.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmUseCaseTest {

    private fun reading(ppm: Float) = SensorReading(ppm, 25f, 50f, 0L)

    /** テスト用に [nowMs] を返す固定クロック */
    private class FixedClock(var nowMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    private val settingsRepo: SettingsRepository = InMemorySettingsRepository()
    private val defaultClock: Clock = Clock.System

    private fun fakeMonitor(vararg ppms: Float): MonitorGasUseCase {
        val repo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(true)
            override fun observeSensorData(): Flow<SensorReading> = flowOf(*ppms.map { reading(it) }.toTypedArray())
            override suspend fun disconnect() {}
        }
        return MonitorGasUseCase(repo, settingsRepo)
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

        AlarmUseCase(fakeMonitor(30f), controller, settingsRepo, defaultClock).observe().toList()

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

        AlarmUseCase(fakeMonitor(100f, 120f), controller, settingsRepo, defaultClock).observe().toList()

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

        AlarmUseCase(fakeMonitor(100f, 250f), controller, settingsRepo, defaultClock).observe().toList()

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

        AlarmUseCase(fakeMonitor(380f), controller, settingsRepo, defaultClock).observe().toList()

        assertEquals(listOf(GasLevel.CRITICAL), triggeredLevels)
    }

    @Test
    fun SAFE後に再度WARNINGが来るとtriggerが呼ばれる() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        var dismissCount = 0
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() { dismissCount++ }
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(30f, 100f), controller, settingsRepo, defaultClock).observe().toList()

        assertEquals(1, dismissCount)
        assertEquals(listOf(GasLevel.WARNING), triggeredLevels)
    }

    @Test
    fun SAFE後に再度同じWARNINGが来ても再発報する() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        var dismissCount = 0
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() { dismissCount++ }
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(100f, 30f, 100f), controller, settingsRepo, defaultClock).observe().toList()

        assertEquals(1, dismissCount, "SAFE (ppm=30f) で dismiss が1回呼ばれるべき")
        assertEquals(
            listOf(GasLevel.WARNING, GasLevel.WARNING),
            triggeredLevels,
            "WARNING → SAFE → WARNING で trigger が2回呼ばれるべき",
        )
    }

    @Test
    fun SAFE後に同一DANGERレベルが再度来ても再発報する() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(250f, 30f, 250f), controller, settingsRepo, defaultClock).observe().toList()

        assertEquals(
            listOf(GasLevel.DANGER, GasLevel.DANGER),
            triggeredLevels,
            "DANGER → SAFE → DANGER で trigger が2回呼ばれるべき",
        )
    }

    @Test
    fun すべての危険レベルが順に発報される() = runTest {
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(fakeMonitor(100f, 250f, 380f), controller, settingsRepo, defaultClock).observe().toList()

        assertEquals(
            listOf(GasLevel.WARNING, GasLevel.DANGER, GasLevel.CRITICAL),
            triggeredLevels
        )
    }

    @Test
    fun releaseはAlarmControllerのreleaseに委譲される() = runTest {
        var releaseCount = 0
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) {}
            override fun dismiss() {}
            override fun release() { releaseCount++ }
        }
        val useCase = AlarmUseCase(fakeMonitor(), controller, settingsRepo, defaultClock)

        useCase.release()

        assertEquals(1, releaseCount, "release() が AlarmController.release() を1回呼ぶべき")
    }

    @Test
    fun dismiss後にreleaseを呼んでも例外が発生しない() = runTest {
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) {}
            override fun dismiss() {}
            override fun release() {}
        }
        val useCase = AlarmUseCase(fakeMonitor(30f), controller, settingsRepo, defaultClock)
        useCase.observe().toList()

        useCase.dismiss()
        useCase.release()
    }

    // ---- スヌーズのテスト ----

    @Test
    fun スヌーズ中はWARNINGが抑制される() = runTest {
        val fixedClock = FixedClock(0L)
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        val useCase = AlarmUseCase(fakeMonitor(100f, 110f), controller, settingsRepo, clock = fixedClock)
        useCase.snooze(5 * 60_000L)
        useCase.observe().toList()

        assertTrue(triggeredLevels.isEmpty(), "スヌーズ中の WARNING は抑制されるべき")
    }

    @Test
    fun スヌーズ中でもCRITICALは発報される() = runTest {
        val fixedClock = FixedClock(0L)
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        val useCase = AlarmUseCase(fakeMonitor(380f), controller, settingsRepo, clock = fixedClock)
        useCase.snooze(5 * 60_000L)
        useCase.observe().toList()

        assertEquals(listOf(GasLevel.CRITICAL), triggeredLevels, "スヌーズ中でも CRITICAL は発報されるべき")
    }

    @Test
    fun スヌーズ期限切れ後はWARNINGが再発報される() = runTest {
        val fixedClock = FixedClock(0L)
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        val useCase = AlarmUseCase(fakeMonitor(100f), controller, settingsRepo, clock = fixedClock)
        useCase.snooze(60_000L)

        fixedClock.nowMs = 61_000L

        useCase.observe().toList()

        assertEquals(listOf(GasLevel.WARNING), triggeredLevels, "スヌーズ期限後は再発報されるべき")
    }

    @Test
    fun cancelSnooze後はすぐにWARNINGが発報される() = runTest {
        val fixedClock = FixedClock(0L)
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        val useCase = AlarmUseCase(fakeMonitor(100f), controller, settingsRepo, clock = fixedClock)
        useCase.snooze(5 * 60_000L)
        useCase.cancelSnooze()
        useCase.observe().toList()

        assertEquals(listOf(GasLevel.WARNING), triggeredLevels, "cancelSnooze 後は WARNING が発報されるべき")
    }

    @Test
    fun SAFEに戻るとスヌーズがクリアされる() = runTest {
        val fixedClock = FixedClock(0L)
        val triggeredLevels = mutableListOf<GasLevel>()
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) { triggeredLevels += level }
            override fun dismiss() {}
            override fun release() {}
        }

        val useCase = AlarmUseCase(fakeMonitor(100f, 30f, 100f), controller, settingsRepo, clock = fixedClock)
        useCase.snooze(5 * 60_000L)
        useCase.observe().toList()

        assertEquals(
            listOf(GasLevel.WARNING),
            triggeredLevels,
            "SAFE でスヌーズがクリアされ、2回目の WARNING は発報されるべき",
        )
    }

    @Test
    fun isSnoozedはスヌーズ設定中にtrueを返す() = runTest {
        val fixedClock = FixedClock(0L)
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) {}
            override fun dismiss() {}
            override fun release() {}
        }

        val useCase = AlarmUseCase(fakeMonitor(), controller, settingsRepo, clock = fixedClock)
        assertFalse(useCase.isSnoozed(), "初期状態はスヌーズ未設定のため false")
        useCase.snooze(60_000L)
        assertTrue(useCase.isSnoozed(), "snooze 後は true を返すべき")
        fixedClock.nowMs = 61_000L
        assertFalse(useCase.isSnoozed(), "期限切れ後は false を返すべき")
    }

    @Test
    fun snoozeRemainingMsはスヌーズ残り時間を返す() = runTest {
        val fixedClock = FixedClock(0L)
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) {}
            override fun dismiss() {}
            override fun release() {}
        }

        val useCase = AlarmUseCase(fakeMonitor(), controller, settingsRepo, clock = fixedClock)
        assertEquals(0L, useCase.snoozeRemainingMs(), "初期状態は 0 を返すべき")
        useCase.snooze(60_000L)
        assertEquals(60_000L, useCase.snoozeRemainingMs(), "スヌーズ設定直後は設定時間を返すべき")
        fixedClock.nowMs = 30_000L
        assertEquals(30_000L, useCase.snoozeRemainingMs(), "30秒後は残り30秒を返すべき")
        fixedClock.nowMs = 65_000L
        assertEquals(0L, useCase.snoozeRemainingMs(), "期限切れ後は 0 を返すべき")
    }

    // ---- Clock DI を使った時間ベースの抑制テスト ----

    @Test
    fun 同一レベルはSUPPRESS_INTERVAL_MS未満では再発報しない() = runTest {
        val fixedClock = FixedClock(0L)
        val triggeredLevels = mutableListOf<GasLevel>()

        val readings = flowOf(
            SensorReading(100f, 25f, 50f, 0L),
            SensorReading(120f, 25f, 50f, 0L),
        )
        val repo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(true)
            override fun observeSensorData(): Flow<SensorReading> = readings
            override suspend fun disconnect() {}
        }
        val monitor = MonitorGasUseCase(repo, settingsRepo)

        var callCount = 0
        val controllerWithClockAdv = object : AlarmController {
            override fun trigger(level: GasLevel) {
                triggeredLevels += level
                callCount++
                if (callCount == 1) {
                    fixedClock.nowMs += AlarmUseCase.SUPPRESS_INTERVAL_MS - 1_000L
                }
            }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(monitor, controllerWithClockAdv, settingsRepo, clock = fixedClock).observe().toList()

        assertEquals(
            listOf(GasLevel.WARNING),
            triggeredLevels,
            "29秒以内の同一レベルは抑制されるべき"
        )
    }

    @Test
    fun 同一レベルでもSUPPRESS_INTERVAL_MS以上経過すれば再発報する() = runTest {
        val fixedClock = FixedClock(0L)
        val triggeredLevels = mutableListOf<GasLevel>()

        val readings = flowOf(
            SensorReading(100f, 25f, 50f, 0L),
            SensorReading(120f, 25f, 50f, 0L),
        )
        val repo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(true)
            override fun observeSensorData(): Flow<SensorReading> = readings
            override suspend fun disconnect() {}
        }
        val monitor = MonitorGasUseCase(repo, settingsRepo)

        var callCount = 0
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) {
                triggeredLevels += level
                callCount++
                if (callCount == 1) {
                    fixedClock.nowMs += AlarmUseCase.SUPPRESS_INTERVAL_MS
                }
            }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(monitor, controller, settingsRepo, clock = fixedClock).observe().toList()

        assertEquals(
            listOf(GasLevel.WARNING, GasLevel.WARNING),
            triggeredLevels,
            "ちょうど SUPPRESS_INTERVAL_MS 経過後の同一レベルは再発報すべき"
        )
    }

    @Test
    fun クロック巻き戻し時は抑制されず即座に再発報する() = runTest {
        val fixedClock = FixedClock(30_000L)
        val triggeredLevels = mutableListOf<GasLevel>()

        val readings = flowOf(
            SensorReading(100f, 25f, 50f, 0L),
            SensorReading(120f, 25f, 50f, 0L),
        )
        val repo = object : BleRepository {
            override fun scanDevices(): Flow<List<GasDevice>> = flowOf(emptyList())
            override fun connect(deviceId: String): Flow<BleConnectionState> = flowOf(BleConnectionState.Connected)
            override fun reconnect(deviceId: String): Flow<Boolean> = flowOf(true)
            override fun observeSensorData(): Flow<SensorReading> = readings
            override suspend fun disconnect() {}
        }
        val monitor = MonitorGasUseCase(repo, settingsRepo)

        var callCount = 0
        val controller = object : AlarmController {
            override fun trigger(level: GasLevel) {
                triggeredLevels += level
                callCount++
                if (callCount == 1) {
                    fixedClock.nowMs -= 60_000L
                }
            }
            override fun dismiss() {}
            override fun release() {}
        }

        AlarmUseCase(monitor, controller, settingsRepo, clock = fixedClock).observe().toList()

        assertEquals(
            listOf(GasLevel.WARNING, GasLevel.WARNING),
            triggeredLevels,
            "時刻巻き戻し (elapsed < 0) 時は抑制されず再発報すべき"
        )
    }
}
