package com.instrument.presentation.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.instrument.domain.model.*
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.usecase.AlarmUseCase
import com.instrument.presentation.ui.alarm.formatSnoozeRemaining
import com.instrument.presentation.ui.theme.GasLevelColors
import com.instrument.presentation.viewmodel.DashboardViewModel
import com.instrument.domain.model.SessionStats
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.*

// 再接続バナーの背景色 (警告系オレンジ)
private val ReconnectBannerColor = Color(0xFFFFF3E0)
// 再接続失敗バナーの背景色
private val ReconnectFailedBannerColor = Color(0xFFFFEBEE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    onNavigateToDeviceList: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAlarm: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val history        by viewModel.recentHistory.collectAsStateWithLifecycle()
    val reconnectState by viewModel.reconnectState.collectAsStateWithLifecycle()
    val sessionStats   by viewModel.sessionStats.collectAsStateWithLifecycle()
    // カスタム閾値をゲージ・チャートに反映するために settings を購読する
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // errorMessage が非 null になったら Snackbar を表示してクリアする
    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearErrorMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ガス監視") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ConnectionBar(
                        deviceName = uiState.connectedDeviceName,
                        state = uiState.connectionState,
                        onScanClick = onNavigateToDeviceList,
                    )
                }
                // 再接続中または失敗時のバナーを接続バーとゲージの間に表示する
                item {
                    ReconnectBanner(
                        reconnectState = reconnectState,
                        onCancel = { viewModel.cancelReconnect() },
                        onRetry = { viewModel.cancelReconnect() }, // キャンセルで Idle に戻してから自動トリガを待つ
                    )
                }
                // 未接続かつ前回のデバイスが記憶されている場合に再接続を促すバナーを表示する
                item {
                    LastDeviceReconnectBanner(
                        connectionState = uiState.connectionState,
                        lastDeviceName = uiState.lastConnectedDeviceName,
                        lastDeviceId = uiState.lastConnectedDeviceId,
                        onReconnect = { id, name -> viewModel.connectDevice(id, name) },
                    )
                }
                item {
                    val status = uiState.gasStatus
                    GasGauge(
                        ppm = status?.reading?.ppm ?: 0f,
                        level = status?.level ?: GasLevel.SAFE,
                        warningPpm = settings.warningThresholdPpm.toFloat(),
                        dangerPpm = settings.dangerThresholdPpm.toFloat(),
                        criticalPpm = settings.criticalThresholdPpm.toFloat(),
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
                    RealtimeChart(
                        history = history,
                        warningPpm = settings.warningThresholdPpm.toFloat(),
                        dangerPpm = settings.dangerThresholdPpm.toFloat(),
                        criticalPpm = settings.criticalThresholdPpm.toFloat(),
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                }
                item {
                    TrendIndicator(status = uiState.gasStatus, history = history)
                }
                item {
                    SessionStatsCard(stats = sessionStats, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onNavigateToHistory, modifier = Modifier.weight(1f)) { Text("履歴") }
                        OutlinedButton(onClick = onNavigateToAlarm, modifier = Modifier.weight(1f)) { Text("アラーム") }
                    }
                }
            }

            // スヌーズ中はオーバーレイを抑制し、代わりにスヌーズバナーをオーバーレイ下部に表示する
            if (shouldShowAlarmOverlay(uiState.isAlarmActive, uiState.alarmLevel, uiState.isSnoozed)) {
                AlarmOverlay(
                    level     = uiState.alarmLevel ?: GasLevel.WARNING,
                    ppm       = uiState.gasStatus?.reading?.ppm ?: 0f,
                    onDismiss = { viewModel.dismissAlarm() },
                    onSnooze  = { minutes -> viewModel.snoozeAlarm(minutes) },
                )
            }
            if (uiState.isSnoozed) {
                SnoozeBanner(
                    snoozeRemainingMs = uiState.snoozeRemainingMs,
                    onCancelSnooze    = { viewModel.cancelSnooze() },
                )
            }
        }
    }
}

