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
            val reading = if (++criticalCounter >= CRITICAL_INTERVAL) {
                criticalCounter = 0
                SensorReading(CRITICAL_PPM, BASE_TEMP.toFloat(), BASE_HUMIDITY.toFloat(), Clock.System.now().toEpochMilliseconds())
            } else {
                val ppm  = ((sin(t) * PPM_AMPLITUDE + PPM_CENTER).toFloat() + (-PPM_NOISE..PPM_NOISE).random()).coerceAtLeast(0f)
                val temp = (BASE_TEMP + sin(t * TEMP_FREQ) * TEMP_AMPLITUDE).toFloat()
                val hum  = (BASE_HUMIDITY + cos(t * HUM_FREQ) * HUM_AMPLITUDE).toFloat()
                SensorReading(ppm, temp, hum, Clock.System.now().toEpochMilliseconds())
            }
            emit(reading)
            t += T_INCREMENT
            delay(EMIT_DELAY_MS)
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

    companion object {
        /** CRITICAL 読み取り値を挿入する間隔（回数）: 20回 × 500ms = 10秒ごと */
        const val CRITICAL_INTERVAL = 20
        /** センサーデータ送出間隔 (ミリ秒) */
        const val EMIT_DELAY_MS     = 500L
        /** CRITICAL 時に使用する固定 ppm 値 */
        const val CRITICAL_PPM      = 380f
        /** ppm サイン波の振幅 */
        const val PPM_AMPLITUDE     = 150.0
        /** ppm サイン波の中心値 */
        const val PPM_CENTER        = 150.0
        /** ppm へ加えるランダムノイズの範囲 */
        const val PPM_NOISE         = 20
        /** t の増分（サイン波の周波数に影響） */
        const val T_INCREMENT       = 0.1
        /** 気温の基準値 (℃) */
        const val BASE_TEMP         = 20.0
        /** 気温サイン波の周波数係数 */
        const val TEMP_FREQ         = 0.1
        /** 気温サイン波の振幅 (℃) */
        const val TEMP_AMPLITUDE    = 5.0
        /** 湿度の基準値 (%) */
        const val BASE_HUMIDITY     = 55.0
        /** 湿度コサイン波の周波数係数 */
        const val HUM_FREQ          = 0.07
        /** 湿度コサイン波の振幅 (%) */
        const val HUM_AMPLITUDE     = 15.0
    }
}
