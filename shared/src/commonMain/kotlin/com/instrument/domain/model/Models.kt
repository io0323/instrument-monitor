package com.instrument.domain.model

// ガス検知センサー読み取り値
data class SensorReading(
    val ppm:         Float,
    val temperature: Float,
    val humidity:    Float,
    val timestamp:   Long
)

// BLE接続デバイス情報
data class GasDevice(
    val id:          String,   // MACアドレス (Android) / UUID (iOS)
    val name:        String,
    val rssi:        Int,
    val isConnected: Boolean = false
)

// ガス濃度レベル (JIS T 8201準拠イメージ)
// SAFE:     ppm < 50
// WARNING:  50  ≤ ppm < 200
// DANGER:   200 ≤ ppm < 350
// CRITICAL: ppm ≥ 350
enum class GasLevel {
    SAFE, WARNING, DANGER, CRITICAL;

    companion object {
        /** WARNING レベルへ移行する閾値 (ppm) */
        const val WARNING_THRESHOLD: Float  = 50f
        /** DANGER レベルへ移行する閾値 (ppm) */
        const val DANGER_THRESHOLD: Float   = 200f
        /** CRITICAL レベルへ移行する閾値 (ppm) */
        const val CRITICAL_THRESHOLD: Float = 350f

        /** ppm 値から対応する [GasLevel] を返す */
        fun fromPpm(ppm: Float): GasLevel = when {
            ppm < WARNING_THRESHOLD  -> SAFE
            ppm < DANGER_THRESHOLD   -> WARNING
            ppm < CRITICAL_THRESHOLD -> DANGER
            else                     -> CRITICAL
        }
    }
}

// 濃度トレンド
enum class Trend { RISING, STABLE, FALLING }

// UseCase出力: ガス状態 (読み取り値 + レベル + トレンド)
data class GasStatus(
    val reading: SensorReading,
    val level:   GasLevel,
    val trend:   Trend
)

// BLE自動再接続の状態
sealed class ReconnectState {
    // 再接続待機中 (初期状態・接続成功後)
    object Idle : ReconnectState()
    // 再接続試行中
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ReconnectState()
    // 最大試行回数超過による再接続失敗
    object Failed : ReconnectState()
    // 再接続成功
    object Connected : ReconnectState()
}

// GPS位置情報付き計測記録
data class GeoTaggedReading(
    val reading: SensorReading,
    val lat:     Double,
    val lng:     Double,
    val level:   GasLevel,
    val id:      Long = 0
)

// セッション中の統計サマリ
data class SessionStats(
    val minPpm:       Float,
    val maxPpm:       Float,
    val avgPpm:       Float,
    val peakLevel:    GasLevel,
    val readingCount: Int,
)
