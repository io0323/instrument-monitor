package com.instrument.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.instrument.android.notification.NotificationHelper
import com.instrument.android.service.GasMonitorService
import com.instrument.android.ui.BatteryOptimizationDialog
import com.instrument.domain.repository.SettingsRepository
import com.instrument.presentation.ui.AppNavGraph
import com.instrument.presentation.ui.theme.InstrumentTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()

    // 通知タップなどで渡されるディープリンク先ルート。
    // Compose state として持つことで setContent 内の再コンポーズをトリガーできる
    private var pendingDeepLink by mutableStateOf<String?>(null)

    // Android 13 以上で通知権限をリクエストするランチャー
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startMonitorService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // コールドスタート時の通知 Intent を処理する
        pendingDeepLink = intent.getStringExtra(NotificationHelper.EXTRA_DEEP_LINK)
        // バッテリー最適化が有効かつ警告未確認の場合にダイアログを表示する初期値を決定する
        val initialShowBatteryDialog = shouldShowBatteryOptimizationDialog()
        setContent {
            InstrumentTheme {
                var showBatteryDialog by remember { mutableStateOf(initialShowBatteryDialog) }
                if (showBatteryDialog) {
                    BatteryOptimizationDialog(
                        onOpenSettings = {
                            showBatteryDialog = false
                            dismissBatteryOptimizationWarning()
                            openBatteryOptimizationSettings()
                        },
                        onDismiss = {
                            showBatteryDialog = false
                            dismissBatteryOptimizationWarning()
                        },
                    )
                }
                AppNavGraph(
                    pendingDeepLink    = pendingDeepLink,
                    onDeepLinkConsumed = { pendingDeepLink = null },
                )
            }
        }
        requestNotificationPermissionAndStartService()
    }

    // アプリが前面にある状態で通知をタップしたとき (ホットスタート) に呼ばれる
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.getStringExtra(NotificationHelper.EXTRA_DEEP_LINK)
    }

    override fun onDestroy() {
        // Activity が完全に破棄されるときはサービスも停止する
        stopService(GasMonitorService.stopIntent(this))
        super.onDestroy()
    }

    // Android 13+ は実行時に POST_NOTIFICATIONS 権限が必要
    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startMonitorService()
            } else {
                notificationPermissionLauncher.launch(permission)
            }
        } else {
            startMonitorService()
        }
    }

    private fun startMonitorService() {
        ContextCompat.startForegroundService(this, GasMonitorService.startIntent(this))
    }

    /**
     * バッテリー最適化ダイアログを表示すべきか判定する。
     * 以下の条件がすべて真のときに true を返す:
     * - ユーザーがまだ警告を確認・却下していない
     * - システムがこのアプリのバッテリー最適化を除外していない
     */
    private fun shouldShowBatteryOptimizationDialog(): Boolean {
        val alreadyDismissed =
            settingsRepository.settings.value.batteryOptimizationWarningDismissed
        if (alreadyDismissed) return false
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    /** ダイアログを「後で」または「設定を開く」で閉じた後、次回以降表示しないよう記録する */
    private fun dismissBatteryOptimizationWarning() {
        val current = settingsRepository.settings.value
        settingsRepository.update(current.copy(batteryOptimizationWarningDismissed = true))
    }

    /** バッテリー設定画面を開く。ユーザーがこのアプリを除外リストに追加できる */
    private fun openBatteryOptimizationSettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
