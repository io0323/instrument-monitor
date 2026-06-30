package com.instrument.presentation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.instrument.presentation.ui.alarm.AlarmScreen
import com.instrument.presentation.ui.dashboard.DashboardScreen
import com.instrument.presentation.ui.devicelist.DeviceListScreen
import com.instrument.presentation.ui.history.HistoryScreen

object Routes {
    const val DASHBOARD   = "dashboard"
    const val DEVICE_LIST = "deviceList"
    const val ALARM       = "alarm"
    const val HISTORY     = "history"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToDeviceList = { navController.navigate(Routes.DEVICE_LIST) },
                onNavigateToHistory    = { navController.navigate(Routes.HISTORY) },
                onNavigateToAlarm      = { navController.navigate(Routes.ALARM) },
            )
        }
        composable(Routes.DEVICE_LIST) {
            DeviceListScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.ALARM) {
            AlarmScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
