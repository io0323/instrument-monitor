package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.model.Trend
import com.instrument.domain.repository.GpsRepository
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// LogMeasurementUseCase のビジネスロジックを検証するテスト
class LogMeasurementUseCaseTest {

    private val savedReadings = mutableListOf<GeoTaggedReading>()

    private val fakeLogRepo = object : LogRepository {
        override suspend fun save(reading: GeoTaggedReading): Result<Long> {
            savedReadings.add(reading)
            return Result.success(savedReadings.size.toLong())
        }
        override fun getAllReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
        override suspend fun exportCsv(): Result<String> = Result.success("")
    }

    private val fakeGpsRepo = object : GpsRepository {
        override fun observeLocation(): Flow<Pair<Double, Double>> =
            flowOf(Pair(35.6812, 139.7671))
    }

    private fun buildUseCase() = LogMeasurementUseCase(fakeLogRepo, fakeGpsRepo)

    // GasLevel ごとの代表 ppm を返すヘルパー
    private fun GasLevel.toRepresentativePpm(): Float = when (this) {
        GasLevel.SAFE     -> 30f
        GasLevel.WARNING  -> 100f
        GasLevel.DANGER   -> 250f
        GasLevel.CRITICAL -> 380f
    }

    private fun gasStatus(level: GasLevel) = GasStatus(
        reading = SensorReading(level.toRepresentativePpm(), 25f, 50f, 0L),
        level   = level,
        trend   = Trend.STABLE,
    )

    @Test
    fun SAFE_レベルは保存しない() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.SAFE))
        assertTrue(savedReadings.isEmpty(), "SAFE レベルは DB に保存されてはならない")
    }

    @Test
    fun WARNING_レベルは保存しない() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.WARNING))
        assertTrue(savedReadings.isEmpty(), "WARNING レベルは DB に保存されてはならない")
    }

    @Test
    fun DANGER_レベルは保存する() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.DANGER))
        assertEquals(1, savedReadings.size, "DANGER レベルは DB に1件保存されるべき")
        assertEquals(GasLevel.DANGER, savedReadings.first().level)
    }

    @Test
    fun CRITICAL_レベルは保存する() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.CRITICAL))
        assertEquals(1, savedReadings.size, "CRITICAL レベルは DB に1件保存されるべき")
        assertEquals(GasLevel.CRITICAL, savedReadings.first().level)
    }

    @Test
    fun manualSave_trueならSAFEでも保存する() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.SAFE), manualSave = true)
        assertEquals(1, savedReadings.size, "manualSave=true の場合は SAFE でも保存されるべき")
    }

    @Test
    fun manualSave_trueならWARNINGでも保存する() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.WARNING), manualSave = true)
        assertEquals(1, savedReadings.size, "manualSave=true の場合は WARNING でも保存されるべき")
    }

    @Test
    fun 保存時にGPS座標が付与される() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.DANGER))
        val saved = savedReadings.first()
        assertEquals(35.6812, saved.lat, "東京の緯度が保存されるべき")
        assertEquals(139.7671, saved.lng, "東京の経度が保存されるべき")
    }

    @Test
    fun 保存時にppm値が正しく保持される() = runTest {
        buildUseCase().invoke(gasStatus(GasLevel.DANGER))
        assertEquals(250f, savedReadings.first().reading.ppm)
    }

    @Test
    fun GPS取得タイムアウト時はフォールバック座標_0_0_で保存される() = runTest {
        // 1000ms以上遅延するGPSリポジトリでタイムアウトをシミュレート
        val slowGpsRepo = object : GpsRepository {
            override fun observeLocation(): Flow<Pair<Double, Double>> = flow {
                delay(5_000L) // UseCase の timeout (1000ms) より長い
                emit(Pair(35.6812, 139.7671))
            }
        }
        val useCase = LogMeasurementUseCase(fakeLogRepo, slowGpsRepo)
        val result = useCase.invoke(gasStatus(GasLevel.DANGER))

        assertTrue(result.isSuccess)
        assertEquals(1, savedReadings.size)
        assertEquals(0.0, savedReadings.first().lat,  "タイムアウト時は lat=0.0 でフォールバック")
        assertEquals(0.0, savedReadings.first().lng,  "タイムアウト時は lng=0.0 でフォールバック")
    }

    @Test
    fun save失敗時はResultFailureが返る() = runTest {
        val failingLogRepo = object : LogRepository {
            override suspend fun save(reading: GeoTaggedReading): Result<Long> =
                Result.failure(RuntimeException("DB write failed"))
            override fun getAllReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
            override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
            override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
            override suspend fun exportCsv(): Result<String> = Result.success("")
        }
        val useCase = LogMeasurementUseCase(failingLogRepo, fakeGpsRepo)
        val result = useCase.invoke(gasStatus(GasLevel.DANGER))

        assertTrue(result.isFailure, "save 失敗時は Result.failure が返るべき")
        assertEquals("DB write failed", result.exceptionOrNull()?.message)
    }
}


