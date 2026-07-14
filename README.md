![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)
![KMP](https://img.shields.io/badge/KMP-Multiplatform-7F52FF)
![Compose](https://img.shields.io/badge/Compose-Multiplatform-4285F4)
![License](https://img.shields.io/badge/license-MIT-green)

# instrument-monitor
> BLE接続の無線計測デバイスをスマートフォンでリアルタイム監視・記録する  
> Kotlin Multiplatform + Jetpack Compose (Compose Multiplatform) ポートフォリオアプリ

![demo](docs/demo.gif)

## 概要

無線計測機器とBLEで接続し、センサーデータ(ガス濃度・温度・湿度)をリアルタイムで可視化するアプリです。  
Mock BLEモードにより実機デバイス不要でデモ可能なため、環境を選ばず動作確認できます。  
Clean Architecture で Domain層をプラットフォーム非依存に設計し、Android / iOS を Kotlin で一本化しています。

## 技術スタック

| カテゴリ | 技術 |
|---------|------|
| 言語 | Kotlin (全層統一) |
| マルチプラットフォーム | Kotlin Multiplatform (KMP) |
| UI | Jetpack Compose / Compose Multiplatform |
| BLE | Kable (KMP対応) |
| DI | Koin (KMP対応) |
| DB | SQLDelight |
| 非同期 | Kotlinx Coroutines + Flow |
| GPS | FusedLocationProviderClient (Android) / CoreLocation (iOS) |
| 地図 | OSMDroid (Android) / MapKit (iOS) |
| グラフ | Vico |

## モジュール構成

```
instrument-monitor/
├── shared/
│   └── src/
│       ├── commonMain/   ← Domain層・UseCase・Repository interface
│       ├── androidMain/  ← BLE・GPS・DB の Android実装
│       └── iosMain/      ← BLE・GPS・DB の iOS実装
├── androidApp/           ← Android エントリポイント
└── iosApp/               ← iOS エントリポイント
```

## アーキテクチャ図

```
[Compose UI] → [ViewModel] → [UseCase] → [Repository interface]
                                               ↓
                           ┌──────────────────────────────┐
                           │ RealBleRepository  (Kable)   │
                           │ MockBleRepository  (sin波)   │
                           └──────────────────────────────┘
        ←─────── commonMain (Domain / UseCase) ───────→
        ←── androidMain / iosMain (Platform impl) ──→
```

## 主要機能

### BLEスキャン & GATT接続

- Kable ライブラリで Android / iOS を共通実装

![device_list](docs/screenshots/device_list.png)

### リアルタイムセンサーゲージ

- Canvas API で描いた半円ゲージ
- 4段階レベルに応じてニードル色・アラーム状態が変化

| SAFE | CRITICAL |
|------|---------|
| ![safe](docs/screenshots/dashboard_safe.png) | ![critical](docs/screenshots/dashboard_critical.png) |

### 多段アラーム (音声 + 振動)

| レベル | 閾値 | 振動 | 音声 |
|--------|------|------|------|
| WARNING | 50 ppm〜 | 単発100ms | 短い警告音 |
| DANGER | 200 ppm〜 | 3回パターン | 警告音 |
| CRITICAL | 350 ppm〜 | 連続 | 緊急アラーム (ループ) |

### GPS位置ロギング + 地図表示

- DANGER以上を GPS座標と紐付けて自動保存
- OSMDroid / MapKit で地図上に可視化

![map](docs/screenshots/history_map.png)

### CSV書き出し

- MediaStore API (API 29+) で Downloads フォルダに書き出し
- フォーマット: `timestamp, ppm, temperature, humidity, lat, lng, level`

## セットアップ

```bash
git clone https://github.com/io0323/instrument-monitor
cd instrument-monitor

# Mockモードでビルド (実機BLEデバイス不要)
./gradlew :androidApp:installDebug

# 実機BLEモードでビルド
./gradlew :androidApp:installDebug -PUSE_MOCK=false
```

動作確認環境:
- Android 8.0 (API 26) 以上
- BLE 対応端末

## Mock BLEモードについて

- `BuildConfig.USE_MOCK = true` (デフォルト) で動作
- CO濃度: `sin(t) × 150 + 150` にランダムノイズを加算 (0〜300 ppm 変動)
- 10秒に1回 ppm=380 (CRITICAL) を一時挿入
- 500ms ごとに Flow へ emit
- 実機デバイスなしでアラーム・GPS記録・CSV書き出しまで全機能を確認可能

## 求人要件との対応

| 理研計器 求人要件 | 実装箇所 |
|----------------|---------|
| BLE通信 | `shared/src/*Main/kotlin/com/instrument/data/ble/` + `*BleRepository.kt` (Kable) |
| 音声再生 | `AndroidAlarmController` (MediaPlayer / ToneGenerator) |
| 振動制御 | `AndroidAlarmController` (VibrationEffect API 26+) |
| GPS機能 | `AndroidGpsSource` (FusedLocationProviderClient) |
| ファイルアクセス | `CsvExporter` (MediaStore API 29+) |
| Android開発 | `androidApp/` モジュール全体 |
| iOS対応 | `iosApp/` + Compose Multiplatform |

## ライセンス

MIT © 2025 io0323
