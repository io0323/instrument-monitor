package com.instrument.domain.usecase

import com.instrument.data.ble.toSensorReading
import kotlin.test.Test
import kotlin.test.assertEquals

// BLE パケット → SensorReading の変換ロジックを検証するテスト
class BleParserTest {

    // [ppm_hi, ppm_lo, temp_x10_hi, temp_x10_lo, hum_x10_hi, hum_x10_lo] 形式のパケット生成
    private fun makePacket(ppm: Int, tempX10: Int, humX10: Int): ByteArray = byteArrayOf(
        (ppm shr 8).toByte(),
        (ppm and 0xFF).toByte(),
        (tempX10 shr 8).toByte(),
        (tempX10 and 0xFF).toByte(),
        (humX10 shr 8).toByte(),
        (humX10 and 0xFF).toByte(),
    )

    @Test
    fun ppm_パースが正しい() {
        val packet = makePacket(ppm = 250, tempX10 = 250, humX10 = 550)
        assertEquals(250f, packet.toSensorReading().ppm)
    }

    @Test
    fun temperature_パースが正しい() {
        // tempX10 = 252 → 25.2℃
        val packet = makePacket(ppm = 0, tempX10 = 252, humX10 = 0)
        assertEquals(25.2f, packet.toSensorReading().temperature)
    }

    @Test
    fun humidity_パースが正しい() {
        // humX10 = 600 → 60.0%
        val packet = makePacket(ppm = 0, tempX10 = 0, humX10 = 600)
        assertEquals(60.0f, packet.toSensorReading().humidity)
    }

    @Test
    fun 最大値ppm_のオーバーフローなし() {
        // ppm = 65535 (0xFFFF) → 65535f
        val packet = makePacket(ppm = 65535, tempX10 = 0, humX10 = 0)
        assertEquals(65535f, packet.toSensorReading().ppm)
    }

    @Test
    fun ppm_ゼロ値が正しくパースされる() {
        val packet = makePacket(ppm = 0, tempX10 = 0, humX10 = 0)
        assertEquals(0f, packet.toSensorReading().ppm)
        assertEquals(0f, packet.toSensorReading().temperature)
        assertEquals(0f, packet.toSensorReading().humidity)
    }

    @Test
    fun 典型的なガス検知値をパースできる() {
        // ppm=200, temp=25.5℃, humidity=55.0%
        val packet = makePacket(ppm = 200, tempX10 = 255, humX10 = 550)
        val reading = packet.toSensorReading()
        assertEquals(200f, reading.ppm)
        assertEquals(25.5f, reading.temperature)
        assertEquals(55.0f, reading.humidity)
    }
}

