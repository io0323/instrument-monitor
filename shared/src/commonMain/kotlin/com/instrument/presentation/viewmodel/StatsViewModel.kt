package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.domain.model.LogPeriodStats
import com.instrument.domain.usecase.GetLogStatisticsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 統計画面用の ViewModel。
 * [GetLogStatisticsUseCase] から過去 7 日分の集計データを取得して UI へ提供する。
 */
class StatsViewModel(
    private val getLogStatisticsUseCase: GetLogStatisticsUseCase,
) : ViewModel() {

    /** 週間ログ統計。データがない場合や読み込み中は null。 */
    val weeklyStats: StateFlow<LogPeriodStats?> =
        getLogStatisticsUseCase.getWeeklyStats()
            .stateIn(
                scope            = viewModelScope,
                started          = SharingStarted.WhileSubscribed(5_000),
                initialValue     = null,
            )
}
