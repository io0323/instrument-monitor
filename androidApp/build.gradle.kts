plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace  = "com.instrument.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.instrument.android"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        // デフォルトはMockモード (実機BLEデバイス不要)
        buildConfigField("Boolean", "USE_MOCK", "true")
    }

    buildFeatures {
        buildConfig = true
        compose     = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.koin.android)
    implementation(libs.coroutines.android)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
}
