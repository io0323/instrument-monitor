# Phase 8 — iOS Compose Multiplatform 移植

Phase 4〜7 で作った Android UI を Compose Multiplatform に昇格させ、
iOS でも動作するようにする。

---

## 方針

Compose Multiplatform では `commonMain` に UI を配置することで
Android / iOS 双方で動作する。

既存の Android 向け Composable を以下のルールで移植する:

| Android 専用 API | Compose Multiplatform での代替 |
|---|---|
| `AndroidView { OSMDroidMapView }` | iOS: `UIKitView { MKMapView }` に expect/actual |
| `Vico LineChart` | `commonMain` に移動せず Android のみ対応 (iOS はテキスト表示でフォールバック) |
| `MediaPlayer` / `Vibrator` | Phase 5 の `AlarmController` expect/actual 済み |

---

## ファイル移動・昇格

### commonMain へ移動するファイル

以下は Android 専用 API を使っていないため `commonMain` に移動する:

- `GasGauge.kt` — `Canvas` は Compose Multiplatform 対応済み
- `ConnectionBar.kt`
- `AlarmOverlay.kt`
- `TrendIndicator.kt`
- `StatusCard.kt`
- `DashboardScreen.kt` (MapView 部分を除く)
- `DeviceListScreen.kt`
- `instrumentTheme.kt` — Material3 は Compose Multiplatform 対応済み
- `AppNavGraph.kt` — Navigation Compose は Compose Multiplatform 対応済み

### androidMain に残すファイル

- `RealtimeChart.kt` (Vico は Android のみ)
- `HistoryScreen.kt` の MapView 部分

---

## 地図の expect/actual 化

### `commonMain/presentation/components/instrumentMap.kt`

```kotlin
@Composable
expect fun instrumentMap(
    readings: List<GeoTaggedReading>,
    modifier: Modifier = Modifier
)
```

### `androidMain/presentation/components/instrumentMap.android.kt`

```kotlin
@Composable
actual fun instrumentMap(readings: List<GeoTaggedReading>, modifier: Modifier) {
    // 既存の OSMDroid AndroidView 実装をそのまま使う
    AndroidView(
        factory = { ctx -> MapView(ctx).apply { /* OSMDroid 設定 */ } },
        update  = { mapView -> /* マーカー更新 */ },
        modifier = modifier
    )
}
```

### `iosMain/presentation/components/instrumentMap.ios.kt`

```kotlin
@Composable
actual fun instrumentMap(readings: List<GeoTaggedReading>, modifier: Modifier) {
    UIKitView(
        factory = {
            MKMapView().apply {
                // readings を MKPointAnnotation に変換してaddAnnotation
            }
        },
        modifier = modifier
    )
}
```

---

## iOS エントリポイント

### `iosApp/instrumentApp.swift`

```swift
import SwiftUI
import shared

@main
struct instrumentApp: App {
    init() {
        // Koin 初期化
        KoinHelperKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### `iosApp/ContentView.swift`

```swift
import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}

// Compose Multiplatform の MainViewController を UIKit でラップ
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

### `commonMain/MainViewController.kt`

```kotlin
// iOS から呼び出されるエントリポイント
fun MainViewController() = ComposeUIViewController {
    instrumentTheme {
        AppNavGraph()
    }
}
```

---

## KoinHelper (commonMain → iOS から初期化)

```kotlin
// commonMain/di/KoinHelper.kt
fun initKoin() {
    startKoin {
        modules(bleModule, domainModule, viewModelModule, dataModule)
    }
}
```

```kotlin
// iosMain/di/IosKoinInitializer.kt  (iOS Objective-C ブリッジ用)
fun doInitKoin() = initKoin()
```

---

## iOS BLE パーミッション (Info.plist)

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>ガス検知デバイスとのBLE通信に使用します</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>センサーデータの位置情報記録に使用します</string>
```

---

## 完了条件

- [ ] `./gradlew :shared:iosArm64Test` が通る
- [ ] iOS Simulator で DashboardScreen が表示される
- [ ] iOS Simulator で GasGauge のニードルがアニメーションする
- [ ] iOS Simulator で AlarmOverlay が CRITICAL 時に表示される
- [ ] Android / iOS で同じ UI が表示されるスクリーンショットを撮影できる


---
---

# Phase 9 — テスト + README 整備

---

## Unit Tests (commonTest)

### `shared/src/commonTest/kotlin/com/instrument/domain/usecase/MonitorGasUseCaseTest.kt`

```kotlin
class MonitorGasUseCaseTest {

    private val mockSource = MockBleSource()
    private val mockRepo   = MockBleRepository(mockSource)
    private val useCase    = MonitorGasUseCase(mockRepo)

    @Test
    fun `ppm 30 is SAFE`() = runTest {
        // MockBleSource に固定値を返すオーバーライドを追加してテスト
    }

    @Test
    fun `ppm 100 is WARNING`() = runTest { }

    @Test
    fun `ppm 250 is DANGER`() = runTest { }

    @Test
    fun `ppm 380 is CRITICAL`() = runTest { }

    @Test
    fun `trend is RISING when ppm increases`() = runTest {
        // 直近5件を [100, 110, 120, 130, 150] にして RISING を確認
    }

    @Test
    fun `trend is FALLING when ppm decreases`() = runTest { }

    @Test
    fun `same alarm level not re-triggered within 30 seconds`() = runTest { }
}
```

**テスト用 MockBleSource のオーバーライド**:
`MockBleSource` に `fixedPpm: Float?` パラメータを追加し、
非 null の場合はその値だけを emit するように修正する。

---

### `shared/src/commonTest/kotlin/com/instrument/domain/usecase/AlarmUseCaseTest.kt`

```kotlin
class AlarmUseCaseTest {

