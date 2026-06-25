package com.instrument.android.ui.devicelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Phase 2 で実装予定: BLEデバイス一覧・接続画面
@Composable
fun DeviceListScreen(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Device List — Phase 2 で実装")
    }
}
