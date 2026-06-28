package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.data.mock.MockBleSource
import com.instrument.data.repository.MockBleRepository
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.model.SensorReading
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.MonitorGasUseCase
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
    private val monitorGas    : MonitorGasUseCase,
    private val connectDevice : ConnectDeviceUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _recentHistory = MutableStateFlow<List<SensorReading>>(emptyList())
    val recentHistory: StateFlow<List<SensorReading>> = _recentHistory.asStateFlow()

    private var monitorJob: Job? = null
    private var lastAlarmTime: Long = 0L
    private var lastDismissedLevel: GasLevel? = null
    private val alarmSuppressMs = 30_000L

    init {
        startMockMode()
    }

    fun connectDevice(deviceId: String) {
        viewModelScope.launch {
            connectDevice.invoke(deviceId).collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state == BleConnectionState.Connected) startMonitoring()
            }
        }
    }

    fun startMockMode() {
        val mockRepo = MockBleRepository(MockBleSource())
        val mockMonitor = MonitorGasUseCase(mockRepo)
        _uiState.update { it.copy(connectionState = BleConnectionState.Connected) }
        startMonitoring(mockMonitor)
    }

    private fun startMonitoring(useCase: MonitorGasUseCase = monitorGas) {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            useCase().collect { status ->
                val now = System.currentTimeMillis()
                val isAlarm = status.level >= GasLevel.WARNING &&
                    !(status.level == lastDismissedLevel && now - lastAlarmTime < alarmSuppressMs)
                _uiState.update { it.copy(
                    gasStatus     = status,
                    isAlarmActive = isAlarm,
                    alarmLevel    = if (isAlarm) status.level else it.alarmLevel,
                    errorMessage  = null,
                ) }
                val deque = ArrayDeque(_recentHistory.value)
                if (deque.size >= 60) deque.removeFirst()
                deque.addLast(status.reading)
                _recentHistory.value = deque.toList()
            }
        }
    }

    fun dismissAlarm() {
        val level = _uiState.value.alarmLevel
        lastDismissedLevel = level
        lastAlarmTime = System.currentTimeMillis()
        _uiState.update { it.copy(isAlarmActive = false) }
    }
}
