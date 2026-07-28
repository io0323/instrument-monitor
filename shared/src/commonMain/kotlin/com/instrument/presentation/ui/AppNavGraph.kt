package com.instrument.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.instrument.presentation.ui.alarm.AlarmScreen
import com.instrument.presentation.ui.dashboard.DashboardScreen
import com.instrument.presentation.ui.devicelist.DeviceListScreen
import com.instrument.presentation.ui.history.HistoryScreen
import com.instrument.presentation.viewmodel.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

object Routes {
    const val DASHBOARD   = "dashboard"
    const val DEVICE_LIST = "deviceList"
    const val ALARM       = "alarm"
    const val HISTORY     = "history"
}

/** デバイス選択画面 → ダッシュボード間でデバイス名を渡すための savedStateHandle キー */
private const val KEY_CONNECTED_DEVICE_NAME = "connected_device_name"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
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
    }
}
