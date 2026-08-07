package com.instrument.presentation.viewmodel

import com.instrument.data.repository.InMemorySettingsRepository
import com.instrument.domain.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {

    private fun buildViewModel() = SettingsViewModel(InMemorySettingsRepository())

    // ---- GPS ロギングトグル ----

    @Test
    fun 初期状態ではGPSロギングが有効である() {
        val vm = buildViewModel()
        assertTrue(vm.settings.value.gpsLoggingEnabled)
    }

    @Test
    fun toggleGpsLoggingを呼ぶとOFFになる() {
        val vm = buildViewModel()
        vm.toggleGpsLogging()
        assertFalse(vm.settings.value.gpsLoggingEnabled)
    }

    @Test
    fun toggleGpsLoggingを2回呼ぶと元に戻る() {
        val vm = buildViewModel()
        vm.toggleGpsLogging()
        vm.toggleGpsLogging()
        assertTrue(vm.settings.value.gpsLoggingEnabled)
    }

    // ---- ログ保持日数 ----

    @Test
    fun 初期状態のログ保持日数は30日である() {
        val vm = buildViewModel()
        assertEquals(30, vm.settings.value.retentionDays)
    }

    @Test
    fun setRetentionDaysで60日に変更できる() {
        val vm = buildViewModel()
        vm.setRetentionDays(60)
        assertEquals(60, vm.settings.value.retentionDays)
    }

    @Test
    fun setRetentionDaysで90日に変更できる() {
        val vm = buildViewModel()
        vm.setRetentionDays(90)
        assertEquals(90, vm.settings.value.retentionDays)
    }

    @Test
    fun setRetentionDaysで30日に戻せる() {
        val vm = buildViewModel()
        vm.setRetentionDays(60)
        vm.setRetentionDays(30)
        assertEquals(30, vm.settings.value.retentionDays)
    }

    @Test
    fun 有効な保持日数以外を渡すと例外が発生する() {
        val vm = buildViewModel()
        var thrown = false
        try {
            vm.setRetentionDays(45)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "不正な retentionDays で IllegalArgumentException が発生すべき")
    }

    // ---- アラーム再発報間隔 ----

    @Test
    fun 初期状態のアラーム間隔は30秒である() {
        val vm = buildViewModel()
        assertEquals(30, vm.settings.value.alarmSuppressIntervalSec)
    }

    @Test
    fun setAlarmSuppressIntervalSecで15秒に変更できる() {
        val vm = buildViewModel()
        vm.setAlarmSuppressIntervalSec(15)
        assertEquals(15, vm.settings.value.alarmSuppressIntervalSec)
    }

    @Test
    fun setAlarmSuppressIntervalSecで60秒に変更できる() {
        val vm = buildViewModel()
        vm.setAlarmSuppressIntervalSec(60)
        assertEquals(60, vm.settings.value.alarmSuppressIntervalSec)
    }

    @Test
    fun 有効なアラーム間隔以外を渡すと例外が発生する() {
        val vm = buildViewModel()
        var thrown = false
        try {
            vm.setAlarmSuppressIntervalSec(45)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "不正な alarmSuppressIntervalSec で IllegalArgumentException が発生すべき")
    }

    // ---- 複合操作 ----

    @Test
    fun 複数の設定を独立して変更できる() {
        val vm = buildViewModel()
        vm.toggleGpsLogging()
        vm.setRetentionDays(60)
        vm.setAlarmSuppressIntervalSec(15)

        val result = vm.settings.value
        assertEquals(
            AppSettings(gpsLoggingEnabled = false, retentionDays = 60, alarmSuppressIntervalSec = 15),
            result,
        )
    }
}
