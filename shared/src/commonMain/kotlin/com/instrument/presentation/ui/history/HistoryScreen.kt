package com.instrument.presentation.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.presentation.components.InstrumentMap
import com.instrument.presentation.components.rememberCsvSaver
import com.instrument.presentation.ui.theme.GasLevelColors
import com.instrument.presentation.viewmodel.DateFilter
import com.instrument.presentation.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val viewModel: HistoryViewModel = koinViewModel()
    val readings    by viewModel.readings.collectAsStateWithLifecycle()
    val dateFilter  by viewModel.dateFilter.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val csvSaver = rememberCsvSaver()

    // エクスポート完了 / エラーを Snackbar で通知する
    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is HistoryViewModel.ExportState.Done  -> {
                scope.launch { snackbarHostState.showSnackbar("CSV 保存完了: ${s.filePath}") }
                viewModel.clearExportState()
            }
            is HistoryViewModel.ExportState.Error -> {
                scope.launch { snackbarHostState.showSnackbar("エクスポート失敗: ${s.msg}") }
                viewModel.clearExportState()
            }
            else -> Unit
        }
    }

    // 削除完了 / エラーを Snackbar で通知する
    LaunchedEffect(deleteState) {
        when (val s = deleteState) {
            is HistoryViewModel.DeleteState.Done  -> {
                scope.launch { snackbarHostState.showSnackbar("30日以上前のログを削除しました") }
                viewModel.clearDeleteState()
            }
            is HistoryViewModel.DeleteState.Error -> {
                scope.launch { snackbarHostState.showSnackbar("削除失敗: ${s.msg}") }
                viewModel.clearDeleteState()
            }
            else -> Unit
        }
    }

    // 削除確認ダイアログ表示フラグ
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("古いログを削除") },
            text    = { Text("30日以上前の計測記録を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteOldLogs()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            },
        )
    }

    val isExporting = exportState is HistoryViewModel.ExportState.Exporting
    val isDeleting  = deleteState is HistoryViewModel.DeleteState.Deleting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("計測履歴") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("←") }
                },
                actions = {
                    // CSV エクスポートボタン
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = { viewModel.exportCsv { csv -> csvSaver.save(csv) } },
                            enabled = readings.isNotEmpty(),
                        ) { Text("CSV") }
                    }
                    // 古いログ削除ボタン
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = { showDeleteConfirm = true }) { Text("削除") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 日付フィルターチップ行
            DateFilterRow(
                selected = dateFilter,
                onSelect = { viewModel.setDateFilter(it) },
            )
            HorizontalDivider()
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("リスト") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("マップ") })
            }
            when (selectedTab) {
                0 -> ReadingsList(readings = readings)
                1 -> InstrumentMap(readings = readings, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterRow(
    selected: DateFilter,
    onSelect: (DateFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick  = { onSelect(filter) },
                label    = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun ReadingsList(readings: List<GeoTaggedReading>) {
    if (readings.isEmpty()) {
        EmptyHistoryState()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(readings) { r ->
            val color = GasLevelColors[r.level] ?: Color.Gray
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(4.dp).height(40.dp).background(color))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatTimestamp(r.reading.timestamp), style = MaterialTheme.typography.labelSmall)
                    Text("${r.reading.ppm.toInt()} ppm", style = MaterialTheme.typography.bodyMedium)
                }
                Text(r.level.name, color = color, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${"%.4f".format(r.lat)},${"%.4f".format(r.lng)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "記録なし",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "選択した期間に計測データがありません",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatTimestamp(timestamp: Long): String {
    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val mm  = dt.monthNumber.toString().padStart(2, '0')
    val dd  = dt.dayOfMonth.toString().padStart(2, '0')
    val hh  = dt.hour.toString().padStart(2, '0')
    val min = dt.minute.toString().padStart(2, '0')
    return "$mm/$dd $hh:$min"
}

// GPS 座標を "lat,lng" 形式（小数点以下4桁）の文字列に変換する純粋関数
internal fun formatCoordinate(lat: Double, lng: Double): String =
    "${"%.4f".format(lat)},${"%.4f".format(lng)}"
