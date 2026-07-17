package com.instrument.domain.usecase

import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// DeleteOldLogsUseCase の正常系・異常系を検証するテスト
class DeleteOldLogsUseCaseTest {

    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }

    // 削除された cutoffMs を記録する FakeLogRepository
    private class FakeLogRepository(
        private val deleteResult: Result<Unit> = Result.success(Unit),
    ) : LogRepository {
        val deletedCutoffs = mutableListOf<Long>()

        override suspend fun save(reading: GeoTaggedReading): Result<Long> = Result.success(0L)
        override fun getAllReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> {
            deletedCutoffs += epochMs
            return deleteResult
        }
        override suspend fun exportCsv(): Result<String> = Result.success("")
    }

    @Test
    fun デフォルト30日分を保持して古いログを削除する() = runTest {
        // 固定時刻: 2026-07-18T00:00:00Z = 1752796800000L
        val nowMs = 1752796800000L
        val clock = FixedClock(Instant.fromEpochMilliseconds(nowMs))
        val repo = FakeLogRepository()
        val useCase = DeleteOldLogsUseCase(repo, clock)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, repo.deletedCutoffs.size)
        // cutoff = now - 30日 = 1752796800000 - 30*24*60*60*1000 = 1750204800000
        val expected = nowMs - 30L * 24 * 60 * 60 * 1000
        assertEquals(expected, repo.deletedCutoffs.first())
    }

    @Test
    fun daysToKeep_7を指定すると7日分を保持する() = runTest {
        val nowMs = 1752796800000L
        val clock = FixedClock(Instant.fromEpochMilliseconds(nowMs))
        val repo = FakeLogRepository()

        DeleteOldLogsUseCase(repo, clock).invoke(daysToKeep = 7)

        val expected = nowMs - 7L * 24 * 60 * 60 * 1000
        assertEquals(expected, repo.deletedCutoffs.first())
    }

    @Test
    fun daysToKeep_1を指定すると1日分を保持する() = runTest {
        val nowMs = 1752796800000L
        val clock = FixedClock(Instant.fromEpochMilliseconds(nowMs))
        val repo = FakeLogRepository()

        DeleteOldLogsUseCase(repo, clock).invoke(daysToKeep = 1)

        val expected = nowMs - 1L * 24 * 60 * 60 * 1000
        assertEquals(expected, repo.deletedCutoffs.first())
    }

    @Test
    fun daysToKeep_365を指定すると1年分を保持する() = runTest {
        val nowMs = 1752796800000L
        val clock = FixedClock(Instant.fromEpochMilliseconds(nowMs))
        val repo = FakeLogRepository()

        DeleteOldLogsUseCase(repo, clock).invoke(daysToKeep = 365)

        val expected = nowMs - 365L * 24 * 60 * 60 * 1000
        assertEquals(expected, repo.deletedCutoffs.first())
    }

    @Test
    fun リポジトリが失敗した場合はResultFailureを返す() = runTest {
        val clock = FixedClock(Instant.fromEpochMilliseconds(1752796800000L))
        val repo = FakeLogRepository(deleteResult = Result.failure(RuntimeException("DB error")))

        val result = DeleteOldLogsUseCase(repo, clock).invoke()

        assertTrue(result.isFailure)
        assertEquals("DB error", result.exceptionOrNull()?.message)
    }

    @Test
    fun daysToKeep_0以下はIllegalArgumentExceptionをスローする() = runTest {
        val clock = FixedClock(Instant.fromEpochMilliseconds(1752796800000L))
        val repo = FakeLogRepository()
        val useCase = DeleteOldLogsUseCase(repo, clock)

        assertFailsWith<IllegalArgumentException> {
            useCase.invoke(daysToKeep = 0)
        }
    }

    @Test
    fun daysToKeep_負数はIllegalArgumentExceptionをスローする() = runTest {
        val clock = FixedClock(Instant.fromEpochMilliseconds(1752796800000L))
        val repo = FakeLogRepository()
        val useCase = DeleteOldLogsUseCase(repo, clock)

        assertFailsWith<IllegalArgumentException> {
            useCase.invoke(daysToKeep = -1)
        }
    }
}

