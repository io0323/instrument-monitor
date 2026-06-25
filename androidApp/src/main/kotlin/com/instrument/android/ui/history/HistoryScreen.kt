package com.instrument.android.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Phase 7 で実装予定: 計測履歴・CSV書き出し画面
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "History — Phase 7 で実装")
    }
}
