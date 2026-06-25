# instrument Monitor — CLAUDE.md

## プロジェクト概要

計器会社向けポートフォリオアプリ。
BLE接続の無線ガス検知器をスマートフォンで監視・記録するアプリを、
**Kotlin Multiplatform + Jetpack Compose (Compose Multiplatform)** で実装する。

---

## 技術スタック

| カテゴリ | 採用技術 | 理由 |
|---------|---------|------|
| 言語 | Kotlin (全層統一) | Kotlin力を最大アピール |
| マルチプラットフォーム | Kotlin Multiplatform (KMP) | Android/iOS を Kotlin で一本化 |
| UI | Compose Multiplatform | Android/iOS 共通UIをKotlinで記述 |
| BLE | Kable (KMP対応) | expect/actual不要でBLE抽象化 |
| DI | Koin (KMP対応) | Hiltは Android専用のため |
| DB | SQLDelight | KMP対応のSQLite ORM |
| 非同期 | Kotlinx Coroutines + Flow | 全層で統一 |
| シリアライズ | Kotlinx Serialization | KMP対応 |
| 日時 | Kotlinx DateTime | KMP対応 |
| GPS (Android) | FusedLocationProviderClient | androidMain に配置 |
| GPS (iOS) | CoreLocation (expect/actual) | iosMain に配置 |
| 地図 | OSMDroid (Android) / MapKit (iOS) | APIキー不要 |
| グラフ | Vico (Android Compose) | リアルタイム折れ線 |

---

## モジュール構成

```
instrument-monitor/
├── CLAUDE.md                     ← このファイル (Claude Code が最初に読む)
├── prompts/                      ← フェーズ別プロンプト集
│   ├── 01_setup.md
│   ├── 02_ble_kmp.md
│   ├── 03_domain.md
│   ├── 04_android_ui.md
│   ├── 05_alarm.md
│   ├── 06_gps.md
│   ├── 07_db_export.md
│   ├── 08_ios_compose.md
│   └── 09_test_readme.md
│
├── shared/                       ← KMP 共有モジュール
│   └── src/
│       ├── commonMain/kotlin/com/instrument/
│       │   ├── domain/
│       │   │   ├── model/        ← SensorReading, GasDevice, GasStatus ...
│       │   │   ├── usecase/      ← MonitorGasUseCase, ConnectDeviceUseCase ...
│       │   │   └── repository/   ← BleRepository, GpsRepository, LogRepository (interface)
│       │   └── data/
│       │       ├── repository/   ← 各Repositoryの共通実装
│       │       └── mock/         ← MockBleSource (テスト・デモ用)
│       ├── androidMain/kotlin/   ← Android固有実装
│       │   ├── ble/              ← AndroidBleDataSource (BluetoothLeScanner)
│       │   └── gps/              ← AndroidGpsSource (FusedLocationProvider)
│       └── iosMain/kotlin/       ← iOS固有実装
│           ├── ble/              ← IosBleDataSource (CoreBluetooth via Kable)
│           └── gps/              ← IosGpsSource (CoreLocation)
│
├── androidApp/                   ← Android エントリポイント
│   └── src/main/kotlin/com/instrument/android/
│       ├── MainActivity.kt
│       └── di/                   ← AndroidModule.kt (Koin)
│
└── iosApp/                       ← iOS エントリポイント (Xcode プロジェクト)
    └── instrumentApp.swift          ← ComposeUIViewController を呼び出すだけ
```

---

## アーキテクチャ方針

```
[Compose UI] → [ViewModel] → [UseCase] → [Repository interface]
                                               ↓
                                     [DataSource: Real / Mock]
                                     (androidMain / iosMain / commonMain)
```

- **Domain 層 (commonMain)**: プラットフォーム依存ゼロ。ビジネスロジックのみ。
- **Data 層**: Repository は interface を commonMain で定義。実装は expect/actual または Kable で抽象化。
- **Presentation 層**: Compose Multiplatform。ViewModel は `androidx.lifecycle:lifecycle-viewmodel` のKMP対応版を使用。

