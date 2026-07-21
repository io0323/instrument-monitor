package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.domain.model.GeoTaggedReading
import com.instrument.domain.repository.LogRepository
import com.instrument.domain.usecase.DeleteOldLogsUseCase
import com.instrument.domain.usecase.ExportCsvUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
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

// 計測履歴の表示・CSV書き出し・古いログ削除を担う ViewModel
class HistoryViewModel(
    private val logRepo: LogRepository,
    private val exportCsvUseCase: ExportCsvUseCase = ExportCsvUseCase(logRepo),
    private val deleteOldLogsUseCase: DeleteOldLogsUseCase = DeleteOldLogsUseCase(logRepo),
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter

    val readings: StateFlow<List<GeoTaggedReading>> =
        combine(logRepo.getAllReadings(), _dateFilter) { all, filter ->
            filterReadings(all, filter, clock.now(), timeZone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    fun exportCsv(onExport: suspend (String) -> Result<String>) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            exportCsvUseCase(onSave = onExport)
                .onSuccess { uri -> _exportState.value = ExportState.Done(uri) }
                .onFailure { e -> _exportState.value = ExportState.Error(e.message ?: "不明なエラー") }
        }
    }

    // エクスポート状態を Idle にリセットする（再エクスポート前に呼ぶ）
    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState

    // [daysToKeep] 日分より古いログを削除する（デフォルト 30 日）
    fun deleteOldLogs(daysToKeep: Int = 30) {
        viewModelScope.launch {
            _deleteState.value = DeleteState.Deleting
            val result: Result<Unit> = deleteOldLogsUseCase(daysToKeep = daysToKeep)
            result
                .onSuccess { _deleteState.value = DeleteState.Done }
                .onFailure { e -> _deleteState.value = DeleteState.Error(e.message ?: "不明なエラー") }
        }
    }

    // 削除状態を Idle にリセットする
    fun clearDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

    sealed class ExportState {
        object Idle                           : ExportState()
        object Exporting                      : ExportState()
        data class Done(val filePath: String) : ExportState()
        data class Error(val msg: String)     : ExportState()
    }

    sealed class DeleteState {
        object Idle     : DeleteState()
        object Deleting : DeleteState()
        object Done     : DeleteState()
        data class Error(val msg: String) : DeleteState()
    }
}

/**
 * [DateFilter] に従って [all] をフィルタリングして返す純粋関数。
 * ViewModel の外からも直接テストできるよう [internal] に公開する。
 */
internal fun filterReadings(
    all: List<GeoTaggedReading>,
    filter: DateFilter,
    now: Instant,
    timeZone: TimeZone,
): List<GeoTaggedReading> = when (filter) {
    DateFilter.ALL   -> all
    DateFilter.TODAY -> {
        val startOfDay = now.toLocalDateTime(timeZone).date
            .atStartOfDayIn(timeZone).toEpochMilliseconds()
        all.filter { it.reading.timestamp >= startOfDay }
    }
    DateFilter.WEEK  -> {
        val cutoff = now.toEpochMilliseconds() - 7L * 24 * 60 * 60 * 1000
        all.filter { it.reading.timestamp >= cutoff }
    }
    DateFilter.MONTH -> {
        val local        = now.toLocalDateTime(timeZone)
        val startOfMonth = LocalDate(local.year, local.monthNumber, 1)
            .atStartOfDayIn(timeZone).toEpochMilliseconds()
        all.filter { it.reading.timestamp >= startOfMonth }
    }
}

