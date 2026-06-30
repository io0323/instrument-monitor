package com.instrument.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.instrument.domain.model.GasLevel

val GasLevelColors = mapOf(
    GasLevel.SAFE     to Color(0xFF4CAF50),
    GasLevel.WARNING  to Color(0xFFFFC107),
    GasLevel.DANGER   to Color(0xFFFF5722),
    GasLevel.CRITICAL to Color(0xFFF44336),
)

@Composable
fun InstrumentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
