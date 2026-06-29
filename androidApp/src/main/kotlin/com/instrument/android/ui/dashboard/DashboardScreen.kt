package com.instrument.android.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.android.ui.theme.GasLevelColors
import com.instrument.domain.model.*
import com.instrument.domain.repository.BleConnectionState
import com.instrument.presentation.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.*

@Composable
fun DashboardScreen(
    onNavigateToDeviceList: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAlarm: () -> Unit,
) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.recentHistory.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionBar(
                    deviceName = if (uiState.connectionState == BleConnectionState.Connected) "Mock Device" else null,
                    state = uiState.connectionState,
                    onScanClick = onNavigateToDeviceList,
                )
            }
            item {
                val status = uiState.gasStatus
                GasGauge(
                    ppm = status?.reading?.ppm ?: 0f,
                    level = status?.level ?: GasLevel.SAFE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }
            item {
                val reading = uiState.gasStatus?.reading
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SensorCard("温度", "${reading?.temperature?.let { "%.1f".format(it) } ?: "--"}°C", Modifier.weight(1f))
                    SensorCard("湿度", "${reading?.humidity?.let { "%.1f".format(it) } ?: "--"}%", Modifier.weight(1f))
                }
            }
            item {
                RealtimeChart(history = history, modifier = Modifier.fillMaxWidth().height(140.dp))
            }
            item {
                TrendIndicator(status = uiState.gasStatus, history = history)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNavigateToHistory, modifier = Modifier.weight(1f)) { Text("履歴") }
                    OutlinedButton(onClick = onNavigateToAlarm, modifier = Modifier.weight(1f)) { Text("アラーム") }
                }
            }
        }

        if (uiState.isAlarmActive && uiState.alarmLevel == GasLevel.CRITICAL) {
            AlarmOverlay(
                level = uiState.alarmLevel ?: GasLevel.CRITICAL,
                ppm = uiState.gasStatus?.reading?.ppm ?: 0f,
                onDismiss = { viewModel.dismissAlarm() },
            )
        }
    }
}

@Composable
fun ConnectionBar(deviceName: String?, state: BleConnectionState, onScanClick: () -> Unit) {
    val connected  = state == BleConnectionState.Connected
    val connecting = state == BleConnectionState.Connecting || state == BleConnectionState.Scanning
    val color = when {
        connected  -> Color(0xFF4CAF50)
        connecting -> Color(0xFFFFC107)
        else       -> Color(0xFF9E9E9E)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (connecting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
        } else {
            Canvas(modifier = Modifier.size(12.dp)) { drawCircle(color) }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when {
                connected  -> deviceName ?: "接続済み"
                connecting -> "接続中..."
                state is BleConnectionState.Error -> state.message
                else       -> "未接続"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (!connected) {
            TextButton(onClick = onScanClick) { Text("スキャン") }
        }
    }
}

@Composable
fun GasGauge(ppm: Float, level: GasLevel, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f, label = "blink",
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
    )
    val animatedAngle by animateFloatAsState(
        targetValue = ppmToAngle(ppm.coerceIn(0f, 500f)),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "needle",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height * 0.65f
            val radius = minOf(cx, cy) * 0.85f
            val startAngle = 200f
            val sweepTotal = 140f
            val maxPpm = 500f

            fun ppmToSweep(ppmVal: Float) = (ppmVal / maxPpm) * sweepTotal

            // 背景アーク（レベル帯）
            val bands = listOf(
                GasLevel.SAFE     to (0f to 50f),
                GasLevel.WARNING  to (50f to 200f),
                GasLevel.DANGER   to (200f to 350f),
                GasLevel.CRITICAL to (350f to 500f),
            )
            bands.forEach { (lvl, range) ->
                val sweepStart = startAngle + ppmToSweep(range.first)
                val sweepEnd   = ppmToSweep(range.second - range.first)
                drawArc(
                    color = GasLevelColors[lvl]!!.copy(alpha = 0.3f),
                    startAngle = sweepStart, sweepAngle = sweepEnd,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt),
                )
            }

            // アクティブアーク
            drawArc(
                color = (GasLevelColors[level] ?: Color.Green).copy(
                    alpha = if (level == GasLevel.CRITICAL) blinkAlpha else 1f
                ),
                startAngle = startAngle, sweepAngle = ppmToSweep(ppm.coerceIn(0f, maxPpm)),
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt),
            )

            // 針
            val angleRad = Math.toRadians((startAngle + animatedAngle).toDouble())
            val needleLen = radius * 0.75f
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = Offset(
                    cx + (needleLen * cos(angleRad)).toFloat(),
                    cy + (needleLen * sin(angleRad)).toFloat(),
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 24.dp),
        ) {
            Text(
                text = ppm.toInt().toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ppm  ${level.name}",
                color = GasLevelColors[level] ?: Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun ppmToAngle(ppm: Float): Float = (ppm / 500f) * 140f

@Composable
fun SensorCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RealtimeChart(history: List<SensorReading>, modifier: Modifier = Modifier) {
    val thresholds = listOf(
        50f  to GasLevel.SAFE,
        200f to GasLevel.WARNING,
        350f to GasLevel.DANGER,
    )
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas
        val maxPpm = 500f
        val stepX = size.width / (history.size - 1).toFloat()
        val path = Path()
        history.forEachIndexed { i, r ->
            val x = i * stepX
            val y = size.height * (1f - r.ppm / maxPpm)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFF2196F3), style = Stroke(width = 2.dp.toPx()))
        thresholds.forEach { (ppm, lvl) ->
            val y = size.height * (1f - ppm / maxPpm)
            drawLine(
                color = (GasLevelColors[lvl] ?: Color.Gray).copy(alpha = 0.6f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

@Composable
fun TrendIndicator(status: GasStatus?, history: List<SensorReading>) {
    val trend = status?.trend ?: Trend.STABLE
    val avg = if (history.isNotEmpty()) history.takeLast(5).map { it.ppm }.average() else 0.0
    val arrow = when (trend) {
        Trend.RISING  -> "↑"
        Trend.FALLING -> "↓"
        Trend.STABLE  -> "→"
    }
    val color = when (trend) {
        Trend.RISING  -> Color(0xFFFF5722)
        Trend.FALLING -> Color(0xFF4CAF50)
        Trend.STABLE  -> Color(0xFF9E9E9E)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Text(text = arrow, fontSize = 28.sp, color = color)
        Spacer(modifier = Modifier.width(8.dp))
        Text("直近5件平均: ${"%.1f".format(avg)} ppm", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AlarmOverlay(level: GasLevel, ppm: Float, onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f, label = "pulse",
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background((GasLevelColors[GasLevel.CRITICAL] ?: Color.Red).copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("⚠ 危険レベル検出", style = MaterialTheme.typography.displayMedium, color = Color.White)
            Text(
                "${ppm.toInt()} ppm",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red),
            ) {
                Text("確認", fontWeight = FontWeight.Bold)
            }
        }
    }
}
