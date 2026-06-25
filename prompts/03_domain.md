# Phase 3 — Domain UseCases + ViewModels

CLAUDE.md のドメインモデル・コーディング規約を参照して実装すること。
全ファイルは `shared/src/commonMain/` に配置する (プラットフォーム非依存)。

---

## UseCases

### `domain/usecase/MonitorGasUseCase.kt`

```kotlin
class MonitorGasUseCase(private val repo: BleRepository) {

    // SensorReading → GasStatus に変換して Flow で流す
    operator fun invoke(): Flow<GasStatus>
}
```

**GasLevel 判定ロジック**:
```
ppm < 50       → SAFE
50  ≤ ppm < 200 → WARNING
200 ≤ ppm < 350 → DANGER
ppm ≥ 350      → CRITICAL
```

**Trend 判定ロジック**:
- 直近5件の ppm リストを保持 (`ArrayDeque<Float>(5)`)
- 最新 ppm − 5件前 ppm の差分で判定:
  - 差分 > +10f  → `Trend.RISING`
  - 差分 < -10f  → `Trend.FALLING`
  - それ以外      → `Trend.STABLE`
- 件数が5未満の場合は `Trend.STABLE`

**実装方針**:
```kotlin
operator fun invoke(): Flow<GasStatus> = repo.observeSensorData()
    .scan(/* 初期状態: Pair(emptyDeque, null) */) { acc, reading ->
        // deque 更新 + GasStatus 計算
    }
    .mapNotNull { it.second }
```

---

### `domain/usecase/ConnectDeviceUseCase.kt`

```kotlin
class ConnectDeviceUseCase(private val repo: BleRepository) {
    operator fun invoke(deviceId: String): Flow<BleConnectionState> =
        repo.connect(deviceId)
}
```

---

### `domain/usecase/ScanDevicesUseCase.kt`

```kotlin
class ScanDevicesUseCase(private val repo: BleRepository) {
    operator fun invoke(): Flow<List<GasDevice>> = repo.scanDevices()
}
```

---

### `domain/usecase/LogMeasurementUseCase.kt`

```kotlin
class LogMeasurementUseCase(
    private val logRepo: LogRepository,
    private val gpsRepo: GpsRepository
) {
    // DANGER以上 → 自動保存
    // それ以外   → manualSave=true の場合のみ保存
    suspend operator fun invoke(status: GasStatus, manualSave: Boolean = false): Result<Unit>
}
```

`LogRepository` / `GpsRepository` は interface のみ定義 (実装は Phase 6-7)。

---

## ViewModels (commonMain)

`androidx.lifecycle:lifecycle-viewmodel` の KMP対応版を使うこと。

---

### `presentation/viewmodel/DashboardViewModel.kt`

```kotlin
class DashboardViewModel(
    private val monitorGas: MonitorGasUseCase,
    private val connectDevice: ConnectDeviceUseCase,
) : ViewModel() {

    // UI状態
    val uiState: StateFlow<DashboardUiState>

    // 直近60件の履歴 (グラフ用)
    val recentHistory: StateFlow<List<SensorReading>>

    fun connectDevice(deviceId: String)
    fun startMockMode()       // MockBleRepository を直接使い即座に接続
    fun dismissAlarm()
}

data class DashboardUiState(
    val connectionState : BleConnectionState = BleConnectionState.Disconnected,
    val gasStatus       : GasStatus?         = null,
    val isAlarmActive   : Boolean            = false,
    val alarmLevel      : GasLevel?          = null,
    val errorMessage    : String?            = null,
)
```

**実装方針**:
- `init { startMockMode() }` でViewModel初期化時に自動接続 (デモ用)
- `monitorGas()` を `viewModelScope` で collect
- `isAlarmActive`: `gasStatus.level >= WARNING` かつ dismiss されていない場合 true
- `recentHistory`: `ArrayDeque<SensorReading>(60)` を MutableStateFlow で管理
- アラーム dismiss 後、同一レベルのアラームは30秒間再発報しない (`lastAlarmTime` で管理)

---

### `presentation/viewmodel/DeviceListViewModel.kt`

```kotlin
class DeviceListViewModel(
    private val scanDevices: ScanDevicesUseCase,
    private val connectDevice: ConnectDeviceUseCase,
) : ViewModel() {

    val scanResults    : StateFlow<List<GasDevice>>
    val connectionState: StateFlow<BleConnectionState>
    val isScanning     : StateFlow<Boolean>

    fun startScan()
    fun stopScan()
    fun selectDevice(device: GasDevice)
    fun navigateToDashboard(): Boolean  // 接続成功時 true を返す
}
```

---

### `presentation/viewmodel/HistoryViewModel.kt` (骨格のみ)

```kotlin
class HistoryViewModel(
    private val logRepo: LogRepository
) : ViewModel() {

    val readings    : StateFlow<List<GeoTaggedReading>> = MutableStateFlow(emptyList())
    val exportState : StateFlow<ExportState>            = MutableStateFlow(ExportState.Idle)

    sealed class ExportState {
        object Idle                          : ExportState()
        object Exporting                     : ExportState()
        data class Done(val filePath: String): ExportState()
        data class Error(val msg: String)    : ExportState()
    }
}
```

---

## DI (commonMain)

### `di/DomainModule.kt`

```kotlin
val domainModule = module {
    factory { MonitorGasUseCase(get()) }
    factory { ConnectDeviceUseCase(get()) }
    factory { ScanDevicesUseCase(get()) }
    factory { LogMeasurementUseCase(get(), get()) }
}

val viewModelModule = module {
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { DeviceListViewModel(get(), get()) }
    viewModel { HistoryViewModel(get()) }
}
```

---

## 完了条件

- [ ] `MonitorGasUseCase` の単体テストが通る (Phase 9 で実装、ここでは骨格テストファイルを作成)
- [ ] `DashboardViewModel.uiState` が Mock の Flow に連動して更新されることをローカルテストで確認
- [ ] `./gradlew :shared:compileKotlinAndroid` が通る
