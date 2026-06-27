package com.instrument.data.mock

import com.instrument.domain.model.GasDevice
import com.instrument.domain.model.SensorReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Clock
import kotlin.math.cos
import kotlin.math.sin

class MockBleSource {

    fun observeSensorData(): Flow<SensorReading> = flow {
        var t = 0.0
        var criticalCounter = 0
        while (true) {
            val reading = if (++criticalCounter >= 20) {
                criticalCounter = 0
                SensorReading(380f, 25.0f, 55.0f, Clock.System.now().toEpochMilliseconds())
            } else {
                val ppm  = ((sin(t) * 150 + 150).toFloat() + (-20..20).random()).coerceAtLeast(0f)
                val temp = (20.0 + sin(t * 0.1) * 5).toFloat()
                val hum  = (55.0 + cos(t * 0.07) * 15).toFloat()
                SensorReading(ppm, temp, hum, Clock.System.now().toEpochMilliseconds())
            }
            emit(reading)
            t += 0.1
            delay(500)
        }
    }.flowOn(Dispatchers.Default)

    fun scanDevices(): Flow<List<GasDevice>> = flow {
        emit(
            listOf(
                GasDevice(id = "AA:BB:CC:DD:EE:01", name = "instrument-G100", rssi = -62),
                GasDevice(id = "AA:BB:CC:DD:EE:02", name = "instrument-G200", rssi = -78),
                GasDevice(id = "AA:BB:CC:DD:EE:03", name = "instrument-G300", rssi = -85),
            )
        )
    }
}