// 接続状態に応じた表示ラベルを返す純粋関数
internal fun connectionBarLabel(state: BleConnectionState, deviceName: String?): String {
    val connected  = state == BleConnectionState.Connected
    val connecting = state == BleConnectionState.Connecting || state == BleConnectionState.Scanning
    return when {
        connected  -> deviceName ?: "接続済み"
        connecting -> "接続中..."
        state is BleConnectionState.Error -> state.message
        else       -> "未接続"
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
            text = connectionBarLabel(state, deviceName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (!connected) {
            TextButton(onClick = onScanClick) { Text("スキャン") }
        }
    }
}

@Composable
fun GasGauge(
    ppm: Float,
    level: GasLevel,
    warningPpm: Float = GasLevel.WARNING_THRESHOLD,
    dangerPpm: Float = GasLevel.DANGER_THRESHOLD,
    criticalPpm: Float = GasLevel.CRITICAL_THRESHOLD,
    modifier: Modifier = Modifier,
) {
    val isCritical = level == GasLevel.CRITICAL
    val levelColor = GasLevelColors[level] ?: Color.Green
    val criticalColor = GasLevelColors[GasLevel.CRITICAL] ?: Color.Red

    val infiniteTransition = rememberInfiniteTransition(label = "gauge_anim")

    // アーク点滅: CRITICAL時は暗→明を繰り返す (それ以外は常時 1f)
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = if (isCritical) 0.15f else 1f,
        targetValue = 1f,
        label = "arc_blink",
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
    )

    // ppm/レベルテキスト色: CRITICAL時はレベルカラー ↔ 白で点滅
    val labelColor by infiniteTransition.animateColor(
        initialValue = levelColor,
        targetValue = if (isCritical) Color.White else levelColor,
        label = "label_color",
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
    )

    // 背景グロー透明度: CRITICAL時のみ赤いパルスを表示
    val bgGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isCritical) 0.18f else 0f,
        label = "bg_glow",
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
    )

    val animatedAngle by animateFloatAsState(
        targetValue = ppmToAngle(ppm.coerceIn(0f, 500f)),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "needle",
    )

    Box(
        modifier = modifier
            .background(
                color = criticalColor.copy(alpha = bgGlowAlpha),
                shape = MaterialTheme.shapes.large,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height * 0.65f
            val radius = minOf(cx, cy) * 0.85f
            val startAngle = 200f
            val sweepTotal = 140f
            val maxPpm = 500f

            fun ppmToSweep(ppmVal: Float) = (ppmVal / maxPpm) * sweepTotal

            // カスタム閾値に基づいてゲージ帯域を構築する
            val bands = listOf(
                GasLevel.SAFE     to (0f to warningPpm),
                GasLevel.WARNING  to (warningPpm to dangerPpm),
                GasLevel.DANGER   to (dangerPpm to criticalPpm),
                GasLevel.CRITICAL to (criticalPpm to maxPpm),
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

            drawArc(
                color = levelColor.copy(alpha = if (isCritical) blinkAlpha else 1f),
                startAngle = startAngle, sweepAngle = ppmToSweep(ppm.coerceIn(0f, maxPpm)),
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt),
            )

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
                color = labelColor,
            )
            Text(
                text = "ppm  ${level.name}",
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal fun ppmToAngle(ppm: Float): Float = (ppm / 500f) * 140f

/**
 * アラームオーバーレイを表示すべきかを判定する純粋関数。
 * スヌーズ中は CRITICAL 以外を抑制する。
 */
internal fun shouldShowAlarmOverlay(
    isAlarmActive: Boolean,
    alarmLevel: GasLevel?,
    isSnoozed: Boolean,
): Boolean = isAlarmActive && alarmLevel != null && (!isSnoozed || alarmLevel == GasLevel.CRITICAL)

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
fun RealtimeChart(
    history: List<SensorReading>,
    warningPpm: Float = GasLevel.WARNING_THRESHOLD,
    dangerPpm: Float = GasLevel.DANGER_THRESHOLD,
    criticalPpm: Float = GasLevel.CRITICAL_THRESHOLD,
    modifier: Modifier = Modifier,
) {
    // カスタム閾値で横線を描画する (各ラインは境界の開始レベル色で示す)
    val thresholds = listOf(
        warningPpm  to GasLevel.SAFE,
        dangerPpm   to GasLevel.WARNING,
        criticalPpm to GasLevel.DANGER,
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

// 直近 [count] 件の ppm 平均を返す。history が空の場合は 0.0 を返す
internal fun computeRecentAverage(history: List<SensorReading>, count: Int = 5): Double =
    if (history.isEmpty()) 0.0 else history.takeLast(count).map { it.ppm }.average()

@Composable
fun TrendIndicator(status: GasStatus?, history: List<SensorReading>) {
    val trend = status?.trend ?: Trend.STABLE
    val avg = computeRecentAverage(history)
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

/**
 * 前回接続していたデバイスへの再接続を促すバナー。
 * 未接続状態かつ記憶済みデバイスが存在し、再接続フローが走っていない場合のみ表示する。
 */
@Composable
fun LastDeviceReconnectBanner(
    connectionState: BleConnectionState,
    lastDeviceName: String?,
    lastDeviceId: String?,
    onReconnect: (deviceId: String, deviceName: String?) -> Unit,
) {
    val isDisconnected = connectionState == BleConnectionState.Disconnected
    if (!isDisconnected || lastDeviceId == null) return

    val displayName = lastDeviceName ?: lastDeviceId
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "前回のデバイス",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = { onReconnect(lastDeviceId, lastDeviceName) }) {
                Text("再接続")
            }
        }
    }
}

/**
 * 再接続バナー: 再接続中は進捗とキャンセルボタン、失敗時はエラーと再試行ボタンを表示する。
 * [ReconnectState.Idle] および [ReconnectState.Connected] の場合は何も表示しない。
 */
@Composable
fun ReconnectBanner(
    reconnectState: ReconnectState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    when (reconnectState) {
        is ReconnectState.Reconnecting -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ReconnectBannerColor),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "再接続中... (${reconnectState.attempt}/${reconnectState.maxAttempts})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancel) {
                            Text("キャンセル")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { reconnectState.attempt.toFloat() / reconnectState.maxAttempts },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        is ReconnectState.Failed -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ReconnectFailedBannerColor),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "再接続に失敗しました",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text("再試行")
                    }
                }
            }
        }

        // Idle・Connected 時はバナーを表示しない
        is ReconnectState.Idle,
        is ReconnectState.Connected -> Unit
    }
}

