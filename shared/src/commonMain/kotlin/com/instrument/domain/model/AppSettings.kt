package com.instrument.domain.model

// アプリ設定を保持するデータクラス
data class AppSettings(
    // DANGER 以上のガス読み取りを GPS 座標付きで自動保存するかどうか
    val gpsLoggingEnabled: Boolean = true,
    // ログ保持日数。これより古いレコードは削除対象となる
    val retentionDays: Int = 30,
    // 同一レベルアラームを抑制する間隔 (秒)
    val alarmSuppressIntervalSec: Int = 30,
    // アラーム音を鳴らすかどうか
    val soundEnabled: Boolean = true,
    // アラーム振動を有効にするかどうか
    val vibrationEnabled: Boolean = true,
    // WARNING レベルへ移行する閾値 (ppm)。[WARNING_THRESHOLD_RANGE] 内の値のみ有効
    val warningThresholdPpm: Int = 50,
    // DANGER レベルへ移行する閾値 (ppm)。[DANGER_THRESHOLD_RANGE] 内の値のみ有効
    val dangerThresholdPpm: Int = 200,
    // CRITICAL レベルへ移行する閾値 (ppm)。[CRITICAL_THRESHOLD_RANGE] 内の値のみ有効
    val criticalThresholdPpm: Int = 350,
    // 最後に接続成功したデバイスの ID (Android: MAC アドレス / iOS: UUID)。null は未接続
    val lastConnectedDeviceId: String? = null,
    // 最後に接続成功したデバイスの表示名。null は未取得
    val lastConnectedDeviceName: String? = null,
) {
    companion object {
        /** ログ保持日数の選択肢 */
        val RETENTION_OPTIONS: List<Int> = listOf(30, 60, 90)
        /** アラーム再発報間隔の選択肢 (秒) */
        val ALARM_INTERVAL_OPTIONS: List<Int> = listOf(15, 30, 60)

        /** WARNING 閾値の設定可能範囲 (ppm) */
        val WARNING_THRESHOLD_RANGE: IntRange = 10..150
        /** DANGER 閾値の設定可能範囲 (ppm) */
        val DANGER_THRESHOLD_RANGE: IntRange = 30..400
        /** CRITICAL 閾値の設定可能範囲 (ppm) */
        val CRITICAL_THRESHOLD_RANGE: IntRange = 50..500
    }
}
