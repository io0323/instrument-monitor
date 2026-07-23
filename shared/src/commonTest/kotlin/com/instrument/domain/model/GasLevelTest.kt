package com.instrument.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

// GasLevel.fromPpm() と閾値定数の直接ユニットテスト
class GasLevelTest {

    // ---- 閾値定数の値を固定するテスト ----
    // 定数値が変更された場合に気づけるよう、意図的に具体値で検証する

    @Test
    fun WARNING_THRESHOLD定数は50f() {
        assertEquals(50f, GasLevel.WARNING_THRESHOLD, "WARNING 閾値は JIS T 8201 準拠で 50 ppm")
    }

    @Test
    fun DANGER_THRESHOLD定数は200f() {
        assertEquals(200f, GasLevel.DANGER_THRESHOLD, "DANGER 閾値は JIS T 8201 準拠で 200 ppm")
    }

    @Test
    fun CRITICAL_THRESHOLD定数は350f() {
        assertEquals(350f, GasLevel.CRITICAL_THRESHOLD, "CRITICAL 閾値は JIS T 8201 準拠で 350 ppm")
    }

    // ---- fromPpm() 境界値テスト ----

    @Test
    fun fromPpm_0ppmはSAFE() {
        assertEquals(GasLevel.SAFE, GasLevel.fromPpm(0f))
    }

    @Test
    fun fromPpm_WARNING閾値未満の49_9fはSAFE() {
        assertEquals(GasLevel.SAFE, GasLevel.fromPpm(49.9f))
    }

    @Test
    fun fromPpm_WARNING閾値ちょうど50fはWARNING() {
        assertEquals(GasLevel.WARNING, GasLevel.fromPpm(50f))
    }

    @Test
    fun fromPpm_WARNINGの代表値100fはWARNING() {
        assertEquals(GasLevel.WARNING, GasLevel.fromPpm(100f))
    }

    @Test
    fun fromPpm_DANGER閾値未満の199_9fはWARNING() {
        assertEquals(GasLevel.WARNING, GasLevel.fromPpm(199.9f))
    }

    @Test
    fun fromPpm_DANGER閾値ちょうど200fはDANGER() {
        assertEquals(GasLevel.DANGER, GasLevel.fromPpm(200f))
    }

    @Test
    fun fromPpm_DANGERの代表値250fはDANGER() {
        assertEquals(GasLevel.DANGER, GasLevel.fromPpm(250f))
    }

    @Test
    fun fromPpm_CRITICAL閾値未満の349_9fはDANGER() {
        assertEquals(GasLevel.DANGER, GasLevel.fromPpm(349.9f))
    }

    @Test
    fun fromPpm_CRITICAL閾値ちょうど350fはCRITICAL() {
        assertEquals(GasLevel.CRITICAL, GasLevel.fromPpm(350f))
    }

    @Test
    fun fromPpm_CRITICALの代表値380fはCRITICAL() {
        assertEquals(GasLevel.CRITICAL, GasLevel.fromPpm(380f))
    }

    @Test
    fun fromPpm_非常に大きい値もCRITICAL() {
        assertEquals(GasLevel.CRITICAL, GasLevel.fromPpm(Float.MAX_VALUE))
    }

    // ---- GasLevel の順序テスト (比較演算子に依存するコードの安全網) ----

    @Test
    fun SAFEはWARNINGより小さい() {
        assertTrue(GasLevel.SAFE < GasLevel.WARNING, "SAFE < WARNING でなければならない")
    }

    @Test
    fun WARNINGはDANGERより小さい() {
        assertTrue(GasLevel.WARNING < GasLevel.DANGER, "WARNING < DANGER でなければならない")
    }

    @Test
    fun DANGERはCRITICALより小さい() {
        assertTrue(GasLevel.DANGER < GasLevel.CRITICAL, "DANGER < CRITICAL でなければならない")
    }

    // ---- entries の網羅性テスト ----

    @Test
    fun GasLevelのentriesは4件() {
        assertEquals(4, GasLevel.entries.size, "GasLevel は SAFE/WARNING/DANGER/CRITICAL の4種類")
    }
}

// Enum の比較演算子を補助する拡張関数 (テスト内のみ)
private operator fun GasLevel.compareTo(other: GasLevel): Int = this.ordinal.compareTo(other.ordinal)
private fun assertTrue(value: Boolean, message: String) = kotlin.test.assertTrue(value, message)

