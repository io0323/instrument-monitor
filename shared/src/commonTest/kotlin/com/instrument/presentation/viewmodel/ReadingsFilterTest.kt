package com.instrument.presentation.viewmodel

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.SensorReading
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

// filterReadings() 純粋関数の境界値テスト
class ReadingsFilterTest {

    private val tz = TimeZone.of("Asia/Tokyo")

    // "2026-07-11T03:00:00Z" → JST 2026-07-11 12:00:00
    private val now = Instant.parse("2026-07-11T03:00:00Z")

    private fun reading(epochMs: Long, ppm: Float = 10f) = GeoTaggedReading(
        reading = SensorReading(ppm = ppm, temperature = 25f, humidity = 50f, timestamp = epochMs),
        lat = 35.0, lng = 139.0, level = GasLevel.WARNING,
    )

    private fun epochOf(date: LocalDate, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
            .toInstant(tz).toEpochMilliseconds()

    // ------- ALL -------

    @Test
    fun ALL_フィルターは全件を返す() {
        val all = listOf(reading(0L), reading(1_000L), reading(2_000L))
        val result = filterReadings(all, DateFilter.ALL, now, tz)
        assertEquals(3, result.size)
    }

    @Test
    fun ALL_フィルターは空リストをそのまま返す() {
        val result = filterReadings(emptyList(), DateFilter.ALL, now, tz)
        assertEquals(0, result.size)
    }

    // ------- TODAY -------

    @Test
    fun TODAY_フィルターは当日開始エポックより前を除外する() {
        // JST 今日の 00:00 を計算
        val startOfToday = LocalDate(2026, 7, 11).atStartOfDayIn(tz).toEpochMilliseconds()
        val all = listOf(
            reading(startOfToday - 1, ppm = 10f),  // 前日最後の1ms → 除外
            reading(startOfToday,     ppm = 20f),  // 当日ちょうど → 含む
            reading(startOfToday + 1, ppm = 30f),  // 当日の1ms後 → 含む
        )
        val result = filterReadings(all, DateFilter.TODAY, now, tz)
        assertEquals(listOf(20f, 30f), result.map { it.reading.ppm })
    }

    // ------- WEEK -------

    @Test
    fun WEEK_フィルターはcutoff境界をちょうど含む() {
        val cutoff = now.toEpochMilliseconds() - 7L * 24 * 60 * 60 * 1000
        val all = listOf(
            reading(cutoff - 1, ppm = 10f),  // 除外
            reading(cutoff,     ppm = 20f),  // ちょうど境界 → 含む
            reading(cutoff + 1, ppm = 30f),  // 境界後 → 含む
        )
        val result = filterReadings(all, DateFilter.WEEK, now, tz)
        assertEquals(listOf(20f, 30f), result.map { it.reading.ppm })
    }

    // ------- MONTH -------

    @Test
    fun MONTH_フィルターは月初ちょうどを含む() {
        val startOfMonth = LocalDate(2026, 7, 1).atStartOfDayIn(tz).toEpochMilliseconds()
        val all = listOf(
            reading(startOfMonth - 1, ppm = 10f),  // 前月最後の1ms → 除外
            reading(startOfMonth,     ppm = 20f),  // 月初ちょうど → 含む
            reading(startOfMonth + 1, ppm = 30f),  // 月初の1ms後 → 含む
        )
        val result = filterReadings(all, DateFilter.MONTH, now, tz)
        assertEquals(listOf(20f, 30f), result.map { it.reading.ppm })
    }

    @Test
    fun MONTH_フィルターは前月のデータを除外する() {
        val all = listOf(
            reading(epochOf(LocalDate(2026, 6, 30), 23, 59), ppm = 10f), // 前月末 → 除外
            reading(epochOf(LocalDate(2026, 7,  1),  0,  0), ppm = 20f), // 今月頭 → 含む
        )
        val result = filterReadings(all, DateFilter.MONTH, now, tz)
        assertEquals(listOf(20f), result.map { it.reading.ppm })
    }

    @Test
    fun filterReadings_UTCタイムゾーンで正しく動作する() {
        val utcTz  = TimeZone.of("UTC")
        val utcNow = Instant.parse("2026-07-11T00:00:00Z")
        val startOfToday = LocalDate(2026, 7, 11).atStartOfDayIn(utcTz).toEpochMilliseconds()
        val all = listOf(
            reading(startOfToday - 1, ppm = 10f),
            reading(startOfToday,     ppm = 20f),
        )
        val result = filterReadings(all, DateFilter.TODAY, utcNow, utcTz)
        assertEquals(listOf(20f), result.map { it.reading.ppm })
    }
}

