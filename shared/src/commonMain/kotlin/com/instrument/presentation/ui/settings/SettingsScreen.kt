package com.instrument.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.domain.model.AppSettings
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
