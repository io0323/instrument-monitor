# Phase 4 — Android Dashboard UI (Jetpack Compose)

Phase 3 の `DashboardViewModel` / `DeviceListViewModel` を使って
Android の Compose UI を実装する。
ファイルは `shared/src/androidMain/` または `androidApp/` に配置する。

---

## テーマ / カラー定義

### `instrumentTheme.kt`

```kotlin
// Material3 カラースキーム
// シード: 工業系ダークネイビー #1A237E
// GasLevel カラーは theme に含める

val GasLevelColors = mapOf(
    GasLevel.SAFE     to Color(0xFF4CAF50),
    GasLevel.WARNING  to Color(0xFFFFC107),
    GasLevel.DANGER   to Color(0xFFFF5722),
    GasLevel.CRITICAL to Color(0xFFF44336),
)
```

---

## DashboardScreen.kt

### レイアウト (LazyColumn)

```
┌─────────────────────────────┐
│ ConnectionBar               │  ← デバイス名 + 接続状態
├─────────────────────────────┤
│                             │
│       GasGauge              │  ← Canvas 半円ゲージ (中央)
│                             │
├──────────────┬──────────────┤
│ TempCard     │ HumidityCard │  ← 横2列カード
├─────────────────────────────┤
│ RealtimeChart               │  ← 折れ線グラフ (Vico)
├─────────────────────────────┤
│ TrendIndicator              │  ← ↑↓→ + 5件平均
└─────────────────────────────┘
```

AlarmScreen のオーバーレイは `DashboardScreen` 内で `isAlarmActive` を見て表示切り替え。

---

## GasGauge コンポーザブル

```kotlin
@Composable
fun GasGauge(
    ppm: Float,
    level: GasLevel,
    modifier: Modifier = Modifier
)
```

**Canvas 描画仕様**:
- 半円: 200° 開口 (下部 160° を空ける)
- 4色の弧:
  - SAFE帯    (0〜50ppm相当角度):    `GasLevelColors[SAFE]`    alpha=0.3
  - WARNING帯 (50〜200ppm相当角度):  `GasLevelColors[WARNING]` alpha=0.3
  - DANGER帯  (200〜350ppm相当角度): `GasLevelColors[DANGER]`  alpha=0.3
  - CRITICAL帯(350〜500ppm相当角度): `GasLevelColors[CRITICAL]` alpha=0.3
  - アクティブ弧: 0 〜 現在ppm の範囲を `GasLevelColors[level]` alpha=1.0 で上書き
- ニードル: `animateFloatAsState(targetValue = ppmToAngle(ppm), animationSpec = spring())`
- 中央テキスト:
  - 上: ppm 値 (MaterialTheme.typography.displayLarge)
  - 下: "ppm" + GasLevel 名 (色付き)
- CRITICAL 時: `GasLevelColors[CRITICAL]` で外枠が点滅 (`InfiniteTransition`)

---

## RealtimeChart コンポーザブル

Vico を使用:
```kotlin
@Composable
fun RealtimeChart(history: List<SensorReading>)
```

- `LineChart` (Vico compose-m3)
- X軸: インデックス (最新60件)
- Y軸: 0〜500 固定
- 警戒閾値ライン (y=50, y=200, y=350) を `ThresholdLine` で表示
- 各ラインの色は `GasLevelColors` に対応

---

## DeviceListScreen.kt

```
┌─────────────────────────────┐
│ スキャン中... [停止ボタン]   │
├─────────────────────────────┤
│ [Shimmer] ← scanning中       │
│ ─────────                   │
│ instrument-G100  AA:BB:…  ▮▮▮▮ │  ← RSSI バー (4段階)
│ instrument-G200  AA:BB:…  ▮▮▮░ │
│ instrument-G300  AA:BB:…  ▮▮░░ │
└─────────────────────────────┘
```

- Shimmerは `Modifier.shimmer()` (valentinilk/compose-shimmer ライブラリ)
- RSSI → バー段数:  rssi > -65: 4, > -75: 3, > -85: 2, それ以外: 1
- タップ → `DeviceListViewModel.selectDevice(device)` → Dashboard へ遷移

---

## ConnectionBar コンポーザブル

```kotlin
@Composable
fun ConnectionBar(
    deviceName: String?,
    state: BleConnectionState,
    onScanClick: () -> Unit
)
```

