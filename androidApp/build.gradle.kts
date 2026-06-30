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
    implementation(libs.shimmer)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Compose + Material3 (androidApp UI ファイルに必要)
    implementation("androidx.compose.runtime:runtime:1.7.5")
    implementation("androidx.compose.foundation:foundation:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-graphics:1.7.5")
    implementation("androidx.compose.animation:animation:1.7.5")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.insert-koin:koin-androidx-compose:3.6.0-Beta5")
    implementation(libs.osmdroid)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
