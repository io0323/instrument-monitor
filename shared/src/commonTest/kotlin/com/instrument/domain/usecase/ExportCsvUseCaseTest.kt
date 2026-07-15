package com.instrument.domain.usecase

import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ExportCsvUseCase の正常系・異常系を検証するテスト
class ExportCsvUseCaseTest {

    // exportCsv() が指定文字列を返す FakeLogRepository
    private fun fakeRepo(csvResult: Result<String>): LogRepository = object : LogRepository {
        override suspend fun save(reading: GeoTaggedReading): Result<Long> = Result.success(0L)
        override fun getAllReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override fun getDangerousReadings(): Flow<List<GeoTaggedReading>> = flowOf(emptyList())
        override suspend fun deleteOlderThan(epochMs: Long): Result<Unit> = Result.success(Unit)
        override suspend fun exportCsv(): Result<String> = csvResult
    }

    @Test
    fun 正常系_onSaveが成功するとResultSuccessでファイルパスを返す() = runTest {
        val useCase = ExportCsvUseCase(fakeRepo(Result.success("ppm,temp\n30,25")))
        val result = useCase { _ -> Result.success("/tmp/log.csv") }

        assertTrue(result.isSuccess)
        assertEquals("/tmp/log.csv", result.getOrNull())
    }

    @Test
    fun 正常系_onSaveにCSV文字列が渡される() = runTest {
        val csv = "ppm,temp,humidity\n100,25,60"
        var receivedCsv = ""
        val useCase = ExportCsvUseCase(fakeRepo(Result.success(csv)))

        useCase { received ->
            receivedCsv = received
            Result.success("/tmp/log.csv")
        }

        assertEquals(csv, receivedCsv)
    }

    @Test
    fun 異常系_exportCsvが失敗するとResultFailureを返す() = runTest {
        val useCase = ExportCsvUseCase(fakeRepo(Result.failure(RuntimeException("DB error"))))
        val result = useCase { _ -> Result.success("/tmp/log.csv") }

        assertTrue(result.isFailure)
        assertEquals("DB error", result.exceptionOrNull()?.message)
    }

    @Test
    fun 異常系_onSaveが失敗するとResultFailureを返す() = runTest {
        val useCase = ExportCsvUseCase(fakeRepo(Result.success("ppm,temp\n30,25")))
        val result = useCase { _ -> Result.failure(RuntimeException("write error")) }

        assertTrue(result.isFailure)
        assertEquals("write error", result.exceptionOrNull()?.message)
    }

    @Test
    fun 異常系_onSaveが例外をスローするとResultFailureを返す() = runTest {
        val useCase = ExportCsvUseCase(fakeRepo(Result.success("ppm,temp\n30,25")))
        val result = useCase { _ -> throw RuntimeException("unexpected throw") }

        assertTrue(result.isFailure)
        assertEquals("unexpected throw", result.exceptionOrNull()?.message)
    }
}

