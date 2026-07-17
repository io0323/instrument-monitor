package com.instrument.presentation.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

// DashboardScreen の ppmToAngle 純粋ロジックを検証するテスト
class DashboardScreenPpmAngleTest {

    @Test
    fun ppmToAngle_0ppmは0度() {
        assertEquals(0f, ppmToAngle(0f))
    }

    @Test
    fun ppmToAngle_500ppmは140度_最大スウィープ() {
        assertEquals(140f, ppmToAngle(500f))
    }

    @Test
    fun ppmToAngle_250ppmは70度_中間値() {
        assertEquals(70f, ppmToAngle(250f))
    }

    @Test
    fun ppmToAngle_SAFE閾値50ppmは14度() {
        // 50 / 500 * 140 = 14.0
        assertEquals(14f, ppmToAngle(50f))
    }

    @Test
    fun ppmToAngle_WARNING閾値200ppmは56度() {
        // 200 / 500 * 140 = 56.0
        assertEquals(56f, ppmToAngle(200f))
    }

    @Test
    fun ppmToAngle_DANGER閾値350ppmは98度() {
        // 350 / 500 * 140 = 98.0
        assertEquals(98f, ppmToAngle(350f))
    }

    @Test
    fun ppmToAngle_CRITICAL代表値380ppmの角度() {
        // 380 / 500 * 140 = 106.4
        assertEquals(106.4f, ppmToAngle(380f))
    }

    @Test
    fun ppmToAngle_線形補間が正しい() {
        // ppm を2倍にすると角度も2倍になる線形性を確認
        val angle100 = ppmToAngle(100f)
        val angle200 = ppmToAngle(200f)
        assertEquals(angle100 * 2f, angle200, "線形補間: ppm2倍で角度も2倍になるべき")
    }
}

