package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.usecase.AlarmUseCase
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.LogMeasurementUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val connectionState: BleConnectionState = BleConnectionState.Disconnected,
    val gasStatus      : GasStatus?         = null,
    val isAlarmActive  : Boolean            = false,
    val alarmLevel     : GasLevel?          = null,
    val errorMessage   : String?            = null,
)

class DashboardViewModel(
    private val alarmUseCase   : AlarmUseCase,
    private val connectDevice  : ConnectDeviceUseCase,
    private val logMeasurement : LogMeasurementUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _recentHistory = MutableStateFlow<List<SensorReading>>(emptyList())
    val recentHistory: StateFlow<List<SensorReading>> = _recentHistory.asStateFlow()

    private var monitorJob: Job? = null

    init { startMockMode() }

    fun connectDevice(deviceId: String) {
        viewModelScope.launch {
            connectDevice.invoke(deviceId).collect { state ->
                _uiState.update {
                    when (state) {
                        BleConnectionState.Scanning,
                        BleConnectionState.Connecting,
                        BleConnectionState.Connected,
                        BleConnectionState.Disconnected -> it.copy(
                            connectionState = state,
                            errorMessage = null,
                        )

                        is BleConnectionState.Error -> it.copy(
                            connectionState = state,
                            errorMessage = state.message,
                        )
                    }
                }
                if (state == BleConnectionState.Connected) startMonitoring()
            }
        }
    }

    fun startMockMode() {
        _uiState.update { it.copy(connectionState = BleConnectionState.Connected) }
        startMonitoring()
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            alarmUseCase.observe().collect { status ->
                // DANGER 以上は GPS 座標と紐付けて自動ロギング
                // 失敗しても監視は継続し、エラーメッセージのみ更新する
                launch {
                    logMeasurement.invoke(status).onFailure { e ->
                        _uiState.update { it.copy(errorMessage = "ログ保存に失敗しました: ${e.message}") }
                    }
                }

                // WARNING 以上でアラームバナーを表示
                val isAlarmActive = status.level >= GasLevel.WARNING
                _uiState.update {
                    it.copy(
                        gasStatus     = status,
                        isAlarmActive = isAlarmActive,
                        alarmLevel    = if (isAlarmActive) status.level else null,
                        errorMessage  = null,
                    )
                }
                // 直近60件の読み取り履歴を保持
                val deque = ArrayDeque(_recentHistory.value)
                if (deque.size >= 60) deque.removeFirst()
                deque.addLast(status.reading)
                _recentHistory.value = deque.toList()
            }
        }
    }

    fun dismissAlarm() {
        alarmUseCase.dismiss()
        _uiState.update { it.copy(isAlarmActive = false, alarmLevel = null) }
    }

    override fun onCleared() {
        monitorJob?.cancel()
        // AlarmController が保持するリソース（音声・振動）を解放する
        alarmUseCase.release()
        super.onCleared()
    }
}
