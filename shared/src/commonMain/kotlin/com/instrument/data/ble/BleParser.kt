package com.instrument.data.ble

import com.instrument.domain.model.SensorReading
import kotlinx.datetime.Clock

fun ByteArray.toSensorReading(): SensorReading {
    val ppm         = ((this[0].toInt() and 0xFF) shl 8 or (this[1].toInt() and 0xFF)).toFloat()
    val temperature = ((this[2].toInt() and 0xFF) shl 8 or (this[3].toInt() and 0xFF)) / 10f
    val humidity    = ((this[4].toInt() and 0xFF) shl 8 or (this[5].toInt() and 0xFF)) / 10f
    return SensorReading(ppm, temperature, humidity, Clock.System.now().toEpochMilliseconds())
}
