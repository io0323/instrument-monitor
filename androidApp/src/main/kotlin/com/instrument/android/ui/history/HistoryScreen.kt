package com.instrument.android.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.instrument.android.ui.theme.GasLevelColors
import com.instrument.data.export.CsvExporter
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.presentation.viewmodel.HistoryViewModel
import org.koin.androidx.compose.koinViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val viewModel: HistoryViewModel = koinViewModel()
    val readings by viewModel.readings.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(exportState) {
        if (exportState is HistoryViewModel.ExportState.Done) {
            val uri = (exportState as HistoryViewModel.ExportState.Done).filePath
            val result = snackbarHostState.showSnackbar(
                message = "保存: $uri",
                actionLabel = "開く",
            )
            if (result == SnackbarResult.ActionPerformed) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(uri), "text/csv")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.let { context.startActivity(it) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("計測履歴") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val isExporting = exportState is HistoryViewModel.ExportState.Exporting
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = {
                            val exporter = CsvExporter(context)
                            viewModel.exportCsv { csv ->
                                exporter.export(csv).map { it.toString() }
                            }
                        }) { Text("CSV") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("リスト") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("マップ") })
            }
            when (selectedTab) {
                0 -> ReadingsList(readings = readings)
                1 -> ReadingsMap(readings = readings)
            }
        }
    }
}

@Composable
fun ReadingsList(readings: List<GeoTaggedReading>) {
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(readings) { r ->
            val color = GasLevelColors[r.level] ?: Color.Gray
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(fmt.format(Date(r.reading.timestamp)), style = MaterialTheme.typography.labelSmall)
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
fun ReadingsMap(readings: List<GeoTaggedReading>) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(it).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(12.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            readings.forEach { r ->
                if (r.lat != 0.0 && r.lng != 0.0) {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(r.lat, r.lng)
                        title    = "${r.reading.ppm.toInt()} ppm"
                        snippet  = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                            .format(Date(r.reading.timestamp))
                    }
                    mapView.overlays.add(marker)
                }
            }
            if (readings.isNotEmpty()) {
                val lat = readings.map { it.lat }.average()
                val lng = readings.map { it.lng }.average()
                if (lat != 0.0 && lng != 0.0) {
                    mapView.controller.setCenter(GeoPoint(lat, lng))
                }
            }
            mapView.invalidate()
        },
    )
}
