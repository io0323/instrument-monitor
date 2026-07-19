package com.instrument.presentation.ui.dashboard

import com.instrument.domain.model.SensorReading
import kotlin.test.Test
import kotlin.test.assertEquals

// DashboardScreen の computeRecentAverage 純粋ロジックを検証するテスト
class DashboardScreenTrendTest {

    private fun reading(ppm: Float) = SensorReading(ppm = ppm, temperature = 25f, humidity = 50f, timestamp = 0L)

    @Test
    fun computeRecentAverage_空リストは0を返す() {
        assertEquals(0.0, computeRecentAverage(emptyList<SensorReading>()))
    }

    @Test
    fun computeRecentAverage_1件のリストはその値を返す() {
        assertEquals(100.0, computeRecentAverage(listOf(reading(100f))))
    }

    @Test
    fun computeRecentAverage_5件未満は全件平均を返す() {
        // (10 + 20 + 30) / 3 = 20.0
        val history = listOf(reading(10f), reading(20f), reading(30f))
        assertEquals(20.0, computeRecentAverage(history))
    }

    @Test
    fun computeRecentAverage_5件ちょうどは全件平均を返す() {
        // (10 + 20 + 30 + 40 + 50) / 5 = 30.0
        val history = (1..5).map { reading(it * 10f) }
        assertEquals(30.0, computeRecentAverage(history))
    }

    @Test
    fun computeRecentAverage_5件超は末尾5件のみで平均を算出する() {
        // history: [10, 20, 30, 40, 50, 60, 70] → takeLast(5) = [30, 40, 50, 60, 70]
        // 平均 = (30 + 40 + 50 + 60 + 70) / 5 = 50.0
        val history = (1..7).map { reading(it * 10f) }
        assertEquals(50.0, computeRecentAverage(history))
    }

    @Test
    fun computeRecentAverage_全件同一値は同値を返す() {
        val history = List(10) { reading(150f) }
        assertEquals(150.0, computeRecentAverage(history))
    }

    @Test
    fun computeRecentAverage_countパラメータが反映される() {
        // history: [10, 20, 30, 40, 50], count=3 → takeLast(3) = [30, 40, 50] → 40.0
        val history = (1..5).map { reading(it * 10f) }
        assertEquals(40.0, computeRecentAverage(history, count = 3))
    }
}


