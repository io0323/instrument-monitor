package com.instrument.data.ble

import com.instrument.domain.model.SensorReading
import kotlinx.datetime.Clock

// BLEパケット (6バイト固定) → SensorReading への変換
// clock を DI することでテスト時にタイムスタンプを固定できる
fun ByteArray.toSensorReading(clock: Clock = Clock.System): SensorReading {
    // パケット形式: [ppm_hi, ppm_lo, temp_x10_hi, temp_x10_lo, hum_x10_hi, hum_x10_lo] の6バイト固定
    require(size >= 6) { "BLEパケットは6バイト以上必要です (actual: $size)" }
    val ppm         = ((this[0].toInt() and 0xFF) shl 8 or (this[1].toInt() and 0xFF)).toFloat()
    val temperature = ((this[2].toInt() and 0xFF) shl 8 or (this[3].toInt() and 0xFF)) / 10f
    val humidity    = ((this[4].toInt() and 0xFF) shl 8 or (this[5].toInt() and 0xFF)) / 10f
    return SensorReading(ppm, temperature, humidity, clock.now().toEpochMilliseconds())
}
