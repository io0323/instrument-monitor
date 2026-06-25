# iosApp

## 概要

iOS エントリポイント。Xcode プロジェクトから KMP shared フレームワークをリンクし、
Compose Multiplatform UI を `ComposeUIViewController` 経由で表示する。

## Phase 8 で実装予定

Phase 8 (iOS Compose Multiplatform移植) にて以下を作成:

- `iosApp.xcodeproj` — Xcode プロジェクト
- `iosApp/ContentView.swift` — SwiftUI ラッパー
- `iosApp/iOSApp.swift` — エントリポイント

## KMP フレームワークのリンク方法

### Gradle タスクでフレームワークをビルド

```bash
# シミュレーター向け
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 実機向け (arm64)
./gradlew :shared:linkDebugFrameworkIosArm64
```

### Xcode 側の設定

1. `Build Phases` → `Link Binary With Libraries` に `shared.framework` を追加
2. `Build Settings` → `Framework Search Paths` に `$(SRCROOT)/../shared/build/bin/iosSimulatorArm64/debugFramework` を追加
3. `Build Phases` → `Run Script` に以下を追加:

```bash
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

## Swift から Kotlin/Compose を呼び出す例

```swift
import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Phase 8 で共通 Composable を呼び出す
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```
