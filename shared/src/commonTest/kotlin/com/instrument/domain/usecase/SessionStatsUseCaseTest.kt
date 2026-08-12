package com.instrument.domain.usecase

import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.SensorReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStatsUseCaseTest {

    private val useCase = SessionStatsUseCase()

    private fun reading(ppm: Float) = SensorReading(ppm = ppm, temperature = 25f, humidity = 50f, timestamp = 0L)

    // ---- 空リスト ----

    @Test
    fun 空リストはnullを返す() {
        assertNull(useCase.compute(emptyList()))
    }

    // ---- 件数 ----

    @Test
    fun readingCountが入力リストの件数と一致する() {
        val result = useCase.compute(List(7) { reading(it * 10f) })!!
        assertEquals(7, result.readingCount)
    }

    // ---- min / max ----

    @Test
    fun 単件のリストはminMaxAvgが同値になる() {
        val result = useCase.compute(listOf(reading(100f)))!!
        assertEquals(100f, result.minPpm)
        assertEquals(100f, result.maxPpm)
        assertEquals(100f, result.avgPpm)
    }

    @Test
    fun minPpmが最小値を返す() {
        val result = useCase.compute(listOf(reading(300f), reading(50f), reading(150f)))!!
        assertEquals(50f, result.minPpm)
    }

    @Test
    fun maxPpmが最大値を返す() {
        val result = useCase.compute(listOf(reading(300f), reading(50f), reading(150f)))!!
        assertEquals(300f, result.maxPpm)
    }

    // ---- avgPpm ----

    @Test
    fun avgPpmが正しい平均を返す() {
        // (0 + 100 + 200) / 3 = 100.0
        val result = useCase.compute(listOf(reading(0f), reading(100f), reading(200f)))!!
        assertEquals(100f, result.avgPpm, absoluteTolerance = 0.01f)
    }

    @Test
    fun avgPpmが小数を含む平均を正しく返す() {
        // (10 + 20 + 30) / 3 = 20.0
        val result = useCase.compute(listOf(reading(10f), reading(20f), reading(30f)))!!
        assertEquals(20f, result.avgPpm, absoluteTolerance = 0.01f)
    }

    // ---- peakLevel ----

    @Test
    fun peakLevelがSAFEになる場合() {
        // maxPpm = 30 < 50 → SAFE
        val result = useCase.compute(listOf(reading(10f), reading(30f)))!!
        assertEquals(GasLevel.SAFE, result.peakLevel)
    }

    @Test
    fun peakLevelがWARNINGになる場合() {
        // maxPpm = 100, 50 ≤ 100 < 200 → WARNING
        val result = useCase.compute(listOf(reading(30f), reading(100f)))!!
        assertEquals(GasLevel.WARNING, result.peakLevel)
    }

    @Test
    fun peakLevelがDANGERになる場合() {
        // maxPpm = 250, 200 ≤ 250 < 350 → DANGER
        val result = useCase.compute(listOf(reading(50f), reading(250f)))!!
        assertEquals(GasLevel.DANGER, result.peakLevel)
    }

    @Test
    fun peakLevelがCRITICALになる場合() {
        // maxPpm = 380 ≥ 350 → CRITICAL
        val result = useCase.compute(listOf(reading(100f), reading(380f)))!!
        assertEquals(GasLevel.CRITICAL, result.peakLevel)
    }

    // ---- 境界値 ----

    @Test
    fun WARNING閾値ちょうどはWARNINGになる() {
        // maxPpm = 50.0 (WARNING_THRESHOLD) → WARNING
        val result = useCase.compute(listOf(reading(GasLevel.WARNING_THRESHOLD)))!!
        assertEquals(GasLevel.WARNING, result.peakLevel)
    }

    @Test
    fun DANGER閾値ちょうどはDANGERになる() {
        val result = useCase.compute(listOf(reading(GasLevel.DANGER_THRESHOLD)))!!
        assertEquals(GasLevel.DANGER, result.peakLevel)
    }

    @Test
    fun CRITICAL閾値ちょうどはCRITICALになる() {
        val result = useCase.compute(listOf(reading(GasLevel.CRITICAL_THRESHOLD)))!!
        assertEquals(GasLevel.CRITICAL, result.peakLevel)
    }

    // ---- 大量データ ----

    @Test
    fun 最大件数のリストでもreadingCountが正しい() {
        val result = useCase.compute(List(60) { reading(it.toFloat()) })!!
        assertEquals(60, result.readingCount)
    }

    @Test
    fun 全件同一値のとき統計値が一致する() {
        val result = useCase.compute(List(10) { reading(150f) })!!
        assertEquals(150f, result.minPpm)
        assertEquals(150f, result.maxPpm)
        assertEquals(150f, result.avgPpm, absoluteTolerance = 0.01f)
    }
}
