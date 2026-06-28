package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel(
    private val logRepo: LogRepository
) : ViewModel() {

    val readings    : StateFlow<List<GeoTaggedReading>> = MutableStateFlow(emptyList())
    val exportState : StateFlow<ExportState>            = MutableStateFlow(ExportState.Idle)

    sealed class ExportState {
        object Idle                           : ExportState()
        object Exporting                      : ExportState()
        data class Done(val filePath: String) : ExportState()
        data class Error(val msg: String)     : ExportState()
    }
}