    @Test
    fun `dismiss is called when level is SAFE`() = runTest { }

    @Test
    fun `trigger is NOT called for same level within 30 seconds`() = runTest { }

    @Test
    fun `trigger IS called when level escalates`() = runTest {
        // WARNING → CRITICAL への昇格は30秒以内でも発報
    }
}
```

---

## Instrumented Tests (androidTest / Compose)

### `androidApp/src/androidTest/kotlin/DashboardScreenTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SAFE状態で緑ゲージが表示される`() {
        composeTestRule.setContent {
            instrumentTheme {
                GasGauge(ppm = 30f, level = GasLevel.SAFE)
            }
        }
        composeTestRule.onNodeWithText("30").assertIsDisplayed()
        composeTestRule.onNodeWithText("SAFE").assertIsDisplayed()
    }

    @Test
    fun `CRITICAL状態でアラームオーバーレイが表示される`() {
        composeTestRule.setContent {
            instrumentTheme {
                AlarmOverlay(level = GasLevel.CRITICAL, ppm = 380f, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("危険レベル検出").assertIsDisplayed()
        composeTestRule.onNodeWithText("380").assertIsDisplayed()
        composeTestRule.onNodeWithText("確認").assertIsDisplayed()
    }
}
```

---

## README.md

`README.md` をプロジェクトルートに作成する。

```markdown
# instrument Monitor

> BLE接続ガス検知器のスマートフォン管理アプリ  
> Kotlin Multiplatform + Jetpack Compose (Compose Multiplatform) で実装したポートフォリオ作品

## デモ (Mock BLEモード — 実機不要)

| ダッシュボード | アラーム発報 | 履歴マップ |
|---|---|---|
| ![dashboard](docs/screenshots/dashboard.gif) | ![alarm](docs/screenshots/alarm.png) | ![map](docs/screenshots/map.png) |

## 技術スタック

| カテゴリ | Android | iOS |
|---------|---------|-----|
| 言語 | Kotlin | Kotlin (KMP) |
| UI | Jetpack Compose | Compose Multiplatform |
| BLE | Kable (KMP) | Kable (KMP) |
| 状態管理 | ViewModel + StateFlow | ViewModel + StateFlow |
| DI | Koin | Koin |
| DB | SQLDelight (Android Driver) | SQLDelight (Native Driver) |
| 地図 | OSMDroid | MapKit (UIKitView) |

## アーキテクチャ

```
[Compose UI] → [ViewModel] → [UseCase] → [Repository]
                                              ↓
                             ┌────────────────┴────────────────┐
                             │ Real (Kable BLE)  │ Mock (sin波) │
                             └───────────────────┴─────────────┘
     共通 (commonMain)                     プラットフォーム固有
     domain / data interfaces              androidMain / iosMain
```

## 主要機能

- 🔵 **BLEスキャン & GATT接続** — Kable でAndroid/iOS共通実装
- 📊 **リアルタイムCO濃度ゲージ** — Canvas API で半円ゲージ + アニメーション
- 🔔 **多段アラーム** — WARNING/DANGER/CRITICAL で段階的に音声+振動
- 📍 **GPS位置ロギング** — DANGER以上を自動記録 + OSMDroid/MapKit で地図表示
- 📁 **CSV書き出し** — MediaStore API (Android) / share_plus (iOS) で書き出し
- 🃏 **Mockモード** — 実機BLEデバイス不要でデモ可能

## セットアップ

```bash
git clone https://github.com/io0323/instrument-monitor
cd instrument-monitor

# Mockモードでビルド (デフォルト)
./gradlew :androidApp:installDebug

# 実機BLEモードでビルド
./gradlew :androidApp:installDebug -PUSE_MOCK=false
```

## 求人対応スキルマッピング

| 理研計器求人要件 | 実装 |
|---------------|------|
| BLE通信 | `shared/data/ble/` (Kable) |
| 音声再生 | `AlarmController` (MediaPlayer / ToneGenerator) |
| 振動制御 | `AlarmController` (VibrationEffect API 26+) |
| GPS機能 | `AndroidGpsSource` (FusedLocationProviderClient) |
| ファイルアクセス | `CsvExporter` (MediaStore API 29+ / File API) |
| Android Studio | androidApp モジュール全体 |
| iOS対応 | iosApp + Compose Multiplatform |
| Clean Architecture | shared/domain, shared/data の分離設計 |
```

---

## スクリーンショット撮影チェックリスト

```
□ Dashboard: SAFE状態 (緑ゲージ、ppm=30)
□ Dashboard: WARNING状態 (黄ゲージ、ppm=100)
□ Dashboard: CRITICAL状態 (赤アラームオーバーレイ、ppm=380)
□ DeviceList: スキャン中 (Shimmerアニメーション)
□ History: リスト (複数レベルのエントリ)
□ History: マップ (複数色マーカー)
□ CSV書き出し完了 Snackbar
□ iOS Simulator: DashboardとHistoryのスクリーンショット
```

---

## 完了条件

- [ ] `./gradlew :shared:commonTest` が全テストグリーン
- [ ] `./gradlew :androidApp:connectedAndroidTest` が全テストグリーン
- [ ] README の表がレンダリング正常
- [ ] GitHub に push して Actions が通る
- [ ] GitHub リポジトリの Description: "KMP + Compose Multiplatform で実装したBLEガス検知器モニタリングアプリ (理研計器ポートフォリオ)"
