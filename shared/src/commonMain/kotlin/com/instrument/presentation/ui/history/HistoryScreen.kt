package com.instrument.presentation.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.presentation.components.InstrumentMap
import com.instrument.presentation.ui.theme.GasLevelColors
import com.instrument.presentation.viewmodel.HistoryViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val viewModel: HistoryViewModel = koinViewModel()
    val readings by viewModel.readings.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("計測履歴") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("←") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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

@Composable
private fun ReadingsList(readings: List<GeoTaggedReading>) {
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

private fun formatTimestamp(timestamp: Long): String {
    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val mm  = dt.monthNumber.toString().padStart(2, '0')
    val dd  = dt.dayOfMonth.toString().padStart(2, '0')
    val hh  = dt.hour.toString().padStart(2, '0')
    val min = dt.minute.toString().padStart(2, '0')
    return "$mm/$dd $hh:$min"
}
