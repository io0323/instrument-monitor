package com.instrument.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // ---- カスタム閾値 fromPpm(ppm, warning, danger, critical) のテスト ----

    @Test
    fun カスタム閾値_100_200_300_で99fはSAFE() {
        assertEquals(GasLevel.SAFE, GasLevel.fromPpm(99f, 100f, 200f, 300f))
    }

    @Test
    fun カスタム閾値_100_200_300_で100fはWARNING() {
        assertEquals(GasLevel.WARNING, GasLevel.fromPpm(100f, 100f, 200f, 300f))
    }

    @Test
    fun カスタム閾値_100_200_300_で199fはWARNING() {
        assertEquals(GasLevel.WARNING, GasLevel.fromPpm(199f, 100f, 200f, 300f))
    }

    @Test
    fun カスタム閾値_100_200_300_で200fはDANGER() {
        assertEquals(GasLevel.DANGER, GasLevel.fromPpm(200f, 100f, 200f, 300f))
    }

    @Test
    fun カスタム閾値_100_200_300_で299fはDANGER() {
        assertEquals(GasLevel.DANGER, GasLevel.fromPpm(299f, 100f, 200f, 300f))
    }

    @Test
    fun カスタム閾値_100_200_300_で300fはCRITICAL() {
        assertEquals(GasLevel.CRITICAL, GasLevel.fromPpm(300f, 100f, 200f, 300f))
    }

    @Test
    fun カスタム閾値_引数なしのfromPpmはデフォルト閾値と同一結果を返す() {
        // デフォルト閾値を明示的に渡した場合と同じ結果になることを確認する
        val ppms = listOf(0f, 49f, 50f, 100f, 199f, 200f, 349f, 350f, 400f)
        ppms.forEach { ppm ->
            assertEquals(
                GasLevel.fromPpm(ppm),
                GasLevel.fromPpm(ppm, GasLevel.WARNING_THRESHOLD, GasLevel.DANGER_THRESHOLD, GasLevel.CRITICAL_THRESHOLD),
                "ppm=$ppm でデフォルト閾値と 4 引数版の結果が一致すべき",
            )
        }
    }
}
