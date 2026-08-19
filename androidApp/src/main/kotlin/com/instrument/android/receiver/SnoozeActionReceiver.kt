package com.instrument.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.instrument.android.notification.NotificationHelper
import com.instrument.domain.usecase.AlarmUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 通知アクションボタンからアラームをスヌーズするBroadcastReceiver。
 *
 * アラート通知の「5分/10分/15分スヌーズ」ボタンを押したとき、
 * アプリを開かずに AlarmUseCase.snooze() を呼び出す。
 * AlarmUseCase はシングルトン登録されているため DashboardViewModel と同一インスタンスを参照する。
 */
class SnoozeActionReceiver : BroadcastReceiver(), KoinComponent {

    private val alarmUseCase: AlarmUseCase by inject()

    companion object {
        const val ACTION_SNOOZE_5  = "com.instrument.action.SNOOZE_5MIN"
        const val ACTION_SNOOZE_10 = "com.instrument.action.SNOOZE_10MIN"
        const val ACTION_SNOOZE_15 = "com.instrument.action.SNOOZE_15MIN"

        fun snoozeIntent(context: Context, action: String): Intent =
            Intent(context, SnoozeActionReceiver::class.java).apply { this.action = action }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val durationMs = when (intent.action) {
            ACTION_SNOOZE_5  ->  5 * 60_000L
            ACTION_SNOOZE_10 -> 10 * 60_000L
            ACTION_SNOOZE_15 -> 15 * 60_000L
            else -> return
        }
        alarmUseCase.snooze(durationMs)
        // スヌーズ開始後はアラート通知を閉じてドロワーをすっきりさせる
        NotificationHelper(context).cancelAlertNotification()
    }
}
