package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// GetLogStatisticsUseCase の正常系・境界値を検証するテスト
class GetLogStatisticsUseCaseTest {

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

    // "現在" を 2026-08-25T12:00:00Z (1756123200000L) に固定
    private val fixedNowMs = 1756123200000L
    private val fixedClock = FixedClock(fixedNowMs)
    private val oneDayMs = 24 * 60 * 60 * 1000L
    private val sevenDaysMs = 7 * oneDayMs

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

    // ---- 空データ ----

    @Test
    fun データなしの場合は空統計を返す() = runTest {
        val repo = FakeLogRepository(emptyList())
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(0, result.totalReadings)
        assertEquals(0f, result.minPpm)
        assertEquals(0f, result.maxPpm)
        assertEquals(0f, result.avgPpm)
        assertEquals(0, result.alarmCounts.total)
    }

    @Test
    fun データなしでも日別統計は7件返る() = runTest {
        val repo = FakeLogRepository(emptyList())
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(GetLogStatisticsUseCase.DAYS, result.dailyStats.size)
    }

    // ---- 期間フィルタ ----

    @Test
    fun 集計期間より前のデータは除外される() = runTest {
        val oldTimestamp = fixedNowMs - sevenDaysMs - 1L   // 7日と1ms前 → 除外
        val readings = listOf(geoReading(300f, GasLevel.DANGER, oldTimestamp))
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(0, result.totalReadings)
    }

    @Test
    fun 集計境界ちょうどのデータは含まれる() = runTest {
        val cutoffMs = fixedNowMs - sevenDaysMs
        val readings = listOf(geoReading(100f, GasLevel.WARNING, cutoffMs))
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(1, result.totalReadings)
    }

    @Test
    fun 古いデータと新しいデータが混在する場合は7日以内のみ集計する() = runTest {
        val recentMs = fixedNowMs - oneDayMs              // 1日前 → 含む
        val oldMs    = fixedNowMs - sevenDaysMs - 1000L   // 7日1秒前 → 除外
        val readings = listOf(
            geoReading(200f, GasLevel.DANGER,  recentMs),
            geoReading(400f, GasLevel.CRITICAL, oldMs),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(1, result.totalReadings)
        assertEquals(200f, result.maxPpm)
    }

    // ---- min / max / avg ----

    @Test
    fun minPpmが最小値を返す() = runTest {
        val nowMs = fixedNowMs
        val readings = listOf(
            geoReading(300f, GasLevel.DANGER,   nowMs - 1_000L),
            geoReading(50f,  GasLevel.WARNING,  nowMs - 2_000L),
            geoReading(150f, GasLevel.WARNING,  nowMs - 3_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(50f, result.minPpm)
    }

    @Test
    fun maxPpmが最大値を返す() = runTest {
        val nowMs = fixedNowMs
        val readings = listOf(
            geoReading(300f, GasLevel.DANGER,   nowMs - 1_000L),
            geoReading(50f,  GasLevel.WARNING,  nowMs - 2_000L),
            geoReading(380f, GasLevel.CRITICAL, nowMs - 3_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(380f, result.maxPpm)
    }

    @Test
    fun avgPpmが正しい平均を返す() = runTest {
        val nowMs = fixedNowMs
        val readings = listOf(
            geoReading(100f, GasLevel.WARNING, nowMs - 1_000L),
            geoReading(200f, GasLevel.DANGER,  nowMs - 2_000L),
            geoReading(300f, GasLevel.DANGER,  nowMs - 3_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        // (100 + 200 + 300) / 3 = 200.0
        assertEquals(200f, result.avgPpm, absoluteTolerance = 0.01f)
    }

    // ---- アラームカウント ----

    @Test
    fun WARNINGのみの場合アラームカウントが正しい() = runTest {
        val nowMs = fixedNowMs
        val readings = listOf(
            geoReading(80f,  GasLevel.WARNING, nowMs - 1_000L),
            geoReading(90f,  GasLevel.WARNING, nowMs - 2_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(2, result.alarmCounts.warning)
        assertEquals(0, result.alarmCounts.danger)
        assertEquals(0, result.alarmCounts.critical)
        assertEquals(2, result.alarmCounts.total)
    }

    @Test
    fun レベル混在の場合それぞれ正しくカウントされる() = runTest {
        val nowMs = fixedNowMs
        val readings = listOf(
            geoReading(80f,  GasLevel.WARNING,  nowMs - 1_000L),
            geoReading(250f, GasLevel.DANGER,   nowMs - 2_000L),
            geoReading(250f, GasLevel.DANGER,   nowMs - 3_000L),
            geoReading(380f, GasLevel.CRITICAL, nowMs - 4_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(1, result.alarmCounts.warning)
        assertEquals(2, result.alarmCounts.danger)
        assertEquals(1, result.alarmCounts.critical)
        assertEquals(4, result.alarmCounts.total)
    }

    @Test
    fun SAFEレベルはアラームカウントに含まれない() = runTest {
        val readings = listOf(
            geoReading(10f, GasLevel.SAFE, fixedNowMs - 1_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(0, result.alarmCounts.total)
        assertEquals(1, result.totalReadings)
    }

    // ---- 日別統計 ----

    @Test
    fun 日別統計は常にDAYS件返る() = runTest {
        val readings = listOf(
            geoReading(200f, GasLevel.DANGER, fixedNowMs - 1_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(GetLogStatisticsUseCase.DAYS, result.dailyStats.size)
    }

    @Test
    fun データがない日の日別統計はmaxPpmが0になる() = runTest {
        // 今日だけデータがある
        val readings = listOf(
            geoReading(200f, GasLevel.DANGER, fixedNowMs - 1_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        // 今日以外の6日はデータなし → maxPpm == 0f
        val emptyDays = result.dailyStats.dropLast(1)  // 最後が今日
        assertTrue(emptyDays.all { it.maxPpm == 0f })
    }

    @Test
    fun 同日内で最大ppmが正しく計算される() = runTest {
        // 同日の複数レコード
        val todayMs = fixedNowMs - 60_000L  // 1分前
        val readings = listOf(
            geoReading(150f, GasLevel.WARNING, todayMs),
            geoReading(300f, GasLevel.DANGER,  todayMs - 1_000L),
            geoReading(200f, GasLevel.DANGER,  todayMs - 2_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        val todayStats = result.dailyStats.last()
        assertEquals(300f, todayStats.maxPpm)
        assertEquals(3, todayStats.readingCount)
    }

    @Test
    fun totalReadingsが7日以内のデータ件数と一致する() = runTest {
        val nowMs = fixedNowMs
        val readings = listOf(
            geoReading(100f, GasLevel.WARNING,  nowMs - 1_000L),
            geoReading(200f, GasLevel.DANGER,   nowMs - 2_000L),
            geoReading(300f, GasLevel.CRITICAL, nowMs - 3_000L),
        )
        val repo = FakeLogRepository(readings)
        val useCase = GetLogStatisticsUseCase(repo, fixedClock)

        val result = useCase.getWeeklyStats().first()

        assertEquals(3, result.totalReadings)
    }
}
