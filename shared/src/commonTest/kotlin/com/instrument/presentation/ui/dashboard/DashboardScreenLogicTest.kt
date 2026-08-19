package com.instrument.presentation.ui.dashboard

import com.instrument.domain.model.GasLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardScreenLogicTest {

    @Test
    fun alarmActiveかつレベルありでオーバーレイ表示() {
        assertTrue(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.WARNING,  isSnoozed = false))
        assertTrue(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.DANGER,   isSnoozed = false))
        assertTrue(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.CRITICAL, isSnoozed = false))
    }

    @Test
    fun alarmActiveでもレベルなしなら非表示() {
        assertFalse(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = null, isSnoozed = false))
    }

    @Test
    fun alarmInactiveならレベルありでも非表示() {
        assertFalse(shouldShowAlarmOverlay(isAlarmActive = false, alarmLevel = GasLevel.CRITICAL, isSnoozed = false))
    }

    // ── スヌーズ中のオーバーレイ制御 ──────────────────────────────────────

    @Test
    fun スヌーズ中はWARNINGのオーバーレイを抑制する() {
        assertFalse(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.WARNING, isSnoozed = true))
    }

    @Test
    fun スヌーズ中はDANGERのオーバーレイを抑制する() {
        assertFalse(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.DANGER, isSnoozed = true))
    }

    @Test
    fun スヌーズ中でもCRITICALのオーバーレイは表示する() {
        assertTrue(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.CRITICAL, isSnoozed = true))
    }

    @Test
    fun スヌーズ中でもalarmInactiveなら非表示() {
        assertFalse(shouldShowAlarmOverlay(isAlarmActive = false, alarmLevel = GasLevel.WARNING, isSnoozed = true))
    }
}
