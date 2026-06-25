package com.instrument.android.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Phase 4 で実装予定: リアルタイムガス濃度モニター画面
@Composable
fun DashboardScreen(
    onNavigateToDeviceList: () -> Unit,
    onNavigateToHistory:    () -> Unit,
    onNavigateToAlarm:      () -> Unit
) {
    Box(
        modifier        = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Dashboard — Phase 4 で実装")
    }
}