- `Connected`: 緑の丸アイコン + デバイス名
- `Connecting`: アニメーションインジケータ
- `Disconnected`: グレー + 「デバイスをスキャン」ボタン
- `Error`: 赤 + エラーメッセージ

---

## AlarmOverlay コンポーザブル

```kotlin
@Composable
fun AlarmOverlay(
    level: GasLevel,
    ppm: Float,
    onDismiss: () -> Unit
)
```

- CRITICAL 時のみフルスクリーン表示 (Box で覆う)
- 背景: `GasLevelColors[CRITICAL]` alpha=0.95
- 中央:
  - "⚠ 危険レベル検出" (displayMedium)
  - ppm 値 (displayLarge、白)
  - 「確認」ボタン → `onDismiss()`
- `DisposableEffect` で `WindowCompat.getInsetsController` により画面常時点灯
- 赤背景の `InfiniteTransition` で alpha 0.85↔1.0 パルス

---

## 完了条件

- [ ] MockモードでGasGaugeのニードルがリアルタイムに動く
- [ ] 10秒後にCRITICALアラームオーバーレイが表示される
- [ ] DeviceListScreenのShimmerが表示され、タップでDashboardに遷移する
- [ ] `./gradlew :androidApp:assembleDebug` が通る


---
---

# Phase 5 — 多段アラームシステム (音声 + 振動)

求人の「音声再生・振動」スキルを実証する。

---

## AlarmController (androidMain)

### `data/alarm/AlarmController.kt`

```kotlin
interface AlarmController {
    fun trigger(level: GasLevel)
    fun dismiss()
    fun release()
}
```

### `data/alarm/AndroidAlarmController.kt`

**振動パターン**:
```kotlin
// WARNING:  単発100ms
val WARNING_PATTERN  = longArrayOf(0, 100)

// DANGER:   3回パターン (100ms ON / 100ms OFF × 3)
val DANGER_PATTERN   = longArrayOf(0, 100, 100, 100, 100, 100)

// CRITICAL: 連続 (200ms ON / 100ms OFF を無限ループ)
val CRITICAL_PATTERN = longArrayOf(0, 200, 100)   // repeat=0
```

API 26+ は `VibrationEffect.createWaveform()` を使うこと。

**音声**:
- `res/raw/warning_beep.ogg`  → WARNING / DANGER
- `res/raw/critical_alarm.ogg` → CRITICAL (ループ再生)
- ファイルが存在しない場合のフォールバック:
  ```kotlin
  ToneGenerator(AudioManager.STREAM_ALARM, 80)
      .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, durationMs)
  ```

**実装**:
```kotlin
class AndroidAlarmController(private val context: Context) : AlarmController {
    private var mediaPlayer: MediaPlayer? = null
    private val vibrator = context.getSystemService(Vibrator::class.java)

    override fun trigger(level: GasLevel) {
        dismiss()   // 前回のアラームを必ずキャンセル
        when (level) {
            GasLevel.WARNING  -> { vibrate(WARNING_PATTERN, -1);  playSound(R.raw.warning_beep, false) }
            GasLevel.DANGER   -> { vibrate(DANGER_PATTERN,  -1);  playSound(R.raw.warning_beep, false) }
            GasLevel.CRITICAL -> { vibrate(CRITICAL_PATTERN, 0);  playSound(R.raw.critical_alarm, true) }
            GasLevel.SAFE     -> { /* do nothing */ }
        }
    }

    override fun dismiss() {
        vibrator.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun release() = dismiss()
}
```

---

## AlarmUseCase (commonMain)

### `domain/usecase/AlarmUseCase.kt`

```kotlin
class AlarmUseCase(
    private val monitor: MonitorGasUseCase,
    private val controller: AlarmController,
) {
    private var lastAlarmTime = 0L
    private var lastAlarmLevel: GasLevel? = null

    fun observe(): Flow<GasStatus> = monitor()
        .onEach { status ->
            handleAlarm(status)
        }

    fun dismiss() = controller.dismiss()

    private fun handleAlarm(status: GasStatus) {
        if (status.level == GasLevel.SAFE) {
            controller.dismiss(); return
        }
        val now = Clock.System.now().toEpochMilliseconds()
        // 同一レベルのアラームは30秒間再発報しない
        if (status.level == lastAlarmLevel && now - lastAlarmTime < 30_000) return
        controller.trigger(status.level)
        lastAlarmTime  = now
        lastAlarmLevel = status.level
    }
}
```

---

## DI 追加

