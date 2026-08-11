package com.instrument.data.notification

import com.instrument.domain.model.GasLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertStateHelperTest {

    // ─── shouldFireAlert ────────────────────────────────────────────────────

    @Test
    fun `DANGER で previous が null のとき発火する`() {
        assertTrue(shouldFireAlert(GasLevel.DANGER, previous = null))
    }

    @Test
    fun `CRITICAL で previous が null のとき発火する`() {
        assertTrue(shouldFireAlert(GasLevel.CRITICAL, previous = null))
    }

    @Test
    fun `DANGER から CRITICAL へ変化したとき発火する`() {
        assertTrue(shouldFireAlert(GasLevel.CRITICAL, previous = GasLevel.DANGER))
    }

    @Test
    fun `CRITICAL から DANGER へ変化したとき発火する`() {
        assertTrue(shouldFireAlert(GasLevel.DANGER, previous = GasLevel.CRITICAL))
    }

    @Test
    fun `DANGER が継続している場合は発火しない (重複通知抑制)`() {
        assertFalse(shouldFireAlert(GasLevel.DANGER, previous = GasLevel.DANGER))
    }

    @Test
    fun `CRITICAL が継続している場合は発火しない (重複通知抑制)`() {
        assertFalse(shouldFireAlert(GasLevel.CRITICAL, previous = GasLevel.CRITICAL))
    }

    @Test
    fun `SAFE では発火しない`() {
        assertFalse(shouldFireAlert(GasLevel.SAFE, previous = null))
    }

    @Test
    fun `WARNING では発火しない`() {
        assertFalse(shouldFireAlert(GasLevel.WARNING, previous = null))
    }

    // ─── shouldCancelAlert ──────────────────────────────────────────────────

    @Test
    fun `DANGER から SAFE へ回復したとき消去する`() {
        assertTrue(shouldCancelAlert(GasLevel.SAFE, previous = GasLevel.DANGER))
    }

    @Test
    fun `CRITICAL から WARNING へ回復したとき消去する`() {
        assertTrue(shouldCancelAlert(GasLevel.WARNING, previous = GasLevel.CRITICAL))
    }

    @Test
    fun `previous が null のとき消去しない (発火済みアラートなし)`() {
        assertFalse(shouldCancelAlert(GasLevel.SAFE, previous = null))
    }

    @Test
    fun `DANGER 継続中は消去しない`() {
        assertFalse(shouldCancelAlert(GasLevel.DANGER, previous = GasLevel.DANGER))
    }

    @Test
    fun `CRITICAL 継続中は消去しない`() {
        assertFalse(shouldCancelAlert(GasLevel.CRITICAL, previous = GasLevel.CRITICAL))
    }
}
