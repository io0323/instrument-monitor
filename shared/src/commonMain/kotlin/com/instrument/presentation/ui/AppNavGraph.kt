package com.instrument.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.instrument.presentation.ui.alarm.AlarmScreen
import com.instrument.presentation.ui.dashboard.DashboardScreen
import com.instrument.presentation.ui.devicelist.DeviceListScreen
import com.instrument.presentation.ui.history.HistoryScreen
import com.instrument.presentation.ui.settings.SettingsScreen
import com.instrument.presentation.viewmodel.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

object Routes {
    const val DASHBOARD   = "dashboard"
    const val DEVICE_LIST = "deviceList"
    const val ALARM       = "alarm"
    const val HISTORY     = "history"
    const val SETTINGS    = "settings"
}

/** デバイス選択画面 → ダッシュボード間でデバイス名を渡すための savedStateHandle キー */
private const val KEY_CONNECTED_DEVICE_NAME = "connected_device_name"

/**
 * アプリ全体のナビゲーショングラフ。
 *
 * @param pendingDeepLink    通知タップなどで指定された遷移先ルート。null なら通常のスタート画面へ。
 * @param onDeepLinkConsumed ディープリンクを処理した後に呼び出すコールバック。呼び出し元で状態をリセットする。
 */
@Composable
fun AppNavGraph(
    pendingDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // コールドスタート時は最初の pendingDeepLink を startDestination として適用する
    val startDestination = remember { pendingDeepLink ?: Routes.DASHBOARD }

    // ホットスタート (onNewIntent) など、グラフ構築後にディープリンクが来た場合に遷移する
    val currentRoute by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingDeepLink) {
        pendingDeepLink?.let { route ->
            if (currentRoute?.destination?.route != route) {
                navController.navigate(route) { launchSingleTop = true }
            }
            onDeepLinkConsumed()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.DASHBOARD) { entry ->
            val vm: DashboardViewModel = koinViewModel()

            // デバイス選択画面から戻った際に savedStateHandle 経由でデバイス名を受け取る
            val deviceNameFlow = entry.savedStateHandle
                .getStateFlow<String?>(KEY_CONNECTED_DEVICE_NAME, null)
            val connectedDeviceName by deviceNameFlow.collectAsStateWithLifecycle()
            LaunchedEffect(connectedDeviceName) {
                connectedDeviceName?.let { name ->
                    vm.onDeviceConnected(name)
                    entry.savedStateHandle.remove<String>(KEY_CONNECTED_DEVICE_NAME)
                }
            }

            DashboardScreen(
                viewModel              = vm,
                onNavigateToDeviceList = { navController.navigate(Routes.DEVICE_LIST) },
                onNavigateToHistory    = { navController.navigate(Routes.HISTORY) },
                onNavigateToAlarm      = { navController.navigate(Routes.ALARM) },
                onNavigateToSettings   = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.DEVICE_LIST) {
            DeviceListScreen(
                onNavigateBack = { deviceName ->
                    // 接続完了時のみデバイス名を前画面 (Dashboard) の savedStateHandle に渡す
                    deviceName?.let { name ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(KEY_CONNECTED_DEVICE_NAME, name)
                    }
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.ALARM) {
            AlarmScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
