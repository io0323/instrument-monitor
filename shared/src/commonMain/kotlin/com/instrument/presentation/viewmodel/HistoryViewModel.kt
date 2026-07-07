package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

// 日付フィルターの種別
enum class DateFilter(val label: String) {
    ALL("全期間"),
    TODAY("今日"),
    WEEK("直近7日"),
    MONTH("今月"),
}

// 計測履歴の表示・CSV書き出しを担う ViewModel
class HistoryViewModel(private val logRepo: LogRepository) : ViewModel() {

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter

    val readings: StateFlow<List<GeoTaggedReading>> =
        combine(logRepo.getAllReadings(), _dateFilter) { all, filter ->
            val tz  = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            when (filter) {
                DateFilter.ALL   -> all
                DateFilter.TODAY -> {
                    val startOfDay = now.toLocalDateTime(tz).date
                        .atStartOfDayIn(tz).toEpochMilliseconds()
                    all.filter { it.reading.timestamp >= startOfDay }
                }
                DateFilter.WEEK  -> {
                    val cutoff = now.toEpochMilliseconds() - 7L * 24 * 60 * 60 * 1000
                    all.filter { it.reading.timestamp >= cutoff }
                }
                DateFilter.MONTH -> {
                    val local     = now.toLocalDateTime(tz)
                    val startOfMonth = kotlinx.datetime.LocalDate(local.year, local.monthNumber, 1)
                        .atStartOfDayIn(tz).toEpochMilliseconds()
                    all.filter { it.reading.timestamp >= startOfMonth }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

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
