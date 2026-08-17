package com.instrument.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instrument.domain.model.GasLevel
import com.instrument.domain.model.GasStatus
import com.instrument.domain.model.ReconnectState
import com.instrument.domain.model.SensorReading
import com.instrument.domain.model.SessionStats
import com.instrument.domain.repository.BleConnectionState
import com.instrument.domain.repository.SettingsRepository
import com.instrument.domain.usecase.AlarmUseCase
import com.instrument.domain.usecase.ConnectDeviceUseCase
import com.instrument.domain.usecase.LogMeasurementUseCase
import com.instrument.domain.usecase.SessionStatsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val connectionState    : BleConnectionState = BleConnectionState.Disconnected,
    val connectedDeviceName: String?            = null,
    val gasStatus          : GasStatus?         = null,
    val isAlarmActive      : Boolean            = false,
    val alarmLevel         : GasLevel?          = null,
    val errorMessage       : String?            = null,
    // スヌーズ中かどうか
    val isSnoozed          : Boolean            = false,
    // スヌーズ残り時間 (ミリ秒)。0 はスヌーズ未設定・期限切れを意味する
    val snoozeRemainingMs  : Long               = 0L,
)

class DashboardViewModel(
    private val alarmUseCase      : AlarmUseCase,
    private val connectDevice     : ConnectDeviceUseCase,
    private val logMeasurement    : LogMeasurementUseCase,
    private val settingsRepo      : SettingsRepository,
    private val sessionStatsUseCase: SessionStatsUseCase = SessionStatsUseCase(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // 設定をそのまま公開することで UI がカスタム閾値をゲージ描画に利用できるようにする
    val settings = settingsRepo.settings

    private val _recentHistory = MutableStateFlow<List<SensorReading>>(emptyList())
    val recentHistory: StateFlow<List<SensorReading>> = _recentHistory.asStateFlow()

    private val _sessionStats = MutableStateFlow<SessionStats?>(null)
    val sessionStats: StateFlow<SessionStats?> = _sessionStats.asStateFlow()

    // 再接続状態を外部へ公開する
    private val _reconnectState = MutableStateFlow<ReconnectState>(ReconnectState.Idle)
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    // 再接続対象デバイスID (接続成功時に保持し、切断時に自動再接続へ使用する)
    private var lastConnectedDeviceId: String? = null

    private var monitorJob: Job? = null
    // 再接続ジョブ (キャンセル可能とするために保持する)
    private var reconnectJob: Job? = null
    // スヌーズ残り時間を 500ms 毎にポーリングして UI を更新するジョブ
    private var snoozeCountdownJob: Job? = null

    init { startMockMode() }

    fun connectDevice(deviceId: String, deviceName: String? = null) {
        viewModelScope.launch {
            connectDevice.invoke(deviceId).collect { state ->
                _uiState.update {
                    when (state) {
                        BleConnectionState.Scanning,
                        BleConnectionState.Connecting -> it.copy(
                            connectionState = state,
                            errorMessage = null,
                        )

                        BleConnectionState.Disconnected -> {
                            // 切断を検知したら自動再接続を開始する
                            triggerAutoReconnectIfNeeded()
                            it.copy(
                                connectionState = state,
                                errorMessage = null,
                            )
                        }

                        BleConnectionState.Connected -> {
                            // 接続成功: デバイスIDを記憶し、再接続状態をリセットする
                            lastConnectedDeviceId = deviceId
                            _reconnectState.value = ReconnectState.Idle
                            it.copy(
                                connectionState     = state,
                                connectedDeviceName = deviceName,
                                errorMessage        = null,
                            )
                        }

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

    /**
     * 切断検知時に呼ばれる内部ヘルパー。
     * 直前に接続していたデバイスIDがあれば自動再接続を開始する。
     */
    private fun triggerAutoReconnectIfNeeded() {
        val deviceId = lastConnectedDeviceId ?: return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            connectDevice.reconnect(deviceId).collect { state ->
                _reconnectState.value = state
                when (state) {
                    is ReconnectState.Connected -> {
                        // 再接続成功: モニタリングを再開する
                        _uiState.update { it.copy(connectionState = BleConnectionState.Connected) }
                        startMonitoring()
                    }
                    is ReconnectState.Failed -> {
                        // 再接続失敗: 状態を失敗のままにして UI へ通知する
                    }
                    is ReconnectState.Reconnecting -> { /* UI は reconnectState を監視する */ }
                    is ReconnectState.Idle -> { /* 通常は到達しない */ }
                }
            }
        }
    }

    /** 手動キャンセル: UI の「キャンセル」ボタンから呼ばれる */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnectState.value = ReconnectState.Idle
    }

    /** デバイス選択画面から戻った際に接続済みデバイス名を反映する */
    fun onDeviceConnected(deviceName: String) {
        _uiState.update { it.copy(connectedDeviceName = deviceName) }
    }

    fun startMockMode() {
        _uiState.update {
            it.copy(
                connectionState     = BleConnectionState.Connected,
                connectedDeviceName = "Mock Device",
            )
        }
        startMonitoring()
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            alarmUseCase.observe().collect { status ->
                // DANGER 以上は GPS 座標と紐付けて自動ロギング
                // 失敗しても監視は継続し、エラーメッセージのみ更新する
                // GPS ロギングが有効な場合のみ自動保存する
                if (settingsRepo.settings.value.gpsLoggingEnabled) {
                    launch {
                        logMeasurement.invoke(status).onFailure { e ->
                            _uiState.update { it.copy(errorMessage = "ログ保存に失敗しました: ${e.message}") }
                        }
                    }
                }

                // WARNING 以上でアラームバナーを表示。
                // スヌーズ中は CRITICAL 以外を抑制する (AlarmUseCase と同じルール)
                val levelTriggersAlarm = status.level >= GasLevel.WARNING
                val snoozed = alarmUseCase.isSnoozed()
                val isAlarmActive = levelTriggersAlarm && (!snoozed || status.level == GasLevel.CRITICAL)
                // SAFE に戻ったときはスヌーズも自動解除する
                if (status.level == GasLevel.SAFE && snoozed) {
                    alarmUseCase.cancelSnooze()
                    snoozeCountdownJob?.cancel()
                    snoozeCountdownJob = null
                }
                _uiState.update {
                    it.copy(
                        gasStatus     = status,
                        isAlarmActive = isAlarmActive,
                        alarmLevel    = if (isAlarmActive) status.level else null,
                        isSnoozed     = alarmUseCase.isSnoozed(),
                        snoozeRemainingMs = alarmUseCase.snoozeRemainingMs(),
                    )
                }
                // 直近60件の読み取り履歴を保持
                val deque = ArrayDeque(_recentHistory.value)
                if (deque.size >= 60) deque.removeFirst()
                deque.addLast(status.reading)
                val updated = deque.toList()
                _recentHistory.value = updated
                // カスタム閾値を反映した統計サマリをリアルタイムで更新する
                _sessionStats.value = sessionStatsUseCase.compute(updated, settingsRepo.settings.value)
            }
        }
    }

    fun dismissAlarm() {
        alarmUseCase.dismiss()
        _uiState.update { it.copy(isAlarmActive = false, alarmLevel = null) }
    }

    /**
     * 指定した分数アラームをスヌーズする。
     * スヌーズ中は WARNING・DANGER を抑制し、CRITICAL は引き続き発報する。
     */
    fun snoozeAlarm(minutes: Int) {
        alarmUseCase.snooze(minutes * 60_000L)
        _uiState.update { it.copy(isAlarmActive = false, alarmLevel = null, isSnoozed = true) }
        startSnoozeCountdown()
    }

    /** スヌーズを手動解除する */
    fun cancelSnooze() {
        alarmUseCase.cancelSnooze()
        snoozeCountdownJob?.cancel()
        snoozeCountdownJob = null
        _uiState.update { it.copy(isSnoozed = false, snoozeRemainingMs = 0L) }
    }

    /** 500ms ごとにスヌーズ残り時間をポーリングして UI を更新するコルーチンを起動する */
    private fun startSnoozeCountdown() {
        snoozeCountdownJob?.cancel()
        snoozeCountdownJob = viewModelScope.launch {
            while (true) {
                val remaining = alarmUseCase.snoozeRemainingMs()
                if (remaining <= 0L) {
                    _uiState.update { it.copy(isSnoozed = false, snoozeRemainingMs = 0L) }
                    break
                }
                _uiState.update { it.copy(isSnoozed = true, snoozeRemainingMs = remaining) }
                delay(500L)
            }
        }
    }

    /** Snackbar 表示後に呼び出してエラーメッセージをクリアする */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        monitorJob?.cancel()
        reconnectJob?.cancel()
        snoozeCountdownJob?.cancel()
        // AlarmController が保持するリソース（音声・振動）を解放する
        alarmUseCase.release()
        super.onCleared()
    }
}
