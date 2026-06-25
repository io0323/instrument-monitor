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
enum class GasLevel { SAFE, WARNING, DANGER, CRITICAL }

// 濃度トレンド
enum class Trend { RISING, STABLE, FALLING }

// UseCase出力: ガス状態 (読み取り値 + レベル + トレンド)
data class GasStatus(
    val reading: SensorReading,
    val level:   GasLevel,
    val trend:   Trend
)

// GPS位置情報付き計測記録
data class GeoTaggedReading(
    val reading: SensorReading,
    val lat:     Double,
    val lng:     Double,
    val level:   GasLevel,
    val id:      Long = 0
)
