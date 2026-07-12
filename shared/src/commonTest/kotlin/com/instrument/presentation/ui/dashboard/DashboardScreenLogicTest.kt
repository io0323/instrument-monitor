package com.instrument.presentation.ui.dashboard

import com.instrument.domain.model.GasLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardScreenLogicTest {

    @Test
    fun alarmActiveかつレベルありでオーバーレイ表示() {
        assertTrue(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.WARNING))
        assertTrue(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.DANGER))
        assertTrue(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = GasLevel.CRITICAL))
    }

    @Test
    fun alarmActiveでもレベルなしなら非表示() {
        assertFalse(shouldShowAlarmOverlay(isAlarmActive = true, alarmLevel = null))
    }

    @Test
    fun alarmInactiveならレベルありでも非表示() {
        assertFalse(shouldShowAlarmOverlay(isAlarmActive = false, alarmLevel = GasLevel.CRITICAL))
    }
}

