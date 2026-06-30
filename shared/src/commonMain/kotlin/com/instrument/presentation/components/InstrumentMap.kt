package com.instrument.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.instrument.domain.model.GeoTaggedReading

@Composable
expect fun InstrumentMap(readings: List<GeoTaggedReading>, modifier: Modifier = Modifier)
