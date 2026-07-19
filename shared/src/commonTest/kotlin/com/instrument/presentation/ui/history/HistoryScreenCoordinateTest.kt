package com.instrument.presentation.ui.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// HistoryScreen の formatCoordinate 純粋ロジックを検証するテスト
class HistoryScreenCoordinateTest {

    @Test
    fun formatCoordinate_正の座標が4桁でフォーマットされる() {
        // 小数点以下4桁 + カンマ区切り
        val result = formatCoordinate(35.6812, 139.7671)
        assertEquals("35.6812,139.7671", result)
    }

    @Test
    fun formatCoordinate_負の座標が正しくフォーマットされる() {
        val result = formatCoordinate(-33.8688, -70.6693)
        assertEquals("-33.8688,-70.6693", result)
    }

    @Test
    fun formatCoordinate_ゼロ座標が4桁でフォーマットされる() {
        val result = formatCoordinate(0.0, 0.0)
        assertEquals("0.0000,0.0000", result)
    }

    @Test
    fun formatCoordinate_小数点以下が4桁未満でもゼロ埋めされる() {
        // 35.1 → "35.1000"
        val result = formatCoordinate(35.1, 139.0)
        assertEquals("35.1000,139.0000", result)
    }

    @Test
    fun formatCoordinate_フォーマットが数値カンマ数値の形式である() {
        val result = formatCoordinate(1.23456, 6.54321)
        // "1.2346,6.5432" (四捨五入は実装依存なのでパターンのみ確認)
        val regex = Regex("""-?\d+\.\d{4},-?\d+\.\d{4}""")
        assertTrue(result.matches(regex), "フォーマットが不正: '$result'")
    }
}

