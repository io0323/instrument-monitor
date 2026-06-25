# Phase 2 — BLE KMP 実装 (Kable + MockBleSource)

CLAUDE.md のドメインモデル・BLE UUIDs・Mockモード仕様を参照して実装すること。

---

## 実装対象ファイル

### commonMain (プラットフォーム非依存)

#### `domain/repository/BleRepository.kt`
```kotlin
interface BleRepository {
    fun scanDevices(): Flow<List<GasDevice>>
    fun connect(deviceId: String): Flow<BleConnectionState>
    fun observeSensorData(): Flow<SensorReading>
    suspend fun disconnect()
}

sealed class BleConnectionState {
    object Scanning    : BleConnectionState()
    object Connecting  : BleConnectionState()
    object Connected   : BleConnectionState()
    object Disconnected: BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}
```

#### `data/mock/MockBleSource.kt`

CLAUDE.md の Mock仕様に従い実装:
- `sin(t) * 150 + 150` に `(-20..20).random()` を加算して ppm を生成
- `temperature`: 20.0f 〜 30.0f をゆっくり変動
- `humidity`: 40.0f 〜 70.0f をゆっくり変動
- 10秒に1回 ppm=380f を1回だけ emit (CRITICAL 挿入)
- `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()` で timestamp
- `flow { while(true) { emit(...); delay(500) } }` で実装
- `flowOn(Dispatchers.Default)`

```kotlin
// シグネチャ
class MockBleSource {
    fun observeSensorData(): Flow<SensorReading>
    fun scanDevices(): Flow<List<GasDevice>>   // ダミーデバイス3件を返す
}
```

ダミーデバイスリスト:
```kotlin
listOf(
    GasDevice(id = "AA:BB:CC:DD:EE:01", name = "instrument-G100", rssi = -62),
    GasDevice(id = "AA:BB:CC:DD:EE:02", name = "instrument-G200", rssi = -78),
    GasDevice(id = "AA:BB:CC:DD:EE:03", name = "instrument-G300", rssi = -85),
)
```

#### `data/repository/MockBleRepository.kt`
`MockBleSource` を DI で受け取り `BleRepository` を実装する。

---

### androidMain — 実機BLE実装

#### `data/ble/AndroidBleDataSource.kt`

Kable を使った実装:

```kotlin
// Kable の Scanner / Peripheral を使う
// Scanner でスキャン → DiscoveredPeripheral を GasDevice にマッピング
// Peripheral.connect() → SERVICE_UUID / CHAR_UUID を observe()
// observe() の ByteArray → SensorReading にパース

// パース関数
fun ByteArray.toSensorReading(): SensorReading {
    val ppm         = ((this[0].toInt() and 0xFF) shl 8 or (this[1].toInt() and 0xFF)).toFloat()
    val temperature = ((this[2].toInt() and 0xFF) shl 8 or (this[3].toInt() and 0xFF)) / 10f
    val humidity    = ((this[4].toInt() and 0xFF) shl 8 or (this[5].toInt() and 0xFF)) / 10f
    return SensorReading(ppm, temperature, humidity, Clock.System.now().toEpochMilliseconds())
}
```

#### `data/repository/RealBleRepository.kt`
`AndroidBleDataSource` を受け取り `BleRepository` を実装する。

---

### iosMain — iOS BLE実装

#### `data/ble/IosBleDataSource.kt`

- Kable は iOS でも動作するため `AndroidBleDataSource` と同じ Kable API を使って実装
- パース関数は commonMain に切り出し (`ByteArray.toSensorReading()` を `commonMain/data/ble/BleParser.kt` に配置)

#### `data/repository/IosRealBleRepository.kt`
`IosBleDataSource` を受け取り `BleRepository` を実装する。

---

### DI 定義

#### `commonMain/di/BleModule.kt`

```kotlin
// expect/actual でプラットフォームごとのモジュールを切り替え
expect val bleModule: Module
```

#### `androidMain/di/AndroidBleModule.kt`

```kotlin
actual val bleModule = module {
    // USE_MOCK = true の場合は MockBleRepository、false なら RealBleRepository
    single<BleRepository> {
        if (BuildConfig.USE_MOCK) MockBleRepository(MockBleSource())
        else RealBleRepository(AndroidBleDataSource())
    }
}
```

#### `iosMain/di/IosBleModule.kt`

```kotlin
actual val bleModule = module {
    single<BleRepository> { MockBleRepository(MockBleSource()) }
    // 実機接続時: IosRealBleRepository(IosBleDataSource())
}
```

---

## パーミッション処理 (androidMain)

`AndroidBleDataSource.kt` の `scanDevices()` 開始前に
`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` / `ACCESS_FINE_LOCATION` の
パーミッション確認を行い、未取得なら `BleConnectionState.Error("パーミッションが必要です")` を emit して終了すること。

パーミッション確認は `ContextCompat.checkSelfPermission` で行う。

---

## 完了条件

- [ ] MockモードでSensorReadingが500ms毎にemitされることをログで確認できる
- [ ] 10秒に1回 ppm=380f が混入することを確認できる
- [ ] `./gradlew :shared:compileKotlinAndroid` が通る
- [ ] `./gradlew :shared:compileKotlinIosArm64` が通る (BleParser.kt が commonMain にあること)
