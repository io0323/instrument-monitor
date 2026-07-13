package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.domain.model.GasDevice
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.ScanDevicesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceListViewModel(
    private val scanDevices   : ScanDevicesUseCase,
    private val connectDevice : ConnectDeviceUseCase,
) : ViewModel() {

    private val _scanResults     = MutableStateFlow<List<GasDevice>>(emptyList())
    val scanResults: StateFlow<List<GasDevice>> = _scanResults.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            scanDevices().collect { _scanResults.value = it }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    fun selectDevice(device: GasDevice) {
        viewModelScope.launch {
            connectDevice(device.id).collect { _connectionState.value = it }
        }
    }

    fun navigateToDashboard(): Boolean =
        _connectionState.value == BleConnectionState.Connected

    override fun onCleared() {
        scanJob?.cancel()
        _isScanning.value = false
        super.onCleared()
    }
}
