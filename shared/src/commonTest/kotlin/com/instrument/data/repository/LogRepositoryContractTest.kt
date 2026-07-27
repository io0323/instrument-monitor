package com.instrument.data.repository

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// LogRepository の実装が満たすべき契約テスト。
// FakeLogRepository をリファレンス実装として使用する。
// SqlDelightLogRepository も同じ契約を満たすべきであり、
// 特に save() が自動採番 ID を正確に返すことを保証する。
// (旧実装は selectAll ORDER BY timestamp DESC の lastOrNull() を使っていたため
//  最古レコードの ID を返すバグが存在した)
class LogRepositoryContractTest {

    private lateinit var repo: LogRepository

    @BeforeTest
    fun setUp() {
        repo = FakeLogRepository()
    }

    // ---- save() の ID 採番 ----

    @Test
    fun save_最初の挿入はID_1を返す() = runTest {
        val result = repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        assertEquals(1L, result.getOrThrow(), "最初の save() は ID=1 を返すべき")
    }

    @Test
    fun save_2回目の挿入はID_2を返す() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        val result = repo.save(geoTaggedReading(ppm = 300f, timestamp = 2000L, level = GasLevel.CRITICAL))
        assertEquals(2L, result.getOrThrow(), "2 回目の save() は ID=2 を返すべき")
    }

    @Test
    fun save_タイムスタンプが古いレコードを後から挿入してもIDは単調増加する() = runTest {
        // timestamp が小さい（= 古い）レコードを後から挿入した場合でも
        // ID は挿入順に採番され、最古 timestamp の ID にはならない。
        // これは SqlDelightLogRepository の旧バグ (selectAll DESC lastOrNull) を再現する。
        val id1 = repo.save(geoTaggedReading(ppm = 380f, timestamp = 9000L, level = GasLevel.CRITICAL)).getOrThrow()
        val id2 = repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER)).getOrThrow()

        assertEquals(1L, id1, "先に挿入したレコードは ID=1 であるべき")
        assertEquals(2L, id2, "後から挿入したレコードは ID=2 であるべき（timestamp が古くても）")
    }

    @Test
    fun save_成功時はResultSuccess() = runTest {
        val result = repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        assertTrue(result.isSuccess)
    }

    // ---- getAllReadings() ----

    @Test
    fun getAllReadings_保存したレコードが全件取得できる() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        repo.save(geoTaggedReading(ppm = 380f, timestamp = 2000L, level = GasLevel.CRITICAL))
        repo.save(geoTaggedReading(ppm = 30f,  timestamp = 3000L, level = GasLevel.SAFE))

        val readings = repo.getAllReadings().first()
        assertEquals(3, readings.size, "保存した 3 件全て取得できるべき")
    }

    @Test
    fun getAllReadings_timestamp_DESC順で返す() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        repo.save(geoTaggedReading(ppm = 380f, timestamp = 3000L, level = GasLevel.CRITICAL))
        repo.save(geoTaggedReading(ppm = 100f, timestamp = 2000L, level = GasLevel.WARNING))

        val readings = repo.getAllReadings().first()
        assertEquals(3000L, readings[0].reading.timestamp, "最新 timestamp が先頭")
        assertEquals(2000L, readings[1].reading.timestamp)
        assertEquals(1000L, readings[2].reading.timestamp, "最古 timestamp が末尾")
    }

    @Test
    fun getAllReadings_空のDBは空リストを返す() = runTest {
        val readings = repo.getAllReadings().first()
        assertTrue(readings.isEmpty(), "未保存状態は空リストを返すべき")
    }

    // ---- getDangerousReadings() ----

    @Test
    fun getDangerousReadings_DANGERとCRITICALのみ返す() = runTest {
        repo.save(geoTaggedReading(ppm = 30f,  timestamp = 1000L, level = GasLevel.SAFE))
        repo.save(geoTaggedReading(ppm = 100f, timestamp = 2000L, level = GasLevel.WARNING))
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 3000L, level = GasLevel.DANGER))
        repo.save(geoTaggedReading(ppm = 380f, timestamp = 4000L, level = GasLevel.CRITICAL))

        val dangerous = repo.getDangerousReadings().first()
        assertEquals(2, dangerous.size, "DANGER と CRITICAL の 2 件のみ返すべき")
        assertTrue(dangerous.all { it.level in setOf(GasLevel.DANGER, GasLevel.CRITICAL) })
    }

    @Test
    fun getDangerousReadings_SAFEとWARNINGは含まれない() = runTest {
        repo.save(geoTaggedReading(ppm = 30f,  timestamp = 1000L, level = GasLevel.SAFE))
        repo.save(geoTaggedReading(ppm = 100f, timestamp = 2000L, level = GasLevel.WARNING))

        val dangerous = repo.getDangerousReadings().first()
        assertTrue(dangerous.isEmpty(), "SAFE/WARNING のみの場合は空リストを返すべき")
    }

    @Test
    fun getDangerousReadings_timestamp_DESC順で返す() = runTest {
        repo.save(geoTaggedReading(ppm = 380f, timestamp = 1000L, level = GasLevel.CRITICAL))
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 5000L, level = GasLevel.DANGER))

        val dangerous = repo.getDangerousReadings().first()
        assertEquals(5000L, dangerous[0].reading.timestamp, "新しい timestamp が先頭")
        assertEquals(1000L, dangerous[1].reading.timestamp)
    }

    // ---- deleteOlderThan() ----

    @Test
    fun deleteOlderThan_指定エポックより古いレコードを削除する() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 5000L, level = GasLevel.DANGER))
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 9000L, level = GasLevel.DANGER))

        val result = repo.deleteOlderThan(5000L)
        assertTrue(result.isSuccess)

        val remaining = repo.getAllReadings().first()
        assertEquals(2, remaining.size, "timestamp < 5000 の 1 件のみ削除されるべき")
        assertFalse(remaining.any { it.reading.timestamp < 5000L }, "古いレコードは残らない")
    }

    @Test
    fun deleteOlderThan_全件削除後は空になる() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        repo.deleteOlderThan(Long.MAX_VALUE)

        val remaining = repo.getAllReadings().first()
        assertTrue(remaining.isEmpty(), "全件削除後は空リストを返すべき")
    }

    @Test
    fun deleteOlderThan_条件外レコードは保持される() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 9000L, level = GasLevel.DANGER))

        repo.deleteOlderThan(500L) // timestamp < 500 だけ削除 → 0 件

        val remaining = repo.getAllReadings().first()
        assertEquals(2, remaining.size, "削除対象外のレコードは保持されるべき")
    }

    // ---- exportCsv() ----

    @Test
    fun exportCsv_正しいCSVヘッダを含む() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))

        val csv = repo.exportCsv().getOrThrow()
        val header = csv.lines().first()
        assertEquals("timestamp,ppm,temperature,humidity,lat,lng,level", header)
    }

    @Test
    fun exportCsv_保存したデータが含まれる() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))

        val csv = repo.exportCsv().getOrThrow()
        assertTrue(csv.contains("1000"), "timestamp が CSV に含まれるべき")
        assertTrue(csv.contains("DANGER"), "gas_level が CSV に含まれるべき")
    }

    @Test
    fun exportCsv_データなしはヘッダのみ() = runTest {
        val csv = repo.exportCsv().getOrThrow()
        val lines = csv.trim().lines()
        assertEquals(1, lines.size, "データなしの場合はヘッダ行のみ出力されるべき")
        assertEquals("timestamp,ppm,temperature,humidity,lat,lng,level", lines.first())
    }

    @Test
    fun exportCsv_複数件は全て含まれる() = runTest {
        repo.save(geoTaggedReading(ppm = 250f, timestamp = 1000L, level = GasLevel.DANGER))
        repo.save(geoTaggedReading(ppm = 380f, timestamp = 2000L, level = GasLevel.CRITICAL))

        val csv = repo.exportCsv().getOrThrow()
        // ヘッダ + 2データ行 + 末尾改行
        val nonEmptyLines = csv.trim().lines()
        assertEquals(3, nonEmptyLines.size, "ヘッダ + 2 件のデータ行が出力されるべき")
    }

    // ---- ヘルパー ----

    private fun geoTaggedReading(
        ppm: Float,
        timestamp: Long,
        level: GasLevel,
        lat: Double = 35.6812,
        lng: Double = 139.7671,
    ) = GeoTaggedReading(
        reading = SensorReading(
            ppm         = ppm,
            temperature = 25f,
            humidity    = 50f,
            timestamp   = timestamp,
        ),
        lat   = lat,
        lng   = lng,
        level = level,
    )
}
