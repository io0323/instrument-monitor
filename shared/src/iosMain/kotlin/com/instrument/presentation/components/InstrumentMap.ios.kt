package com.instrument.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.instrument.domain.model.GeoTaggedReading
import kotlinx.cinterop.ExperimentalForeignApi
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.CoreLocation.CLLocationCoordinate2DMake

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun InstrumentMap(readings: List<GeoTaggedReading>, modifier: Modifier) {
    UIKitView(
        modifier = modifier,
        factory = { MKMapView() },
        update = { mapView ->
            mapView.removeAnnotations(mapView.annotations)
            readings.forEach { r ->
                if (r.lat != 0.0 && r.lng != 0.0) {
                    val annotation = MKPointAnnotation().apply {
                        coordinate = CLLocationCoordinate2DMake(r.lat, r.lng)
                        title      = "${r.reading.ppm.toInt()} ppm"
                    }
                    mapView.addAnnotation(annotation)
                }
            }
        },
    )
}
