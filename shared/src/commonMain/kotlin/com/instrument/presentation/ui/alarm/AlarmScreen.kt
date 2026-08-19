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
import com.instrument.domain.model.AppSettings
import com.instrument.domain.model.GasLevel
import com.instrument.presentation.ui.theme.GasLevelColors
import com.instrument.presentation.viewmodel.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(onNavigateBack: () -> Unit) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
                    level             = uiState.gasStatus?.level ?: GasLevel.SAFE,
                    ppm               = uiState.gasStatus?.reading?.ppm ?: 0f,
                    isAlarmActive     = uiState.isAlarmActive,
                    isSnoozed         = uiState.isSnoozed,
                    snoozeRemainingMs = uiState.snoozeRemainingMs,
                    onDismiss         = { viewModel.dismissAlarm() },
                    onSnooze          = { minutes -> viewModel.snoozeAlarm(minutes) },
                    onCancelSnooze    = { viewModel.cancelSnooze() },
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

            // 閾値一覧 (AppSettings から動的に生成する)
            items(buildAlarmThresholds(settings)) { threshold ->
                AlarmThresholdRow(threshold)
            }
        }
    }
}

// ───── 現在のアラーム状態カード ─────

@Composable
private fun AlarmStatusCard(
    level             : GasLevel,
    ppm               : Float,
    isAlarmActive     : Boolean,
    isSnoozed         : Boolean,
    snoozeRemainingMs : Long,
    onDismiss         : () -> Unit,
    onSnooze          : (Int) -> Unit,
    onCancelSnooze    : () -> Unit,
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
            when {
                isSnoozed -> {
                    // スヌーズ中: 残り時間と解除ボタンを表示する
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = "スヌーズ中 — 残り ${formatSnoozeRemaining(snoozeRemainingMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onCancelSnooze) {
                        Text("スヌーズ解除")
                    }
                }
                isAlarmActive -> {
                    // アラーム発報中: 消音ボタンとスヌーズ選択ボタンを表示する
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onDismiss,
                        colors  = ButtonDefaults.buttonColors(containerColor = levelColor),
                    ) {
                        Text("アラーム消音", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text  = "スヌーズ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { minutes ->
                            OutlinedButton(onClick = { onSnooze(minutes) }) {
                                Text("${minutes}分")
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text  = "アラームなし",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** スヌーズ残り時間を「MM分SS秒」形式でフォーマットする */
private fun formatSnoozeRemaining(remainingMs: Long): String {
    val totalSec = remainingMs / 1_000L
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}分${sec.toString().padStart(2, '0')}秒"
}

// ───── 閾値定義 ─────

internal data class AlarmThreshold(
    val level     : GasLevel,
    val range     : String,
    val vibration : String,
    val sound     : String,
)

/**
 * AppSettings のカスタム閾値とアラーム設定から閾値表示リストを生成する。
 * 音声・振動が無効の場合はその旨をラベルに反映する。
 */
internal fun buildAlarmThresholds(settings: AppSettings): List<AlarmThreshold> {
    val soundSuffix     = if (settings.soundEnabled)     "" else " (OFF)"
    val vibSuffix       = if (settings.vibrationEnabled) "" else " (OFF)"
    return listOf(
        AlarmThreshold(
            level     = GasLevel.WARNING,
            range     = "${settings.warningThresholdPpm} ppm〜",
            vibration = "単発100ms$vibSuffix",
            sound     = "短い警告音$soundSuffix",
        ),
        AlarmThreshold(
            level     = GasLevel.DANGER,
            range     = "${settings.dangerThresholdPpm} ppm〜",
            vibration = "3回パターン$vibSuffix",
            sound     = "警告音$soundSuffix",
        ),
        AlarmThreshold(
            level     = GasLevel.CRITICAL,
            range     = "${settings.criticalThresholdPpm} ppm〜",
            vibration = "連続$vibSuffix",
            sound     = "緊急アラーム (ループ)$soundSuffix",
        ),
    )
}

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