```kotlin
// androidMain/di/AndroidModule.kt
single<AlarmController> { AndroidAlarmController(androidContext()) }

// commonMain/di/DomainModule.kt
factory { AlarmUseCase(get(), get()) }
```

---

## DashboardViewModel 更新

- `AlarmUseCase` を注入
- `monitorGas()` の代わりに `alarmUseCase.observe()` を collect
- `dismissAlarm()` → `alarmUseCase.dismiss()`

---

## 完了条件

- [ ] Mockの10秒CRITICALタイミングで振動 + 音声が発動する
- [ ] 「確認」ボタンでアラームが停止する
- [ ] 30秒以内に同一レベルのアラームが再発報されないことを確認


---
---

# Phase 6 — GPS ロギング + 地図表示 (OSMDroid)

---

## GpsRepository (commonMain)

### `domain/repository/GpsRepository.kt`

```kotlin
interface GpsRepository {
    fun observeLocation(): Flow<GpsLocation?>
}

data class GpsLocation(val lat: Double, val lng: Double)
```

---

## AndroidGpsSource (androidMain)

### `data/gps/AndroidGpsSource.kt`

```kotlin
class AndroidGpsSource(private val context: Context) {
    fun observeLocation(): Flow<GpsLocation?> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    trySend(GpsLocation(it.latitude, it.longitude))
                }
            }
        }

        // パーミッション未取得なら null を emit して終了
        if (!hasLocationPermission(context)) { trySend(null); close(); return@callbackFlow }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
```

---

## LogRepository (commonMain)

### `domain/repository/LogRepository.kt`

```kotlin
interface LogRepository {
    suspend fun save(reading: GeoTaggedReading): Result<Long>
    fun getAllReadings(): Flow<List<GeoTaggedReading>>
    fun getDangerousReadings(): Flow<List<GeoTaggedReading>>   // DANGER 以上
    suspend fun deleteOlderThan(epochMs: Long): Result<Unit>
    suspend fun exportCsv(): Result<String>   // CSV 文字列を返す
}
```

---

## LogMeasurementUseCase 実装

Phase 3 で骨格だけ作った `LogMeasurementUseCase` を完成させる:

```kotlin
override suspend operator fun invoke(status: GasStatus, manualSave: Boolean): Result<Unit> {
    val location = gpsRepo.observeLocation().first()   // 現在地を1件取得
    val geoReading = GeoTaggedReading(
        reading  = status.reading,
        lat      = location?.lat ?: 0.0,
        lng      = location?.lng ?: 0.0,
        level    = status.level
    )
    return if (status.level >= GasLevel.DANGER || manualSave) {
        logRepo.save(geoReading).map { }
    } else Result.success(Unit)
}
```

---

## HistoryScreen.kt

### タブ構成 (TabRow)

**リストタブ**:
```
[日時]      [ppm]  [レベル]     [緯度,経度]
2025-06-01  380    ⚠CRITICAL   35.123, 139.456
2025-06-01  220    ⚠DANGER     35.124, 139.457
```
- LazyColumn
- GasLevel に応じた先頭カラーバー

**マップタブ**:
- `AndroidView { OSMDroidMapView }` で地図表示
- `GeoTaggedReading` ごとに `Marker` を配置
  - マーカーアイコン色: `GasLevelColors[level]` に対応した円アイコンを `Canvas` で生成
  - タップ → InfoWindow に ppm / 日時を表示
- 初期表示: 全マーカーの重心にカメラを移動

---

## AndroidManifest 追加

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />       <!-- OSMDroid タイル -->
```

---

## 完了条件

- [ ] Historyリストにダミーの GeoTaggedReading が表示される (Phase 7のDB実装前はインメモリ)
- [ ] マップタブでOSMDroidの地図が表示される
- [ ] マーカーをタップすると ppm / 日時のポップアップが出る


---
---

# Phase 7 — SQLDelight 永続化 + CSV 書き出し

---

## SQLDelight スキーマ

### `shared/src/commonMain/sqldelight/com/instrument/db/SensorReadings.sq`

```sql
CREATE TABLE sensor_readings (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    ppm         REAL    NOT NULL,
    temperature REAL    NOT NULL,
    humidity    REAL    NOT NULL,
    lat         REAL    NOT NULL,
    lng         REAL    NOT NULL,
    gas_level   TEXT    NOT NULL,
    timestamp   INTEGER NOT NULL
);

