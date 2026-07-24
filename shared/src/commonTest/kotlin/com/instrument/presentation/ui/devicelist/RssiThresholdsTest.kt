package com.instrument.presentation.ui.devicelist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RssiThresholdsTest {

    @Test
    fun STRONG_threshold_is_minus65() {
        assertEquals(-65, RssiThresholds.STRONG)
    }

    @Test
    fun MEDIUM_threshold_is_minus75() {
        assertEquals(-75, RssiThresholds.MEDIUM)
    }

    @Test
    fun WEAK_threshold_is_minus85() {
        assertEquals(-85, RssiThresholds.WEAK)
    }

    @Test
    fun STRONG_gt_MEDIUM() {
        assertTrue(RssiThresholds.STRONG > RssiThresholds.MEDIUM)
    }

    @Test
    fun MEDIUM_gt_WEAK() {
        assertTrue(RssiThresholds.MEDIUM > RssiThresholds.WEAK)
    }

    private fun activeCount(rssi: Int): Int = when {
        rssi > RssiThresholds.STRONG -> 3
        rssi > RssiThresholds.MEDIUM -> 2
        rssi > RssiThresholds.WEAK   -> 1
        else                         -> 0
    }

    @Test
    fun rssi_minus64_returns_3() { assertEquals(3, activeCount(-64)) }

    @Test
    fun rssi_minus65_returns_2() { assertEquals(2, activeCount(-65)) }

    @Test
    fun rssi_minus70_returns_2() { assertEquals(2, activeCount(-70)) }

    @Test
    fun rssi_minus75_returns_1() { assertEquals(1, activeCount(-75)) }

    @Test
    fun rssi_minus80_returns_1() { assertEquals(1, activeCount(-80)) }

    @Test
    fun rssi_minus85_returns_0() { assertEquals(0, activeCount(-85)) }

    @Test
    fun rssi_minus100_returns_0() { assertEquals(0, activeCount(-100)) }
}
