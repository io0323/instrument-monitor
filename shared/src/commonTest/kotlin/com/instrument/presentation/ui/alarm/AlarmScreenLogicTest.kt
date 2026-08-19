package com.instrument.presentation.ui.alarm

import com.instrument.domain.model.AppSettings
import com.instrument.domain.model.GasLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ── formatSnoozeRemaining のテスト ────────────────────────────────────────

class FormatSnoozeRemainingTest {

    @Test
    fun ゼロミリ秒は0秒を返す() {
        assertEquals("0秒", formatSnoozeRemaining(0L))
    }

    @Test
    fun 負値は0秒を返す() {
        assertEquals("0秒", formatSnoozeRemaining(-1000L))
    }

    @Test
    fun ちょうど1分は1分00秒を返す() {
        assertEquals("1分00秒", formatSnoozeRemaining(60_000L))
    }

    @Test
    fun `1分30秒は1分30秒を返す`() {
        assertEquals("1分30秒", formatSnoozeRemaining(90_000L))
    }

    @Test
    fun `59秒は59秒を返す`() {
        assertEquals("59秒", formatSnoozeRemaining(59_000L))
    }

    @Test
    fun `5分は5分00秒を返す`() {
        assertEquals("5分00秒", formatSnoozeRemaining(5 * 60_000L))
    }

    @Test
    fun `10分は10分00秒を返す`() {
        assertEquals("10分00秒", formatSnoozeRemaining(10 * 60_000L))
    }

    @Test
    fun 秒の一桁はゼロ埋めされる() {
        // 1分5秒
        assertEquals("1分05秒", formatSnoozeRemaining(65_000L))
    }
}

class AlarmScreenLogicTest {

    // ── buildAlarmThresholds: カスタム閾値の反映 ──────────────────────────

    @Test
    fun デフォルト設定で三行分の閾値が生成される() {
        val thresholds = buildAlarmThresholds(AppSettings())
        assertEquals(3, thresholds.size)
        assertEquals(GasLevel.WARNING,  thresholds[0].level)
        assertEquals(GasLevel.DANGER,   thresholds[1].level)
        assertEquals(GasLevel.CRITICAL, thresholds[2].level)
    }

    @Test
    fun WARNINGの閾値がsettingsのwarningThresholdPpmを使う() {
        val settings   = AppSettings(warningThresholdPpm = 80)
        val thresholds = buildAlarmThresholds(settings)
        assertEquals("80 ppm〜", thresholds[0].range)
    }

    @Test
    fun DANGERの閾値がsettingsのdangerThresholdPpmを使う() {
        val settings   = AppSettings(dangerThresholdPpm = 250)
        val thresholds = buildAlarmThresholds(settings)
        assertEquals("250 ppm〜", thresholds[1].range)
    }

    @Test
    fun CRITICALの閾値がsettingsのcriticalThresholdPpmを使う() {
        val settings   = AppSettings(criticalThresholdPpm = 400)
        val thresholds = buildAlarmThresholds(settings)
        assertEquals("400 ppm〜", thresholds[2].range)
    }

    @Test
    fun カスタム閾値が三つ同時に反映される() {
        val settings = AppSettings(
            warningThresholdPpm  = 60,
            dangerThresholdPpm   = 220,
            criticalThresholdPpm = 380,
        )
        val thresholds = buildAlarmThresholds(settings)
        assertEquals("60 ppm〜",  thresholds[0].range)
        assertEquals("220 ppm〜", thresholds[1].range)
        assertEquals("380 ppm〜", thresholds[2].range)
    }

    // ── buildAlarmThresholds: 音声・振動 OFF 表示 ────────────────────────

    @Test
    fun 音声OFFのとき全レベルのsoundに_OFF_サフィックスがつく() {
        val settings   = AppSettings(soundEnabled = false)
        val thresholds = buildAlarmThresholds(settings)
        thresholds.forEach { threshold ->
            assertTrue(
                threshold.sound.endsWith(" (OFF)"),
                "level=${threshold.level} の sound が (OFF) で終わること: ${threshold.sound}",
            )
        }
    }

    @Test
    fun 振動OFFのとき全レベルのvibrationに_OFF_サフィックスがつく() {
        val settings   = AppSettings(vibrationEnabled = false)
        val thresholds = buildAlarmThresholds(settings)
        thresholds.forEach { threshold ->
            assertTrue(
                threshold.vibration.endsWith(" (OFF)"),
                "level=${threshold.level} の vibration が (OFF) で終わること: ${threshold.vibration}",
            )
        }
    }

    @Test
    fun 音声ONのときsoundに_OFF_サフィックスがつかない() {
        val settings   = AppSettings(soundEnabled = true)
        val thresholds = buildAlarmThresholds(settings)
        thresholds.forEach { threshold ->
            assertFalse(
                threshold.sound.contains("(OFF)"),
                "level=${threshold.level} の sound に (OFF) が含まれないこと: ${threshold.sound}",
            )
        }
    }

    @Test
    fun 振動ONのときvibrationに_OFF_サフィックスがつかない() {
        val settings   = AppSettings(vibrationEnabled = true)
        val thresholds = buildAlarmThresholds(settings)
        thresholds.forEach { threshold ->
            assertFalse(
                threshold.vibration.contains("(OFF)"),
                "level=${threshold.level} の vibration に (OFF) が含まれないこと: ${threshold.vibration}",
            )
        }
    }

    @Test
    fun 音声振動ともOFFのとき両方にサフィックスがつく() {
        val settings = AppSettings(soundEnabled = false, vibrationEnabled = false)
        val thresholds = buildAlarmThresholds(settings)
        thresholds.forEach { threshold ->
            assertTrue(threshold.sound.endsWith(" (OFF)"))
            assertTrue(threshold.vibration.endsWith(" (OFF)"))
        }
    }

    @Test
    fun デフォルト設定では音声振動ともにサフィックスなし() {
        val thresholds = buildAlarmThresholds(AppSettings())
        thresholds.forEach { threshold ->
            assertFalse(threshold.sound.contains("(OFF)"))
            assertFalse(threshold.vibration.contains("(OFF)"))
        }
    }
}
