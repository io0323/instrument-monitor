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

- **BLEスキャン & GATT接続** — Kable でAndroid/iOS共通実装
- **リアルタイムCO濃度ゲージ** — Canvas API で半円ゲージ + アニメーション
- **多段アラーム** — WARNING/DANGER/CRITICAL で段階的に音声+振動
- **GPS位置ロギング** — DANGER以上を自動記録 + OSMDroid/MapKit で地図表示
- **CSV書き出し** — MediaStore API (Android) / share_plus (iOS) で書き出し
- **Mockモード** — 実機BLEデバイス不要でデモ可能

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
