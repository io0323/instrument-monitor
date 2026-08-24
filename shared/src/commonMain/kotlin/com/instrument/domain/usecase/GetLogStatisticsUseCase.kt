package com.instrument.domain.usecase

import com.instrument.domain.model.AlarmCounts
import com.instrument.domain.model.DailyPpmStats
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.model.LogPeriodStats
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 過去 [DAYS] 日間のログ統計を集計するユースケース。
 * SQLDelight の全件取得 Flow を利用して、in-memory で日別・レベル別の集計を行う。
 */
class GetLogStatisticsUseCase(private val logRepository: LogRepository) {

    companion object {
        // 集計対象期間 (日)
        const val DAYS = 7
        // 曜日ラベル (日本語)
        private val DAY_LABELS = listOf("日", "月", "火", "水", "木", "金", "土")
    }

    /** 過去 7 日間のログを集計した [LogPeriodStats] を Flow として返す。 */
    fun getWeeklyStats(): Flow<LogPeriodStats> =
        logRepository.getAllReadings().map { readings ->
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val cutoffMs = nowMs - DAYS * 24 * 60 * 60 * 1000L
            val recent = readings.filter { it.reading.timestamp >= cutoffMs }
            buildStats(recent, nowMs)
        }

    private fun buildStats(readings: List<GeoTaggedReading>, nowMs: Long): LogPeriodStats {
        if (readings.isEmpty()) {
            return LogPeriodStats(
                minPpm        = 0f,
                maxPpm        = 0f,
                avgPpm        = 0f,
                totalReadings = 0,
                dailyStats    = buildEmptyDailyStats(nowMs),
                alarmCounts   = AlarmCounts(0, 0, 0),
            )
        }

        val ppms = readings.map { it.reading.ppm }

        // レベル別カウント (DANGER 以上を対象にする)
        val alarmCounts = AlarmCounts(
            warning  = readings.count { it.level == GasLevel.WARNING },
            danger   = readings.count { it.level == GasLevel.DANGER },
            critical = readings.count { it.level == GasLevel.CRITICAL },
        )

        // 日別集計 (過去 DAYS 日分)
        val tz = TimeZone.currentSystemDefault()
        val readingsByDay = readings.groupBy { reading ->
            // epoch ms → ローカル日付の dayOfWeek (0=Sun .. 6=Sat)
            val localDate = Instant.fromEpochMilliseconds(reading.reading.timestamp)
                .toLocalDateTime(tz)
                .date
            localDate
        }

        // 過去 DAYS 日分を古い順に並べてリストを作る
        val dailyStats = (DAYS - 1 downTo 0).map { offsetDays ->
            val targetMs = nowMs - offsetDays * 24 * 60 * 60 * 1000L
            val targetDate = Instant.fromEpochMilliseconds(targetMs)
                .toLocalDateTime(tz)
                .date

            // dayOfWeek は 1=月曜 .. 7=日曜 (ISO 8601)
            val dowIndex = (targetDate.dayOfWeek.ordinal + 1) % 7  // 0=日, 1=月 .. 6=土
            val dayLabel = DAY_LABELS[dowIndex]

            val dayReadings = readingsByDay[targetDate] ?: emptyList()
            if (dayReadings.isEmpty()) {
                DailyPpmStats(dayLabel = dayLabel, maxPpm = 0f, avgPpm = 0f, readingCount = 0)
            } else {
                val dayPpms = dayReadings.map { it.reading.ppm }
                DailyPpmStats(
                    dayLabel     = dayLabel,
                    maxPpm       = dayPpms.max(),
                    avgPpm       = dayPpms.average().toFloat(),
                    readingCount = dayReadings.size,
                )
            }
        }

        return LogPeriodStats(
            minPpm        = ppms.min(),
            maxPpm        = ppms.max(),
            avgPpm        = ppms.average().toFloat(),
            totalReadings = readings.size,
            dailyStats    = dailyStats,
            alarmCounts   = alarmCounts,
        )
    }

    /** データが空の場合に使う、過去 DAYS 日分の空エントリを生成する。 */
    private fun buildEmptyDailyStats(nowMs: Long): List<DailyPpmStats> {
        val tz = TimeZone.currentSystemDefault()
        return (DAYS - 1 downTo 0).map { offsetDays ->
            val targetMs = nowMs - offsetDays * 24 * 60 * 60 * 1000L
            val targetDate = Instant.fromEpochMilliseconds(targetMs).toLocalDateTime(tz).date
            val dowIndex = (targetDate.dayOfWeek.ordinal + 1) % 7
            DailyPpmStats(dayLabel = DAY_LABELS[dowIndex], maxPpm = 0f, avgPpm = 0f, readingCount = 0)
        }
    }
}
