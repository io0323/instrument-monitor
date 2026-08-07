package com.instrument.domain.model

// アプリ設定を保持するデータクラス
data class AppSettings(
    // DANGER 以上のガス読み取りを GPS 座標付きで自動保存するかどうか
    val gpsLoggingEnabled: Boolean = true,
    // ログ保持日数。これより古いレコードは削除対象となる
    val retentionDays: Int = 30,
    // 同一レベルアラームを抑制する間隔 (秒)
    val alarmSuppressIntervalSec: Int = 30,
) {
    companion object {
        /** ログ保持日数の選択肢 */
        val RETENTION_OPTIONS: List<Int> = listOf(30, 60, 90)
        /** アラーム再発報間隔の選択肢 (秒) */
        val ALARM_INTERVAL_OPTIONS: List<Int> = listOf(15, 30, 60)
    }
}
