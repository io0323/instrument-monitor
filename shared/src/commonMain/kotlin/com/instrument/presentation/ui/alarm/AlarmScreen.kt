package com.instrument.presentation.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.domain.model.GasLevel
import com.instrument.presentation.ui.theme.GasLevelColors
import com.instrument.presentation.viewmodel.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(onNavigateBack: () -> Unit) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アラーム状態") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("←") }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding      = PaddingValues(vertical = 16.dp),
        ) {
            // 現在のアラーム状態カード
            item {
                AlarmStatusCard(
                    level         = uiState.gasStatus?.level ?: GasLevel.SAFE,
                    ppm           = uiState.gasStatus?.reading?.ppm ?: 0f,
                    isAlarmActive = uiState.isAlarmActive,
                    onDismiss     = { viewModel.dismissAlarm() },
                )
            }

            // セクションヘッダー
            item {
                Text(
                    text       = "アラーム閾値",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(top = 8.dp),
                )
            }

            // 閾値一覧
            items(alarmThresholds) { threshold ->
                AlarmThresholdRow(threshold)
            }
        }
    }
}

// ───── 現在のアラーム状態カード ─────

@Composable
private fun AlarmStatusCard(
    level         : GasLevel,
    ppm           : Float,
    isAlarmActive : Boolean,
    onDismiss     : () -> Unit,
) {
    val levelColor = GasLevelColors[level] ?: Color.Green
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = levelColor.copy(alpha = 0.15f),
        ),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text       = levelLabel(level),
                style      = MaterialTheme.typography.headlineMedium,
                color      = levelColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = "${ppm.toInt()} ppm",
                style = MaterialTheme.typography.displaySmall,
                color = levelColor,
            )
            if (isAlarmActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    colors  = ButtonDefaults.buttonColors(containerColor = levelColor),
                ) {
                    Text("アラーム消音", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text  = "アラームなし",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ───── 閾値定義 ─────

private data class AlarmThreshold(
    val level     : GasLevel,
    val range     : String,
    val vibration : String,
    val sound     : String,
)

private val alarmThresholds = listOf(
    AlarmThreshold(GasLevel.WARNING,  "50 ppm〜",  "単発100ms",   "短い警告音"),
    AlarmThreshold(GasLevel.DANGER,   "200 ppm〜", "3回パターン", "警告音"),
    AlarmThreshold(GasLevel.CRITICAL, "350 ppm〜", "連続",        "緊急アラーム (ループ)"),
)

// ───── 閾値行コンポーネント ─────

@Composable
private fun AlarmThresholdRow(threshold: AlarmThreshold) {
    val color = GasLevelColors[threshold.level] ?: Color.Gray
    Column {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // レベルカラーバー
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .background(color),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "${threshold.level.name}  ${threshold.range}",
                    style      = MaterialTheme.typography.bodyLarge,
                    color      = color,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text  = "振動: ${threshold.vibration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = "音声: ${threshold.sound}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    }
}

// ───── ヘルパー ─────

private fun levelLabel(level: GasLevel): String = when (level) {
    GasLevel.SAFE     -> "✅ 安全"
    GasLevel.WARNING  -> "⚠️ 注意"
    GasLevel.DANGER   -> "🔶 危険"
    GasLevel.CRITICAL -> "🚨 緊急"
}
