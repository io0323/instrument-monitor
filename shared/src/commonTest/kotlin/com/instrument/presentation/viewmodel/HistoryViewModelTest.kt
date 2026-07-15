package com.instrument.presentation.viewmodel

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

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
    fun TODAYフィルターは当日開始以降のみを返す() = runTest {
        val tz = TimeZone.of("Asia/Tokyo")
        val now = Instant.parse("2026-07-11T03:00:00Z")
        val repo = FakeLogRepository(
            listOf(
                readingAt(LocalDate(2026, 7, 10), 23, 59, tz, 10f),
                readingAt(LocalDate(2026, 7, 11), 0, 0, tz, 20f),
                readingAt(LocalDate(2026, 7, 11), 8, 30, tz, 30f),
            )
        )
        val vm = HistoryViewModel(repo, FixedClock(now), tz)

        val collector = backgroundScope.launch { vm.readings.collect { } }
        vm.setDateFilter(DateFilter.TODAY)
        advanceUntilIdle()

        val result = vm.readings.value
        assertEquals(2, result.size)
        assertTrue(result.all { it.reading.ppm >= 20f })
        collector.cancel()
    }

    @Test
    fun WEEKフィルターは7日境界を含める() = runTest {
        val tz = TimeZone.of("UTC")
        val now = Instant.parse("2026-07-11T00:00:00Z")
        val cutoff = now.toEpochMilliseconds() - 7L * 24 * 60 * 60 * 1000
        val repo = FakeLogRepository(
            listOf(
                reading(epochMs = cutoff - 1, ppm = 10f),
                reading(epochMs = cutoff, ppm = 20f),
                reading(epochMs = cutoff + 1, ppm = 30f),
            )
        )
        val vm = HistoryViewModel(repo, FixedClock(now), tz)

        val collector = backgroundScope.launch { vm.readings.collect { } }
        vm.setDateFilter(DateFilter.WEEK)
        advanceUntilIdle()

        val result = vm.readings.value
        assertEquals(listOf(20f, 30f), result.map { it.reading.ppm })
        collector.cancel()
    }

    @Test
    fun MONTHフィルターは月初開始以降のみを返す() = runTest {
        val tz = TimeZone.of("Asia/Tokyo")
        val now = Instant.parse("2026-07-11T03:00:00Z")
        val repo = FakeLogRepository(
            listOf(
                readingAt(LocalDate(2026, 6, 30), 23, 59, tz, 10f),
                readingAt(LocalDate(2026, 7, 1), 0, 0, tz, 20f),
                readingAt(LocalDate(2026, 7, 10), 18, 0, tz, 30f),
            )
        )
        val vm = HistoryViewModel(repo, FixedClock(now), tz)

        val collector = backgroundScope.launch { vm.readings.collect { } }
        vm.setDateFilter(DateFilter.MONTH)
        advanceUntilIdle()

        val result = vm.readings.value
        assertEquals(listOf(20f, 30f), result.map { it.reading.ppm })
        collector.cancel()
    }

    @Test
    fun exportCsv成功時はDoneになる() = runTest {
        val repo = FakeLogRepository(emptyList(), exportResult = Result.success("ppm,lat,lng"))
        val vm = HistoryViewModel(repo)

        vm.exportCsv { csv -> Result.success("/tmp/history.csv?size=${csv.length}") }
        advanceUntilIdle()

        assertEquals(
            HistoryViewModel.ExportState.Done("/tmp/history.csv?size=11"),
            vm.exportState.value
        )
    }

    @Test
    fun exportCsv失敗時はErrorになる() = runTest {
        val repo = FakeLogRepository(emptyList(), exportResult = Result.failure(IllegalStateException("db error")))
        val vm = HistoryViewModel(repo)

        vm.exportCsv { Result.success("/tmp/history.csv") }
        advanceUntilIdle()

        assertEquals(
            HistoryViewModel.ExportState.Error("db error"),
            vm.exportState.value
        )
    }

    @Test
    fun export保存処理が失敗した場合はErrorになる() = runTest {
        val repo = FakeLogRepository(emptyList(), exportResult = Result.success("ppm,lat,lng"))
        val vm = HistoryViewModel(repo)

        vm.exportCsv { Result.failure(IllegalStateException("save failed")) }
        advanceUntilIdle()

        assertEquals(
            HistoryViewModel.ExportState.Error("save failed"),
            vm.exportState.value
        )
    }

    @Test
    fun export保存処理がメッセージなしで失敗した場合は既定文言になる() = runTest {
        val repo = FakeLogRepository(emptyList(), exportResult = Result.success("ppm,lat,lng"))
        val vm = HistoryViewModel(repo)

        vm.exportCsv { Result.failure(IllegalStateException()) }
        advanceUntilIdle()

        assertEquals(
            HistoryViewModel.ExportState.Error("不明なエラー"),
            vm.exportState.value
        )
    }

    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeLogRepository(
        initial: List<GeoTaggedReading>,
        private val exportResult: Result<String> = Result.success(""),
    ) : LogRepository {
        private val readings = MutableStateFlow(initial)

        override suspend fun save(reading: GeoTaggedReading): Result<Long> = Result.success(1L)
        override fun getAllReadings(): Flow<List<GeoTaggedReading>> = readings
        override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = readings
        override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
        override suspend fun exportCsv(): Result<String> = exportResult
    }

    private fun reading(epochMs: Long, ppm: Float): GeoTaggedReading =
        GeoTaggedReading(
            reading = SensorReading(ppm = ppm, temperature = 25f, humidity = 50f, timestamp = epochMs),
            lat = 35.0,
            lng = 139.0,
            level = GasLevel.WARNING,
        )

    private fun readingAt(date: LocalDate, hour: Int, minute: Int, tz: TimeZone, ppm: Float): GeoTaggedReading {
        val localDateTime = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
        val epochMs = localDateTime.toInstant(tz).toEpochMilliseconds()
        return reading(epochMs, ppm)
    }
}



