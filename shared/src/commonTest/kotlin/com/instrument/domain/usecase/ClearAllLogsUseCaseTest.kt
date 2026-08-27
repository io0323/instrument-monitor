package com.instrument.domain.usecase

import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ClearAllLogsUseCase の正常系・異常系を検証するテスト
class ClearAllLogsUseCaseTest {

    // 削除リクエストを記録する FakeLogRepository
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

    // ---- 正常系 ----

    @Test
    fun 呼び出しでdeleteOlderThanにLongMaxValueを渡す() = runTest {
        val repo = FakeLogRepository()
        val useCase = ClearAllLogsUseCase(repo)

        useCase()

        assertEquals(1, repo.deletedCutoffs.size)
        assertEquals(Long.MAX_VALUE, repo.deletedCutoffs.first())
    }

    @Test
    fun リポジトリが成功した場合はResultSuccessを返す() = runTest {
        val repo = FakeLogRepository(deleteResult = Result.success(Unit))

        val result = ClearAllLogsUseCase(repo).invoke()

        assertTrue(result.isSuccess)
    }

    @Test
    fun deleteOlderThanへの呼び出しは1回だけ行われる() = runTest {
        val repo = FakeLogRepository()

        ClearAllLogsUseCase(repo).invoke()

        assertEquals(1, repo.deletedCutoffs.size)
    }

    // ---- 異常系 ----

    @Test
    fun リポジトリが失敗した場合はResultFailureを返す() = runTest {
        val repo = FakeLogRepository(
            deleteResult = Result.failure(RuntimeException("DB error")),
        )

        val result = ClearAllLogsUseCase(repo).invoke()

        assertTrue(result.isFailure)
        assertEquals("DB error", result.exceptionOrNull()?.message)
    }

    @Test
    fun 連続して呼び出しても毎回LongMaxValueを渡す() = runTest {
        val repo = FakeLogRepository()
        val useCase = ClearAllLogsUseCase(repo)

        useCase()
        useCase()

        assertEquals(2, repo.deletedCutoffs.size)
        repo.deletedCutoffs.forEach { cutoff ->
            assertEquals(Long.MAX_VALUE, cutoff)
        }
    }
}
