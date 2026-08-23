package com.instrument.presentation.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.domain.model.AlarmCounts
import com.instrument.domain.model.DailyPpmStats
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.LogPeriodStats
import com.instrument.domain.usecase.GetLogStatisticsUseCase
import com.instrument.presentation.ui.theme.GasLevelColors
import com.instrument.presentation.viewmodel.StatsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val weeklyStats by viewModel.weeklyStats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("統計・分析") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val stats = weeklyStats
            if (stats == null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    PeriodSummaryCard(stats = stats)
                }
                item {
                    WeeklyBarChartCard(dailyStats = stats.dailyStats)
                }
                item {
                    AlarmCountsCard(alarmCounts = stats.alarmCounts)
                }
            }
        }
    }
}

/**
 * 週間サマリカード: 集計期間・min/max/avg ppm・総計測回数を表示する。
 */
@Composable
private fun PeriodSummaryCard(stats: LogPeriodStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "過去 ${GetLogStatisticsUseCase.DAYS} 日間のサマリ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (stats.totalReadings == 0) {
                Text(
                    text = "この期間のデータはありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SummaryStatItem(label = "最小 ppm", value = stats.minPpm.toInt().toString())
                    SummaryStatItem(label = "平均 ppm", value = "%.1f".format(stats.avgPpm))
                    SummaryStatItem(label = "最大 ppm", value = stats.maxPpm.toInt().toString())
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "総計測回数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${stats.totalReadings} 件",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 週間日別最大 ppm バーチャート。
 * Canvas でバーを描画し、各バーの色はガスレベルに応じて変化する。
 */
@Composable
private fun WeeklyBarChartCard(dailyStats: List<DailyPpmStats>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "日別最大 ppm (過去 7 日間)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            val maxPpmOverall = dailyStats.maxOfOrNull { it.maxPpm }?.coerceAtLeast(1f) ?: 1f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                val barCount = dailyStats.size
                if (barCount == 0) return@Canvas

                val totalWidth = size.width
                val totalHeight = size.height
                val barGap = 8.dp.toPx()
                val barWidth = (totalWidth - barGap * (barCount - 1)) / barCount
                val maxBarHeight = totalHeight * 0.85f
                // ゼロライン (横線)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(0f, totalHeight),
                    end = Offset(totalWidth, totalHeight),
                    strokeWidth = 1.dp.toPx(),
                )

                dailyStats.forEachIndexed { index, day ->
                    val left = index * (barWidth + barGap)
                    val barHeight = if (maxPpmOverall > 0f) {
                        (day.maxPpm / maxPpmOverall) * maxBarHeight
                    } else 0f

                    val barColor = when {
                        day.maxPpm >= GasLevel.CRITICAL_THRESHOLD -> GasLevelColors[GasLevel.CRITICAL]!!
                        day.maxPpm >= GasLevel.DANGER_THRESHOLD   -> GasLevelColors[GasLevel.DANGER]!!
                        day.maxPpm >= GasLevel.WARNING_THRESHOLD  -> GasLevelColors[GasLevel.WARNING]!!
                        else                                       -> GasLevelColors[GasLevel.SAFE]!!
                    }

                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, totalHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                }
            }

            // 曜日ラベル行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                dailyStats.forEach { day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = day.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (day.maxPpm > 0f) day.maxPpm.toInt().toString() else "-",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * アラームレベル別発生回数カード。
 * WARNING / DANGER / CRITICAL の件数をそれぞれバッジ形式で表示する。
 */
@Composable
private fun AlarmCountsCard(alarmCounts: AlarmCounts) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "アラーム発生回数 (過去 7 日間)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (alarmCounts.total > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "合計 ${alarmCounts.total} 回",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (alarmCounts.total == 0) {
                Text(
                    text = "アラームは発生していません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlarmLevelCountItem(
                        level  = GasLevel.WARNING,
                        count  = alarmCounts.warning,
                        modifier = Modifier.weight(1f),
                    )
                    AlarmLevelCountItem(
                        level  = GasLevel.DANGER,
                        count  = alarmCounts.danger,
                        modifier = Modifier.weight(1f),
                    )
                    AlarmLevelCountItem(
                        level  = GasLevel.CRITICAL,
                        count  = alarmCounts.critical,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmLevelCountItem(level: GasLevel, count: Int, modifier: Modifier = Modifier) {
    val color = GasLevelColors[level] ?: Color.Gray
    Surface(
        modifier = modifier,
        color    = color.copy(alpha = if (count > 0) 0.2f else 0.05f),
        shape    = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = level.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text  = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = "回",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
