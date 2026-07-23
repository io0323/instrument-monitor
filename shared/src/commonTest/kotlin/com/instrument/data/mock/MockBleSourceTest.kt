package com.instrument.data.mock

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// MockBleSource の定数・センサーデータ生成ロジックを検証するテスト
class MockBleSourceTest {

    // ---- companion object 定数の値を固定するテスト ----

    @Test
    fun CRITICAL_INTERVALは20() {
        assertEquals(20, MockBleSource.CRITICAL_INTERVAL, "CRITICAL 挿入間隔は 20 回 (= 10 秒 @ 500ms)")
    }

    @Test
    fun EMIT_DELAY_MSは500L() {
        assertEquals(500L, MockBleSource.EMIT_DELAY_MS, "送出間隔は 500ms")
    }

    @Test
    fun CRITICAL_PPMは380f() {
        assertEquals(380f, MockBleSource.CRITICAL_PPM, "CRITICAL 時 ppm は 380f")
    }

    @Test
    fun PPM_AMPLITUDEは150_0() {
        assertEquals(150.0, MockBleSource.PPM_AMPLITUDE)
    }

    @Test
    fun PPM_CENTERは150_0() {
        assertEquals(150.0, MockBleSource.PPM_CENTER)
    }

    @Test
    fun BASE_TEMPは20_0() {
        assertEquals(20.0, MockBleSource.BASE_TEMP)
    }

    @Test
    fun BASE_HUMIDITYは55_0() {
        assertEquals(55.0, MockBleSource.BASE_HUMIDITY)
    }

    // ---- scanDevices() ----

    @Test
    fun scanDevicesは3件のデバイスを返す() = runTest {
        val source = MockBleSource()
        val results = source.scanDevices().toList()

        assertEquals(1, results.size, "scanDevices は1回だけ emit する")
        assertEquals(3, results.first().size, "3台のデバイスが含まれるべき")
    }

    @Test
    fun scanDevicesのデバイス名にinstrumentが含まれる() = runTest {
        val source = MockBleSource()
        val devices = source.scanDevices().toList().first()

        assertTrue(
            devices.all { it.name.contains("instrument") },
            "全デバイス名に 'instrument' が含まれるべき"
        )
    }

    @Test
    fun scanDevicesの各デバイスIDは一意() = runTest {
        val source = MockBleSource()
        val devices = source.scanDevices().toList().first()

        assertEquals(
            devices.size,
            devices.map { it.id }.toSet().size,
            "デバイス ID はすべて一意であるべき"
        )
    }

    // ---- observeSensorData() ----

    @Test
    fun 最初のN件は通常範囲の読み取り値() = runTest {
        val source = MockBleSource()
        // CRITICAL_INTERVAL-1 件取得 → すべて通常データのはず
        val readings = source.observeSensorData()
            .take(MockBleSource.CRITICAL_INTERVAL - 1)
            .toList()

        assertEquals(MockBleSource.CRITICAL_INTERVAL - 1, readings.size)
        // 通常データは 0 〜 300 ppm 程度の範囲に収まる
        readings.forEach { r ->
            assertTrue(r.ppm >= 0f, "ppm は 0 以上: ${r.ppm}")
            assertTrue(r.ppm < MockBleSource.CRITICAL_PPM, "CRITICAL 挿入前は CRITICAL_PPM 未満: ${r.ppm}")
        }
    }

    @Test
    fun CRITICAL_INTERVAL回目の読み取りはCRITICAL_PPM() = runTest {
        val source = MockBleSource()
        // ちょうど CRITICAL_INTERVAL 件目が CRITICAL_PPM になるはず
        val readings = source.observeSensorData()
            .take(MockBleSource.CRITICAL_INTERVAL)
            .toList()

        assertEquals(
            MockBleSource.CRITICAL_PPM,
            readings.last().ppm,
            "${MockBleSource.CRITICAL_INTERVAL} 回目の読み取りは CRITICAL_PPM (${MockBleSource.CRITICAL_PPM}) であるべき"
        )
    }

    @Test
    fun センサー読み取り値のタイムスタンプは0より大きい() = runTest {
        val source = MockBleSource()
        val readings = source.observeSensorData().take(3).toList()

        readings.forEach { r ->
            assertTrue(r.timestamp > 0L, "タイムスタンプは 0 より大きいべき: ${r.timestamp}")
        }
    }
}

