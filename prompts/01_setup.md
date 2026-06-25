# Phase 1 — KMP プロジェクト基盤セットアップ

CLAUDE.md を読んだうえで、以下の仕様でプロジェクト雛形を作成してください。

---

## 作成するファイル一覧

### ルート
- `settings.gradle.kts`
- `build.gradle.kts` (ルート)
- `gradle/libs.versions.toml`
- `.gitignore`

### :shared モジュール
- `shared/build.gradle.kts`
- `shared/src/commonMain/kotlin/com/instrument/` 以下のパッケージ構成のみ作成 (空ファイルで可)

### :androidApp モジュール
- `androidApp/build.gradle.kts`
- `androidApp/src/main/AndroidManifest.xml`
- `androidApp/src/main/kotlin/com/instrument/android/MainActivity.kt`
- `androidApp/src/main/kotlin/com/instrument/android/instrumentApp.kt`

### :iosApp
- `iosApp/` ディレクトリと KMPlink の説明コメント付き README

---

## libs.versions.toml 仕様

```toml
[versions]
kotlin                  = "2.0.21"
kmp-compose             = "1.7.0"          # Compose Multiplatform
agp                     = "8.7.3"
koin                    = "3.6.0-Beta5"    # KMP対応版
koin-compose            = "1.2.0-Beta5"
sqldelight              = "2.0.2"
kable                   = "0.35.0"         # KMP BLE
coroutines              = "1.9.0"
serialization           = "1.7.3"
datetime                = "0.6.1"
lifecycle               = "2.8.3"          # KMP対応ViewModel
vico                    = "2.0.0-beta.2"   # Androidグラフ
osmdroid                = "6.1.20"
accompanist-permissions = "0.36.0"

[libraries]
# Compose Multiplatform
compose-runtime         = { module = "org.jetbrains.compose.runtime:runtime",         version.ref = "kmp-compose" }
compose-foundation      = { module = "org.jetbrains.compose.foundation:foundation",   version.ref = "kmp-compose" }
compose-material3       = { module = "org.jetbrains.compose.material3:material3",     version.ref = "kmp-compose" }
compose-ui              = { module = "org.jetbrains.compose.ui:ui",                   version.ref = "kmp-compose" }
# Koin
koin-core               = { module = "io.insert-koin:koin-core",                      version.ref = "koin" }
koin-compose            = { module = "io.insert-koin:koin-compose",                   version.ref = "koin-compose" }
koin-android            = { module = "io.insert-koin:koin-android",                   version.ref = "koin" }
# SQLDelight
sqldelight-runtime      = { module = "app.cash.sqldelight:runtime",                   version.ref = "sqldelight" }
sqldelight-coroutines   = { module = "app.cash.sqldelight:coroutines-extensions",     version.ref = "sqldelight" }
sqldelight-android      = { module = "app.cash.sqldelight:android-driver",            version.ref = "sqldelight" }
sqldelight-native       = { module = "app.cash.sqldelight:native-driver",             version.ref = "sqldelight" }
# Kable (BLE)
kable-core              = { module = "com.juliusdickson:kable-core",                  version.ref = "kable" }
# Coroutines
coroutines-core         = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android      = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
# Serialization
serialization-json      = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
# DateTime
datetime                = { module = "org.jetbrains.kotlinx:kotlinx-datetime",        version.ref = "datetime" }
# Lifecycle ViewModel (KMP対応)
lifecycle-viewmodel     = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
lifecycle-compose       = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
# Android固有
osmdroid                = { module = "org.osmdroid:osmdroid-android",                 version.ref = "osmdroid" }
vico-compose            = { module = "com.patrykandpatrick.vico:compose-m3",          version.ref = "vico" }
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist-permissions" }

[plugins]
kotlin-multiplatform    = { id = "org.jetbrains.kotlin.multiplatform",                version.ref = "kotlin" }
kotlin-serialization    = { id = "org.jetbrains.kotlin.plugin.serialization",         version.ref = "kotlin" }
compose-multiplatform   = { id = "org.jetbrains.compose",                             version.ref = "kmp-compose" }
compose-compiler        = { id = "org.jetbrains.kotlin.plugin.compose",               version.ref = "kotlin" }
android-application     = { id = "com.android.application",                           version.ref = "agp" }
android-library         = { id = "com.android.library",                               version.ref = "agp" }
sqldelight              = { id = "app.cash.sqldelight",                               version.ref = "sqldelight" }
```

---

## shared/build.gradle.kts 仕様

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all { kotlinOptions { jvmTarget = "17" } }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "shared" }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kable.core)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.coroutines.android)
            implementation(libs.sqldelight.android)
            implementation(libs.osmdroid)
            implementation(libs.vico.compose)
            implementation(libs.accompanist.permissions)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
    }
}

sqldelight {
    databases {
        create("instrumentDatabase") {
            packageName.set("com.instrument.db")
        }
    }
}
```

---

## androidApp/build.gradle.kts 仕様

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)   // or kotlin.android
    alias(libs.plugins.compose.compiler)
}

android {
    namespace         = "com.instrument.android"
    compileSdk        = 35
    defaultConfig {
        applicationId = "com.instrument.android"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        buildConfigField("Boolean", "USE_MOCK", "true")  // デフォルトMockモード
    }
    buildFeatures { buildConfig = true; compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

---

## AndroidManifest.xml 必要パーミッション

```xml
<!-- BLE -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<!-- GPS -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<!-- ファイル (CSV出力) -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<!-- OSMDroid タイル -->
<uses-permission android:name="android.permission.INTERNET" />
```

---

## MainActivity.kt 仕様

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            instrumentTheme {
                AppNavGraph()
            }
        }
    }
}
```

---

## instrumentApp.kt 仕様

```kotlin
class instrumentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@instrumentApp)
            modules(appModule)
        }
    }
}
```

---

## AppNavGraph.kt 仕様 (4画面)

```
NavHost:
  "dashboard"    → DashboardScreen
  "deviceList"   → DeviceListScreen
  "alarm"        → AlarmScreen
  "history"      → HistoryScreen
```

---

## 完了条件

- [ ] `./gradlew :shared:build` がエラーなく通る
- [ ] `./gradlew :androidApp:assembleDebug` がエラーなく通る
- [ ] Android Studio でプロジェクトを開いて Sync が成功する
- [ ] `shared/src/commonMain/` 以下にパッケージディレクトリが存在する
