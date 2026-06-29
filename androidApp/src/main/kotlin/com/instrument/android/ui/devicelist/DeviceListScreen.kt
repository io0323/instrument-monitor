package com.instrument.android.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.domain.model.GasDevice
import com.instrument.domain.repository.BleConnectionState
import com.instrument.presentation.viewmodel.DeviceListViewModel
import com.valentinilk.shimmer.shimmer
import org.koin.androidx.compose.koinViewModel

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
                    IconButton(onClick = { viewModel.stopScan(); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
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
                items(3) { ShimmerItem() }
            } else {
                items(devices) { device ->
                    DeviceItem(device = device, onClick = { viewModel.selectDevice(device) })
                }
            }
        }
    }
}

@Composable
fun ShimmerItem() {
    Card(modifier = Modifier.fillMaxWidth().shimmer()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }
            RssiBar(rssi = -90)
        }
    }
}

@Composable
fun DeviceItem(device: GasDevice, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

@Composable
fun RssiBar(rssi: Int) {
    val bars = when {
        rssi > -65 -> 4
        rssi > -75 -> 3
        rssi > -85 -> 2
        else       -> 1
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        (1..4).forEach { i ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((4 + i * 5).dp)
                    .background(
                        color = if (i <= bars) Color(0xFF4CAF50) else Color(0xFF424242),
                        shape = MaterialTheme.shapes.extraSmall,
                    )
            )
        }
    }
}
