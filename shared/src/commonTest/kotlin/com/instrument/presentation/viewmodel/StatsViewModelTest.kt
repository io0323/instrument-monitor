package com.instrument.presentation.viewmodel

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.LogRepository
import com.instrument.domain.usecase.GetLogStatisticsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // テスト用の固定時計
    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }

    // テスト用の簡易 LogRepository
    private class StubLogRepository(
        private val readings: List<GeoTaggedReading> = emptyList(),
    ) : LogRepository {
        override suspend fun save(reading: GeoTaggedReading): Result<Long> = Result.success(0L)
        override fun getAllReadings(): Flow<List<GeoTaggedReading>> = flowOf(readings)
        override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
        override suspend fun exportCsv(): Result<String> = Result.success("")
    }

    // テスト用ヘルパー: 指定タイムスタンプで GeoTaggedReading を生成
    private fun readingAt(timestampMs: Long, ppm: Float, level: GasLevel = GasLevel.SAFE) =
        GeoTaggedReading(
            reading = SensorReading(
                ppm = ppm,
                temperature = 25f,
                humidity = 50f,
                timestamp = timestampMs,
            ),
            lat = 35.0,
            lng = 139.0,
            level = level,
        )

    // 固定時刻: 2026-07-15T12:00:00Z
    private val fixedNow = Instant.parse("2026-07-15T12:00:00Z")
    private val fixedNowMs = fixedNow.toEpochMilliseconds()
    private val weekMs = 7 * 24 * 60 * 60 * 1000L

    private fun buildViewModel(
        readings: List<GeoTaggedReading> = emptyList(),
    ): StatsViewModel {
        val useCase = GetLogStatisticsUseCase(
            logRepository = StubLogRepository(readings),
            clock = FixedClock(fixedNow),
        )
        return StatsViewModel(useCase)
    }

    // ---- 初期状態 ----

    @Test
    fun 初期状態ではweeklyStatsがnullである() {
        val vm = buildViewModel()
        assertNull(vm.weeklyStats.value)
    }

    // ---- weeklyStats の反映 ----

    @Test
    fun データがない場合weeklyStatsのtotalReadingsは0になる() = runTest {
        val vm = buildViewModel(readings = emptyList())
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertEquals(0, stats?.totalReadings)
        collector.cancel()
    }

    @Test
    fun 過去7日間のデータがweeklyStatsに反映される() = runTest {
        val readings = listOf(
            readingAt(fixedNowMs - 1000, ppm = 100f),
            readingAt(fixedNowMs - 2000, ppm = 200f),
            readingAt(fixedNowMs - 3000, ppm = 300f),
        )
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value!!
        assertEquals(3, stats.totalReadings)
        assertEquals(100f, stats.minPpm)
        assertEquals(300f, stats.maxPpm)
        assertEquals(200f, stats.avgPpm)
        collector.cancel()
    }

    @Test
    fun 過去7日より古いデータは集計に含まれない() = runTest {
        val readings = listOf(
            readingAt(fixedNowMs - 1000, ppm = 50f),              // 7日以内
            readingAt(fixedNowMs - weekMs - 1000, ppm = 999f),    // 7日より前 (範囲外)
        )
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value!!
        assertEquals(1, stats.totalReadings)
        assertEquals(50f, stats.maxPpm)
        collector.cancel()
    }

    @Test
    fun 境界値_ちょうど過去7日前のデータは含まれる() = runTest {
        // cutoff = nowMs - weekMs なので、cutoffMs 以上が対象
        val readings = listOf(
            readingAt(fixedNowMs - weekMs, ppm = 75f),      // ちょうど境界 (含まれる)
            readingAt(fixedNowMs - weekMs - 1, ppm = 999f), // 1ms 前 (含まれない)
        )
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value!!
        assertEquals(1, stats.totalReadings)
        assertEquals(75f, stats.maxPpm)
        collector.cancel()
    }

    // ---- アラームカウント ----

    @Test
    fun アラームレベル別のカウントが正しく集計される() = runTest {
        val readings = listOf(
            readingAt(fixedNowMs - 1000, ppm = 30f, level = GasLevel.SAFE),
            readingAt(fixedNowMs - 2000, ppm = 80f, level = GasLevel.WARNING),
            readingAt(fixedNowMs - 3000, ppm = 250f, level = GasLevel.DANGER),
            readingAt(fixedNowMs - 4000, ppm = 400f, level = GasLevel.CRITICAL),
            readingAt(fixedNowMs - 5000, ppm = 100f, level = GasLevel.WARNING),
        )
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val alarms = vm.weeklyStats.value!!.alarmCounts
        assertEquals(2, alarms.warning)
        assertEquals(1, alarms.danger)
        assertEquals(1, alarms.critical)
        assertEquals(4, alarms.total)
        collector.cancel()
    }

    @Test
    fun SAFEのみの場合アラームカウントは全て0になる() = runTest {
        val readings = listOf(
            readingAt(fixedNowMs - 1000, ppm = 10f, level = GasLevel.SAFE),
            readingAt(fixedNowMs - 2000, ppm = 20f, level = GasLevel.SAFE),
        )
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val alarms = vm.weeklyStats.value!!.alarmCounts
        assertEquals(0, alarms.warning)
        assertEquals(0, alarms.danger)
        assertEquals(0, alarms.critical)
        assertEquals(0, alarms.total)
        collector.cancel()
    }

    // ---- dailyStats ----

    @Test
    fun dailyStatsは7日分のエントリを持つ() = runTest {
        val vm = buildViewModel(readings = emptyList())
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        assertEquals(7, vm.weeklyStats.value?.dailyStats?.size)
        collector.cancel()
    }

    @Test
    fun データがない日のdailyStatsはreadingCount0になる() = runTest {
        val vm = buildViewModel(readings = emptyList())
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val dailyStats = vm.weeklyStats.value!!.dailyStats
        dailyStats.forEach { daily ->
            assertEquals(0, daily.readingCount)
            assertEquals(0f, daily.maxPpm)
            assertEquals(0f, daily.avgPpm)
        }
        collector.cancel()
    }

    // ---- ppm 統計の精度 ----

    @Test
    fun 単一データの場合minとmaxとavgが全て同じになる() = runTest {
        val readings = listOf(
            readingAt(fixedNowMs - 1000, ppm = 123f),
        )
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value!!
        assertEquals(123f, stats.minPpm)
        assertEquals(123f, stats.maxPpm)
        assertEquals(123f, stats.avgPpm)
        assertEquals(1, stats.totalReadings)
        collector.cancel()
    }

    @Test
    fun 複数データの平均が正しく計算される() = runTest {
        val readings = listOf(
            readingAt(fixedNowMs - 1000, ppm = 10f),
            readingAt(fixedNowMs - 2000, ppm = 20f),
            readingAt(fixedNowMs - 3000, ppm = 30f),
            readingAt(fixedNowMs - 4000, ppm = 40f),
        )
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value!!
        assertEquals(10f, stats.minPpm)
        assertEquals(40f, stats.maxPpm)
        assertEquals(25f, stats.avgPpm)
        assertEquals(4, stats.totalReadings)
        collector.cancel()
    }

    // ---- 大量データ ----

    @Test
    fun 大量のデータを正しく集計できる() = runTest {
        // 過去7日間に100件のデータを生成（各 ppm = index + 1）
        val readings = (1..100).map { i ->
            readingAt(
                timestampMs = fixedNowMs - i * 60_000L, // 1分間隔
                ppm = i.toFloat(),
            )
        }
        val vm = buildViewModel(readings)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value!!
        assertEquals(100, stats.totalReadings)
        assertEquals(1f, stats.minPpm)
        assertEquals(100f, stats.maxPpm)
        collector.cancel()
    }
}
