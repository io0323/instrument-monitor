package com.instrument.android.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.instrument.android.MainActivity
import com.instrument.domain.model.GasLevel

// バックグラウンド通知の構築・チャンネル管理を担当するヘルパー
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_MONITORING     = "gas_monitoring"
        const val CHANNEL_ID_ALERT          = "gas_alert"
        const val NOTIFICATION_ID_FOREGROUND = 100
        const val NOTIFICATION_ID_ALERT      = 101
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // 通知チャンネルを登録する (Android 8.0以降必須、重複登録は無害)
    fun createChannels() {
        // 監視チャンネル: 低重要度の常駐通知 (ドロワーに静かに表示)
        NotificationChannel(
            CHANNEL_ID_MONITORING,
            "ガス監視",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "BLEデバイスに接続中、現在のガス濃度を常時表示します"
            notificationManager.createNotificationChannel(this)
        }

        // アラートチャンネル: 高重要度のヘッズアップ通知 (DANGER以上で発火)
        NotificationChannel(
            CHANNEL_ID_ALERT,
            "ガスアラート",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "DANGER以上のガス濃度を検知したときに警告します"
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    // フォアグラウンド常駐通知を構築する
    // ppm / level が null の場合は「接続中...」の初期テキストを表示する
    fun buildForegroundNotification(ppm: Float? = null, level: GasLevel? = null): Notification {
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = if (ppm != null && level != null) {
            "${level.toJapanese()}: ${ppm.toInt()} ppm"
        } else {
            "デバイスへ接続中..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_MONITORING)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Instrument Monitor 監視中")
            .setContentText(contentText)
            .setOngoing(true)            // ユーザーによるスワイプ削除を防ぐ
            .setContentIntent(tapIntent)
            .build()
    }

    // DANGER / CRITICAL 検知時のヘッズアップ通知を発火する
    fun showAlertNotification(ppm: Float, level: GasLevel) {
        val tapIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ${level.toJapanese()} 検知")
            .setContentText("ガス濃度が ${ppm.toInt()} ppm に達しました — タップで確認")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    // アラート通知を非表示にする (DANGER未満へ回復したとき)
    fun cancelAlertNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ALERT)
    }
}

// GasLevel を日本語表示名に変換する内部拡張関数
internal fun GasLevel.toJapanese(): String = when (this) {
    GasLevel.SAFE     -> "安全"
    GasLevel.WARNING  -> "注意"
    GasLevel.DANGER   -> "危険"
    GasLevel.CRITICAL -> "緊急"
}
