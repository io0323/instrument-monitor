package com.instrument.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.instrument.domain.model.GeoTaggedReading
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@Composable
actual fun InstrumentMap(readings: List<GeoTaggedReading>, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(12.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            readings.forEach { r ->
                if (r.lat != 0.0 && r.lng != 0.0) {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(r.lat, r.lng)
                        title    = "${r.reading.ppm.toInt()} ppm"
                        snippet  = fmt.format(Date(r.reading.timestamp))
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
