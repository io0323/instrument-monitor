package com.instrument.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.instrument.presentation.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SettingsSectionHeader("データ記録") }
            item { GpsLoggingToggle(enabled = settings.gpsLoggingEnabled, onToggle = viewModel::toggleGpsLogging) }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SettingsSectionHeader("ログ保持期間") }
            item {
                OptionSelector(
                    label = "保持日数",
                    options = AppSettings.RETENTION_OPTIONS,
                    selected = settings.retentionDays,
                    onSelect = viewModel::setRetentionDays,
                    labelOf = { "${it}日" },
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SettingsSectionHeader("アラーム") }
            item {
                OptionSelector(
                    label = "再発報間隔",
                    options = AppSettings.ALARM_INTERVAL_OPTIONS,
                    selected = settings.alarmSuppressIntervalSec,
                    onSelect = viewModel::setAlarmSuppressIntervalSec,
                    labelOf = { "${it}秒" },
                )
            }
            item {
                AlarmToggle(
                    label = "アラーム音",
                    description = "ガス検知時に警告音を鳴らします",
                    enabled = settings.soundEnabled,
                    onToggle = viewModel::toggleSoundEnabled,
                )
            }
            item {
                AlarmToggle(
                    label = "振動",
                    description = "ガス検知時にデバイスを振動させます",
                    enabled = settings.vibrationEnabled,
                    onToggle = viewModel::toggleVibrationEnabled,
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SettingsSectionHeader("アラーム閾値") }
            item {
                ThresholdSlider(
                    label = "WARNING",
                    description = "この値以上で注意アラームを発報します",
                    currentValue = settings.warningThresholdPpm,
                    range = AppSettings.WARNING_THRESHOLD_RANGE,
                    levelColor = GasLevelColors[GasLevel.WARNING] ?: Color.Yellow,
                    onValueChangeFinished = viewModel::setWarningThresholdSafe,
                )
            }
            item {
                ThresholdSlider(
                    label = "DANGER",
                    description = "この値以上で危険アラームを発報します",
                    currentValue = settings.dangerThresholdPpm,
                    range = AppSettings.DANGER_THRESHOLD_RANGE,
                    levelColor = GasLevelColors[GasLevel.DANGER] ?: Color.Red,
                    onValueChangeFinished = viewModel::setDangerThresholdSafe,
                )
            }
            item {
                ThresholdSlider(
                    label = "CRITICAL",
                    description = "この値以上で緊急アラームを発報します",
                    currentValue = settings.criticalThresholdPpm,
                    range = AppSettings.CRITICAL_THRESHOLD_RANGE,
                    levelColor = GasLevelColors[GasLevel.CRITICAL] ?: Color.Red,
                    onValueChangeFinished = viewModel::setCriticalThresholdSafe,
                )
            }
            item {
                TextButton(
                    onClick = viewModel::resetThresholdsToDefault,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("デフォルト値に戻す (50 / 200 / 350 ppm)")
                }
            }
        }
    }
}

// セクション見出し
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    HorizontalDivider()
}

// アラーム音・振動のような汎用 ON/OFF スイッチ
@Composable
private fun AlarmToggle(label: String, description: String, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}

// GPS 自動ロギングの ON/OFF スイッチ
@Composable
private fun GpsLoggingToggle(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("GPS 自動ロギング", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "DANGER 以上の検出時に位置情報と合わせて自動保存します",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}

// アラーム閾値を ppm 単位のスライダーで調整するコンポーネント
@Composable
private fun ThresholdSlider(
    label: String,
    description: String,
    currentValue: Int,
    range: IntRange,
    levelColor: Color,
    onValueChangeFinished: (Int) -> Unit,
) {
    // スライダー操作中はローカル状態で表示し、離したときに ViewModel へ反映する
    var sliderValue by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = levelColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${sliderValue.toInt()} ppm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = levelColor,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 10 - 1,
            colors = SliderDefaults.colors(
                thumbColor = levelColor,
                activeTrackColor = levelColor.copy(alpha = 0.8f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${range.first} ppm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${range.last} ppm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// 固定オプションから一つを選ぶセレクター (SegmentedButton)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSelector(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(labelOf(option)) },
                )
            }
        }
    }
}
