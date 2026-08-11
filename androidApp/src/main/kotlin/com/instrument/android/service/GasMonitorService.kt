package com.instrument.android.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.instrument.android.notification.NotificationHelper
import com.instrument.data.notification.shouldCancelAlert
import com.instrument.data.notification.shouldFireAlert
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.usecase.MonitorGasUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * バックグラウンドでガス濃度を監視するフォアグラウンドサービス。
 *
 * - ACTION_START: フォアグラウンド昇格 + BLEセンサーデータ購読を開始する
 * - ACTION_STOP : サービスを停止して通知を消去する
 *
 * BleRepository はシングルトンとして Koin に登録されているため、
 * DashboardViewModel と同一インスタンスを共有し BLE 二重接続を防ぐ。
 */
class GasMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.instrument.action.START_MONITORING"
        const val ACTION_STOP  = "com.instrument.action.STOP_MONITORING"

        fun startIntent(context: Context): Intent =
            Intent(context, GasMonitorService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, GasMonitorService::class.java).apply { action = ACTION_STOP }
    }

    private val monitorGasUseCase: MonitorGasUseCase by inject()
    private val notificationHelper: NotificationHelper by lazy { NotificationHelper(this) }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null

    // 最後に発火したアラートレベル。同一レベルへの連続通知を抑制するために保持する
    private var lastAlertLevel: GasLevel? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP  -> stopSelf()
        }
        // サービスがシステムにより強制終了された場合、同じ Intent で再起動する
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        notificationHelper.cancelAlertNotification()
        super.onDestroy()
    }

    // フォアグラウンドへ昇格してセンサーデータの購読を開始する
    private fun startMonitoring() {
        startForeground(
            NotificationHelper.NOTIFICATION_ID_FOREGROUND,
            notificationHelper.buildForegroundNotification(),
        )

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            monitorGasUseCase()
                .catch {
                    // BLE未接続や切断など正常系の例外はサービスを落とさず無視する
                }
                .collect { status ->
                    updateForegroundNotification(status)
                    handleAlertNotification(status)
                }
        }
    }

    // 常駐通知のテキストを最新の計測値へ更新する
    private fun updateForegroundNotification(status: GasStatus) {
        val notification = notificationHelper.buildForegroundNotification(
            ppm   = status.reading.ppm,
            level = status.level,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
    }

    // DANGER以上を検知したときにアラート通知を発火し、回復時に消去する
    private fun handleAlertNotification(status: GasStatus) {
        when {
            shouldFireAlert(status.level, lastAlertLevel) -> {
                notificationHelper.showAlertNotification(status.reading.ppm, status.level)
                lastAlertLevel = status.level
            }
            shouldCancelAlert(status.level, lastAlertLevel) -> {
                notificationHelper.cancelAlertNotification()
                lastAlertLevel = null
            }
        }
    }
}