/**
 * セッション中の統計サマリを表示するカード。
 * [stats] が null の場合（計測開始前）は何も表示しない。
 */
@Composable
fun SessionStatsCard(stats: SessionStats?, modifier: Modifier = Modifier) {
    if (stats == null) return

    val peakColor = GasLevelColors[stats.peakLevel] ?: Color(0xFF4CAF50)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("セッション統計", style = MaterialTheme.typography.titleSmall)
                Surface(
                    color  = peakColor.copy(alpha = 0.2f),
                    shape  = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text     = "ピーク: ${stats.peakLevel.name}",
                        color    = peakColor,
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionStatItem(label = "最小", value = "${stats.minPpm.toInt()} ppm", modifier = Modifier.weight(1f))
                SessionStatItem(label = "平均", value = "${"%.1f".format(stats.avgPpm)} ppm", modifier = Modifier.weight(1f))
                SessionStatItem(label = "最大", value = "${stats.maxPpm.toInt()} ppm", modifier = Modifier.weight(1f))
            }
            Text(
                text  = "計測回数: ${stats.readingCount} 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionStatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AlarmOverlay(
    level    : GasLevel,
    ppm      : Float,
    onDismiss: () -> Unit,
    onSnooze : (Int) -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val overlayColor = GasLevelColors[level] ?: (GasLevelColors[GasLevel.CRITICAL] ?: Color.Red)
    val title = when (level) {
        GasLevel.WARNING  -> "⚠ 注意レベル検出"
        GasLevel.DANGER   -> "⚠ 危険レベル検出"
        GasLevel.CRITICAL -> "🚨 緊急レベル検出"
        GasLevel.SAFE     -> "監視中"
    }
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f, label = "pulse",
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.displayMedium, color = Color.White)
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
            // CRITICAL 以外のレベルはスヌーズを選択できる
            if (level != GasLevel.CRITICAL) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlarmUseCase.SNOOZE_OPTIONS_MINUTES.forEach { minutes ->
                        OutlinedButton(
                            onClick = { onSnooze(minutes) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border  = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            ),
                        ) {
                            Text("${minutes}分スヌーズ")
                        }
                    }
                }
            }
        }
    }
}

/**
 * スヌーズ中に画面下部に表示するバナー。
 * 残り時間と解除ボタンを含む。
 */
@Composable
fun SnoozeBanner(
    snoozeRemainingMs: Long,
    onCancelSnooze   : () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = Color(0xFF37474F),
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = "スヌーズ中: ${formatSnoozeRemaining(snoozeRemainingMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = onCancelSnooze) {
                    Text("解除", color = Color(0xFFFFCC02), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