insert:
INSERT INTO sensor_readings (ppm, temperature, humidity, lat, lng, gas_level, timestamp)
VALUES (?, ?, ?, ?, ?, ?, ?);

selectAll:
SELECT * FROM sensor_readings ORDER BY timestamp DESC;

selectDangerous:
SELECT * FROM sensor_readings
WHERE gas_level IN ('DANGER', 'CRITICAL')
ORDER BY timestamp DESC;

deleteOlderThan:
DELETE FROM sensor_readings WHERE timestamp < ?;
```

---

## LogRepositoryImpl (androidMain / iosMain)

### `data/repository/SqlDelightLogRepository.kt`

```kotlin
class SqlDelightLogRepository(private val db: instrumentDatabase) : LogRepository {

    override suspend fun save(reading: GeoTaggedReading): Result<Long> = runCatching {
        db.sensorReadingsQueries.insert(
            ppm         = reading.reading.ppm.toDouble(),
            temperature = reading.reading.temperature.toDouble(),
            humidity    = reading.reading.humidity.toDouble(),
            lat         = reading.lat,
            lng         = reading.lng,
            gas_level   = reading.level.name,
            timestamp   = reading.reading.timestamp
        )
        // SQLDelight は lastInsertRowId() で取得
        db.sensorReadingsQueries.selectAll().executeAsList().first().id
    }

    override fun getAllReadings(): Flow<List<GeoTaggedReading>> =
        db.sensorReadingsQueries.selectAll().asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toGeoTaggedReading() } }

    override suspend fun exportCsv(): Result<String> = runCatching {
        val rows = db.sensorReadingsQueries.selectAll().executeAsList()
        buildString {
            appendLine("timestamp,ppm,temperature,humidity,lat,lng,level")
            rows.forEach { row ->
                appendLine("${row.timestamp},${row.ppm},${row.temperature},${row.humidity},${row.lat},${row.lng},${row.gas_level}")
            }
        }
    }
}

// 拡張関数
private fun Sensor_readings.toGeoTaggedReading() = GeoTaggedReading(
    id      = id,
    reading = SensorReading(ppm.toFloat(), temperature.toFloat(), humidity.toFloat(), timestamp),
    lat     = lat,
    lng     = lng,
    level   = GasLevel.valueOf(gas_level)
)
```

---

## DatabaseDriverFactory (expect/actual)

### `commonMain/data/db/DatabaseDriverFactory.kt`
```kotlin
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

### `androidMain/data/db/DatabaseDriverFactory.android.kt`
```kotlin
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(instrumentDatabase.Schema, context, "instrument.db")
}
```

### `iosMain/data/db/DatabaseDriverFactory.ios.kt`
```kotlin
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(instrumentDatabase.Schema, "instrument.db")
}
```

---

## CSV ファイル書き出し (androidMain)

### `data/export/CsvExporter.kt`

```kotlin
class CsvExporter(private val context: Context) {

    // MediaStore API (API 29+) で Downloads/ に保存
    // API 28以下は File API を使用
    suspend fun export(csvContent: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "instrument_${System.currentTimeMillis()}.csv"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                resolver.openOutputStream(uri)!!.use { it.write(csvContent.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                file.writeText(csvContent)
                Uri.fromFile(file)
            }
        }
    }
}
```

---

## HistoryViewModel 完成

```kotlin
fun exportCsv() {
    viewModelScope.launch {
        _exportState.value = ExportState.Exporting
        logRepo.exportCsv()
            .mapCatching { csv -> exporter.export(csv).getOrThrow() }
            .onSuccess { uri -> _exportState.value = ExportState.Done(uri.toString()) }
            .onFailure { e -> _exportState.value = ExportState.Error(e.message ?: "不明なエラー") }
    }
}
```

---

## HistoryScreen UI 追加

- 「CSVエクスポート」ボタン (TopAppBar に追加)
- `ExportState.Exporting`: ボタンを無効化 + `CircularProgressIndicator`
- `ExportState.Done`: `Snackbar` でファイルパス表示 + 「開く」アクション
  ```kotlin
  // 「開く」アクション
  Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "text/csv")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }.let { context.startActivity(it) }
  ```

---

## 完了条件

- [ ] `./gradlew :shared:generateCommonMaininstrumentDatabaseInterface` が通る
- [ ] Mockで生成されたSensorReadingがDBに保存される
- [ ] HistoryリストにDB保存済みの履歴が表示される
- [ ] CSVエクスポートでDownloadsフォルダにファイルが生成される
