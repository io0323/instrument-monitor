package com.instrument.presentation.viewmodel

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.LogRepository
import com.instrument.domain.usecase.GetLogStatisticsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNotNull
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

    // テスト用固定クロック
    private class FixedClock(private val epochMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMs)
    }

    // テスト用 FakeLogRepository
    private class FakeLogRepository(
        private val readings: List<GeoTaggedReading> = emptyList(),
    ) : LogRepository {
        override suspend fun save(reading: GeoTaggedReading): Result<Long> = Result.success(0L)
        override fun getAllReadings(): Flow<List<GeoTaggedReading>> = flowOf(readings)
        override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(
            readings.filter { it.level == GasLevel.DANGER || it.level == GasLevel.CRITICAL }
        )
        override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
        override suspend fun exportCsv(): Result<String> = Result.success("")
    }

    // リアクティブに更新可能な FakeLogRepository
    private class MutableFakeLogRepository(
        initialReadings: List<GeoTaggedReading> = emptyList(),
    ) : LogRepository {
        val readingsFlow = MutableStateFlow(initialReadings)
        override suspend fun save(reading: GeoTaggedReading): Result<Long> = Result.success(0L)
        override fun getAllReadings(): Flow<List<GeoTaggedReading>> = readingsFlow
        override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
        override suspend fun exportCsv(): Result<String> = Result.success("")
    }

    // 2026-08-25T12:00:00Z に固定
    private val fixedNowMs = 1756123200000L
    private val fixedClock = FixedClock(fixedNowMs)
    private val oneDayMs = 24 * 60 * 60 * 1000L

    private fun geoReading(
        ppm: Float,
        level: GasLevel,
        timestampMs: Long,
    ) = GeoTaggedReading(
        reading = SensorReading(ppm = ppm, temperature = 25f, humidity = 50f, timestamp = timestampMs),
        lat = 0.0,
        lng = 0.0,
        level = level,
    )

    private fun createViewModel(repo: LogRepository): StatsViewModel {
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)
        return StatsViewModel(useCase)
    }

    // ---- 初期状態 ----

    @Test
    fun 初期状態ではweeklyStatsはnull() = runTest {
        val vm = createViewModel(FakeLogRepository())
        // stateIn の initialValue は null（コレクタがない状態）
        assertNull(vm.weeklyStats.value)
    }

    // ---- データなし ----

    @Test
    fun ログデータが空の場合は空の統計が返る() = runTest {
        val vm = createViewModel(FakeLogRepository(emptyList()))
        // WhileSubscribed なので collect を開始しないと値が流れない
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        assertEquals(0, stats.totalReadings)
        assertEquals(0f, stats.minPpm)
        assertEquals(0f, stats.maxPpm)
        assertEquals(0f, stats.avgPpm)
        assertEquals(0, stats.alarmCounts.total)
    }

    @Test
    fun ログデータが空でも日別統計は7日分返る() = runTest {
        val vm = createViewModel(FakeLogRepository(emptyList()))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        assertEquals(GetLogStatisticsUseCase.DAYS, stats.dailyStats.size)
    }

    // ---- 正常系: データあり ----

    @Test
    fun 過去7日間のリーディングが正しく集計される() = runTest {
        val readings = listOf(
            geoReading(ppm = 30f,  level = GasLevel.SAFE,     timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 100f, level = GasLevel.WARNING,  timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 250f, level = GasLevel.DANGER,   timestampMs = fixedNowMs - 2 * oneDayMs),
        )
        val vm = createViewModel(FakeLogRepository(readings))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        assertEquals(3, stats.totalReadings)
        assertEquals(30f, stats.minPpm)
        assertEquals(250f, stats.maxPpm)
    }

    @Test
    fun アラームカウントが正しく集計される() = runTest {
        val readings = listOf(
            geoReading(ppm = 30f,  level = GasLevel.SAFE,     timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 80f,  level = GasLevel.WARNING,  timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 90f,  level = GasLevel.WARNING,  timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 250f, level = GasLevel.DANGER,   timestampMs = fixedNowMs - 2 * oneDayMs),
            geoReading(ppm = 400f, level = GasLevel.CRITICAL, timestampMs = fixedNowMs - 3 * oneDayMs),
        )
        val vm = createViewModel(FakeLogRepository(readings))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        assertEquals(2, stats.alarmCounts.warning)
        assertEquals(1, stats.alarmCounts.danger)
        assertEquals(1, stats.alarmCounts.critical)
        assertEquals(4, stats.alarmCounts.total)
    }

    // ---- 境界値: 7日外のデータ ----

    @Test
    fun 過去7日より古いデータは集計から除外される() = runTest {
        val eightDaysAgo = fixedNowMs - 8 * oneDayMs
        val readings = listOf(
            geoReading(ppm = 500f, level = GasLevel.CRITICAL, timestampMs = eightDaysAgo),
            geoReading(ppm = 30f,  level = GasLevel.SAFE,     timestampMs = fixedNowMs - oneDayMs),
        )
        val vm = createViewModel(FakeLogRepository(readings))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        // 8日前のデータは除外され、1件のみ集計される
        assertEquals(1, stats.totalReadings)
        assertEquals(30f, stats.minPpm)
        assertEquals(30f, stats.maxPpm)
    }

    @Test
    fun ちょうど7日前のデータは集計に含まれる() = runTest {
        val exactlySevenDaysAgo = fixedNowMs - 7 * oneDayMs
        val readings = listOf(
            geoReading(ppm = 100f, level = GasLevel.WARNING, timestampMs = exactlySevenDaysAgo),
        )
        val vm = createViewModel(FakeLogRepository(readings))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        assertEquals(1, stats.totalReadings)
    }

    // ---- 平均値の計算 ----

    @Test
    fun 平均ppmが正しく計算される() = runTest {
        val readings = listOf(
            geoReading(ppm = 100f, level = GasLevel.WARNING, timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 200f, level = GasLevel.DANGER,  timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 300f, level = GasLevel.DANGER,  timestampMs = fixedNowMs - 2 * oneDayMs),
        )
        val vm = createViewModel(FakeLogRepository(readings))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        assertEquals(200f, stats.avgPpm)
    }

    // ---- リアクティブ更新 ----

    @Test
    fun リポジトリのデータ更新がweeklyStatsに反映される() = runTest {
        val repo = MutableFakeLogRepository(emptyList())
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)
        val vm = StatsViewModel(useCase)
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        // 初期は空
        val initialStats = vm.weeklyStats.value
        assertNotNull(initialStats)
        assertEquals(0, initialStats.totalReadings)

        // リポジトリにデータを追加
        repo.readingsFlow.value = listOf(
            geoReading(ppm = 150f, level = GasLevel.WARNING, timestampMs = fixedNowMs - oneDayMs),
        )
        advanceUntilIdle()

        // 更新が反映される
        val updatedStats = vm.weeklyStats.value
        assertNotNull(updatedStats)
        assertEquals(1, updatedStats.totalReadings)
        assertEquals(150f, updatedStats.maxPpm)
    }

    // ---- 日別統計 ----

    @Test
    fun 日別統計で特定の日にリーディングが正しくグループ化される() = runTest {
        // 全て「昨日」のデータ
        val readings = listOf(
            geoReading(ppm = 50f,  level = GasLevel.WARNING, timestampMs = fixedNowMs - oneDayMs),
            geoReading(ppm = 100f, level = GasLevel.WARNING, timestampMs = fixedNowMs - oneDayMs + 1000),
            geoReading(ppm = 150f, level = GasLevel.WARNING, timestampMs = fixedNowMs - oneDayMs + 2000),
        )
        val vm = createViewModel(FakeLogRepository(readings))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        // 7日分の日別統計がある
        assertEquals(7, stats.dailyStats.size)
        // リーディングのある日は1つだけ
        val daysWithData = stats.dailyStats.filter { it.readingCount > 0 }
        assertEquals(1, daysWithData.size)
        assertEquals(3, daysWithData.first().readingCount)
        assertEquals(150f, daysWithData.first().maxPpm)
    }

    // ---- 単一データ ----

    @Test
    fun リーディングが1件だけの場合もmin_max_avgが一致する() = runTest {
        val readings = listOf(
            geoReading(ppm = 42f, level = GasLevel.SAFE, timestampMs = fixedNowMs - oneDayMs),
        )
        val vm = createViewModel(FakeLogRepository(readings))
        val collector = backgroundScope.launch { vm.weeklyStats.collect {} }
        advanceUntilIdle()

        val stats = vm.weeklyStats.value
        assertNotNull(stats)
        assertEquals(1, stats.totalReadings)
        assertEquals(42f, stats.minPpm)
        assertEquals(42f, stats.maxPpm)
        assertEquals(42f, stats.avgPpm)
    }
}
