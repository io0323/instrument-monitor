package com.instrument.android.ui.alarm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Phase 5 で実装予定: 多段アラーム設定画面
@Composable
fun AlarmScreen(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Alarm Settings — Phase 5 で実装")
    }
}
