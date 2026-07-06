package com.instrument.presentation.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.domain.model.GasDevice
import com.instrument.domain.repository.BleConnectionState
import com.instrument.presentation.viewmodel.DeviceListViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(onNavigateBack: () -> Unit) {
    val viewModel: DeviceListViewModel = koinViewModel()
    val devices by viewModel.scanResults.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.startScan() }

    LaunchedEffect(connectionState) {
        if (connectionState == BleConnectionState.Connected) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isScanning) "スキャン中..." else "デバイス一覧") },
                navigationIcon = {
                    TextButton(onClick = { viewModel.stopScan(); onNavigateBack() }) { Text("←") }
                },
                actions = {
                    if (isScanning) {
                        TextButton(onClick = { viewModel.stopScan() }) { Text("停止") }
                    } else {
                        TextButton(onClick = { viewModel.startScan() }) { Text("再スキャン") }
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isScanning && devices.isEmpty()) {
                items(3) { LoadingItem() }
            } else {
                items(devices) { device ->
                    DeviceItem(device = device, onClick = { viewModel.selectDevice(device) })
                }
            }
        }
    }
}

@Composable
private fun LoadingItem() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text("スキャン中...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun DeviceItem(device: GasDevice, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    device.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RssiBar(rssi = device.rssi)
        }
    }
}

/**
 * WiFi電波アイコン風のRSSI強度インジケーター。
 * 3段のアークとドットで信号強度を表示し、強度に応じて色が変化する。
 *
 *  強度区分:
 *   rssi > -65 dBm → 強 (3アーク / 緑)
 *   rssi > -75 dBm → 中 (2アーク / 黄)
 *   rssi > -85 dBm → 弱 (1アーク / オレンジ)
 *   rssi ≤ -85 dBm → 圏外 (0アーク / 赤)
 */
@Composable
fun RssiBar(rssi: Int) {
    val activeCount = when {
        rssi > -65 -> 3
        rssi > -75 -> 2
        rssi > -85 -> 1
        else       -> 0
    }
    val activeColor = when (activeCount) {
        3    -> Color(0xFF4CAF50) // 強: 緑
        2    -> Color(0xFFFFB300) // 中: 琥珀
        1    -> Color(0xFFFF7043) // 弱: オレンジ
        else -> Color(0xFFF44336) // 圏外: 赤
    }
    val inactiveColor = Color(0xFF616161)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 36.dp, height = 28.dp)) {
            val cx   = size.width / 2f
            val dotY = size.height * 0.88f
            val dotRadius  = size.width * 0.07f
            val strokeW    = size.width * 0.10f

            // ドット (中心点)
            val dotColor = if (activeCount > 0) activeColor else inactiveColor
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(cx, dotY))

            // 3段のWiFiアーク (下から上に大きくなる)
            val arcRadii = listOf(
                size.width * 0.22f to 1,
                size.width * 0.38f to 2,
                size.width * 0.54f to 3,
            )
            arcRadii.forEach { (radius, level) ->
                val arcColor = if (level <= activeCount) activeColor else inactiveColor
                drawArc(
                    color      = arcColor,
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter  = false,
                    topLeft    = Offset(cx - radius, dotY - radius),
                    size       = Size(radius * 2f, radius * 2f),
                    style      = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
        }

        // dBm 数値ラベル
        Text(
            text  = "${rssi} dBm",
            style = MaterialTheme.typography.labelSmall,
            color = activeColor,
        )
    }
}
