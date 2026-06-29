package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 計測履歴の表示・CSV書き出しを担う ViewModel
class HistoryViewModel(private val logRepo: LogRepository) : ViewModel() {

    val readings: StateFlow<List<GeoTaggedReading>> = logRepo.getAllReadings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    fun exportCsv(onExport: suspend (String) -> Result<String>) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            logRepo.exportCsv()
                .mapCatching { csv -> onExport(csv).getOrThrow() }
                .onSuccess { uri -> _exportState.value = ExportState.Done(uri) }
                .onFailure { e -> _exportState.value = ExportState.Error(e.message ?: "不明なエラー") }
        }
    }

    sealed class ExportState {
        object Idle                           : ExportState()
        object Exporting                      : ExportState()
        data class Done(val filePath: String) : ExportState()
        data class Error(val msg: String)     : ExportState()
    }
}