---

## ドメインモデル (commonMain 確定仕様)

```kotlin
// ガス検知値
data class SensorReading(
    val ppm: Float,
    val temperature: Float,
    val humidity: Float,
    val timestamp: Long
)

// BLEデバイス
data class GasDevice(
    val id: String,        // MACアドレス (Android) / UUID (iOS)
    val name: String,
    val rssi: Int,
    val isConnected: Boolean = false
)

// ガスレベル (閾値はJIS T 8201準拠イメージ)
enum class GasLevel { SAFE, WARNING, DANGER, CRITICAL }
// SAFE:     ppm < 50
// WARNING:  50  ≤ ppm < 200
// DANGER:   200 ≤ ppm < 350
// CRITICAL: ppm ≥ 350

// ガス状態 (UseCase出力)
data class GasStatus(
    val reading: SensorReading,
    val level: GasLevel,
    val trend: Trend        // RISING / STABLE / FALLING
)

// GPS付き記録
data class GeoTaggedReading(
    val reading: SensorReading,
    val lat: Double,
    val lng: Double,
    val level: GasLevel,
    val id: Long = 0
)
```

---

## BLE UUIDs (モック用カスタムサービス)

```kotlin
val SERVICE_UUID  = "0000FFE0-0000-1000-8000-00805F9B34FB"
val CHAR_UUID     = "0000FFE1-0000-1000-8000-00805F9B34FB"
// パケット形式: [ppm_hi, ppm_lo, temp_x10_hi, temp_x10_lo, humidity_x10_hi, humidity_x10_lo]
```

---

## Mock モード

`MockBleSource` は実機・BLEデバイス不要でデモ可能にする。

- CO濃度: `sin(t) * 150 + 150 + Random(-20..20)` (0〜300ppm 変動)
- 10秒に1回、ppm=380 (CRITICAL) を一時的に挿入
- 500ms ごとに Flow へ emit
- `BuildConfig.USE_MOCK` (Android) / コンパイルフラグで切り替え

---

## コーディング規約

- コメント: **日本語**
- ファイルヘッダ: 不要
- sealed class の when: `else` 禁止 (網羅性を保証)
- Flow の collect: ViewModel の `viewModelScope` のみ。UI層は `collectAsStateWithLifecycle`
- エラーハンドリング: `Result<T>` でラップ。例外を UI まで伝播させない
- 命名:
  - UseCase: 動詞 + UseCase (`MonitorGasUseCase`)
  - Repository interface: 名詞 + Repository (`BleRepository`)
  - ViewModel: 画面名 + ViewModel (`DashboardViewModel`)
  - Screen: 画面名 + Screen (`DashboardScreen`)

---

## 開発フェーズ

| フェーズ | 内容 | プロンプトファイル |
|---------|------|-----------------|
| 1 | プロジェクト基盤・KMPセットアップ | `prompts/01_setup.md` |
| 2 | BLE KMP実装 (Kable + Mock) | `prompts/02_ble_kmp.md` |
| 3 | Domain層 UseCase + ViewModel | `prompts/03_domain.md` |
| 4 | Android Dashboard UI (Compose) | `prompts/04_android_ui.md` |
| 5 | 多段アラーム (音声+振動) | `prompts/05_alarm.md` |
| 6 | GPS ロギング + 地図 | `prompts/06_gps.md` |
| 7 | SQLDelight永続化 + CSV書き出し | `prompts/07_db_export.md` |
| 8 | iOS Compose Multiplatform移植 | `prompts/08_ios_compose.md` |
| 9 | テスト + README整備 | `prompts/09_test_readme.md` |

---

## 各フェーズの実行方法

```bash
# Claude Code を起動し、該当フェーズのプロンプトを貼り付ける
# 例: フェーズ2を実行する場合
cat prompts/02_ble_kmp.md | pbcopy
# → Claude Code に貼り付け
```

フェーズは必ず順番に実行すること。
各フェーズ完了後に `git commit` してから次フェーズへ進む。
