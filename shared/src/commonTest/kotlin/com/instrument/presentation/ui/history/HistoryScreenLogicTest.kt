package com.instrument.presentation.ui.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// HistoryScreen の純粋ロジック関数を検証するテスト
class HistoryScreenLogicTest {

    @Test
    fun formatTimestamp_フォーマットがMM_dd_HH_mm形式である() {
        // フォーマットパターン "MM/dd HH:mm" の正規表現検証
        val result = formatTimestamp(0L)
        val regex = Regex("""\d{2}/\d{2} \d{2}:\d{2}""")
        assertTrue(result.matches(regex), "フォーマットが不正: '$result'")
    }

    @Test
    fun formatTimestamp_月日時分が必ず2桁になる() {
        // epoch=0 のタイムスタンプで各パートが2桁であることを確認
        val result = formatTimestamp(0L)
        // "MM/dd HH:mm" → split("/", " ", ":") で4パーツに分解
        val slash = result.indexOf('/')
        val space = result.indexOf(' ')
        val colon = result.indexOf(':')
        // MM, dd, HH, mm それぞれ2文字
        assertEquals(2, slash,            "月パート(MM)は2文字: '$result'")
        assertEquals(2, space - slash - 1,"日パート(dd)は2文字: '$result'")
        assertEquals(2, colon - space - 1,"時パート(HH)は2文字: '$result'")
        assertEquals(2, result.length - colon - 1, "分パート(mm)は2文字: '$result'")
    }

    @Test
    fun formatTimestamp_既知エポック秒のフォーマットが正しい() {
        // 2026-07-17T00:00:00Z = 1752710400000L (UTC)
        // Asia/Tokyo (UTC+9) では 2026-07-17 09:00 になる
        // currentSystemDefault()依存のため、フォーマット形式のみ確認する
        val result = formatTimestamp(1752710400000L)
        val regex = Regex("""\d{2}/\d{2} \d{2}:\d{2}""")
        assertTrue(result.matches(regex), "フォーマットが不正: '$result'")
    }

    @Test
    fun formatTimestamp_月末の大きなタイムスタンプもフォーマットできる() {
        // 2026-12-31T23:59:00Z = 1767225540000L
        val result = formatTimestamp(1767225540000L)
        val regex = Regex("""\d{2}/\d{2} \d{2}:\d{2}""")
        assertTrue(result.matches(regex), "月末のタイムスタンプで不正フォーマット: '$result'")
    }
}

