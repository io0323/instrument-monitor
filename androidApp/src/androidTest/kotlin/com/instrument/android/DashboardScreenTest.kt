package com.instrument.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import com.instrument.domain.model.GasLevel
import com.instrument.presentation.ui.dashboard.AlarmOverlay
import com.instrument.presentation.ui.dashboard.GasGauge
import com.instrument.presentation.ui.theme.InstrumentTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun safe_state_shows_green_gauge() {
        composeTestRule.setContent {
            InstrumentTheme {
                GasGauge(ppm = 30f, level = GasLevel.SAFE)
            }
        }
        composeTestRule.onNodeWithText("30").assertIsDisplayed()
        composeTestRule.onNodeWithText("ppm  SAFE").assertIsDisplayed()
    }

    @Test
    fun critical_state_shows_alarm_overlay() {
        composeTestRule.setContent {
            InstrumentTheme {
                AlarmOverlay(level = GasLevel.CRITICAL, ppm = 380f, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("⚠ 危険レベル検出").assertIsDisplayed()
        composeTestRule.onNodeWithText("380 ppm").assertIsDisplayed()
        composeTestRule.onNodeWithText("確認").assertIsDisplayed()
    }
}
