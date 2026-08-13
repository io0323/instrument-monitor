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

    // ---- アラーム閾値設定 ----

    @Test
    fun 初期状態のWARNING閾値は50ppm() {
        val vm = buildViewModel()
        assertEquals(50, vm.settings.value.warningThresholdPpm)
    }

    @Test
    fun 初期状態のDANGER閾値は200ppm() {
        val vm = buildViewModel()
        assertEquals(200, vm.settings.value.dangerThresholdPpm)
    }

    @Test
    fun 初期状態のCRITICAL閾値は350ppm() {
        val vm = buildViewModel()
        assertEquals(350, vm.settings.value.criticalThresholdPpm)
    }

    @Test
    fun setWarningThresholdで有効な値に変更できる() {
        val vm = buildViewModel()
        vm.setWarningThreshold(80)
        assertEquals(80, vm.settings.value.warningThresholdPpm)
    }

    @Test
    fun setDangerThresholdで有効な値に変更できる() {
        val vm = buildViewModel()
        vm.setDangerThreshold(250)
        assertEquals(250, vm.settings.value.dangerThresholdPpm)
    }

    @Test
    fun setCriticalThresholdで有効な値に変更できる() {
        val vm = buildViewModel()
        vm.setCriticalThreshold(400)
        assertEquals(400, vm.settings.value.criticalThresholdPpm)
    }

    @Test
    fun setWarningThresholdがDANGER以上の場合はIllegalArgumentExceptionが発生する() {
        val vm = buildViewModel()
        var thrown = false
        try {
            vm.setWarningThreshold(200) // デフォルトDANGER == 200
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "WARNING >= DANGER の場合は例外が発生すべき")
    }

    @Test
    fun setDangerThresholdがWARNING以下の場合はIllegalArgumentExceptionが発生する() {
        val vm = buildViewModel()
        var thrown = false
        try {
            vm.setDangerThreshold(50) // デフォルトWARNING == 50
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "DANGER <= WARNING の場合は例外が発生すべき")
    }

    @Test
    fun setCriticalThresholdがDANGER以下の場合はIllegalArgumentExceptionが発生する() {
        val vm = buildViewModel()
        var thrown = false
        try {
            vm.setCriticalThreshold(200) // デフォルトDANGER == 200
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown, "CRITICAL <= DANGER の場合は例外が発生すべき")
    }

    @Test
    fun resetThresholdsToDefaultで全閾値がデフォルトに戻る() {
        val vm = buildViewModel()
        vm.setWarningThreshold(80)
        vm.setDangerThreshold(250)
        vm.setCriticalThreshold(400)
        vm.resetThresholdsToDefault()

        val result = vm.settings.value
        assertEquals(50, result.warningThresholdPpm, "WARNING はデフォルト 50 に戻るべき")
        assertEquals(200, result.dangerThresholdPpm, "DANGER はデフォルト 200 に戻るべき")
        assertEquals(350, result.criticalThresholdPpm, "CRITICAL はデフォルト 350 に戻るべき")
    }

    @Test
    fun setWarningThresholdSafeはDANGER以上でも自動調整して保存する() {
        val vm = buildViewModel()
        // まず DANGER を低めに設定してから WARNING を DANGER 以上に設定しようとする
        vm.setDangerThreshold(80)            // DANGER = 80
        vm.setWarningThresholdSafe(100)      // WARNING を 100 に (> DANGER=80 なので自動調整)
        val result = vm.settings.value
        assertEquals(100, result.warningThresholdPpm, "WARNING は 100 になるべき")
        assertTrue(result.dangerThresholdPpm > 100, "DANGER は WARNING より大きくなるべき")
        assertTrue(result.criticalThresholdPpm > result.dangerThresholdPpm, "CRITICAL は DANGER より大きくなるべき")
    }

    @Test
    fun setCriticalThresholdSafeはDANGER以下でも自動調整して保存する() {
        val vm = buildViewModel()
        // CRITICAL を DANGER デフォルト (200) より小さい 100 に設定すると DANGER が自動調整される
        vm.setCriticalThresholdSafe(100)
        val result = vm.settings.value
        assertEquals(100, result.criticalThresholdPpm, "CRITICAL は 100 になるべき")
        assertTrue(result.dangerThresholdPpm < 100, "DANGER は CRITICAL より小さくなるべき")
        assertTrue(result.warningThresholdPpm < result.dangerThresholdPpm, "WARNING は DANGER より小さくなるべき")
    }
}
