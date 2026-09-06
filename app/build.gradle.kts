plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.musicdownloader.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.musicdownloader.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Значення підставляються з GitHub Actions secrets через
            // -P параметри Gradle (див. app/build.yml) - у репозиторії
            // ніяких паролів не зберігається.
            storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // NewPipeExtractor - дістає дані з YouTube (пошук, посилання на аудіо-
    // потоки) без офіційного API, так само як робить застосунок NewPipe.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.4")

    // NewPipeExtractor сам не робить HTTP-запити - він лише парсить дані,
    // а робити самі запити треба нашим власним "Downloader" на базі OkHttp.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ExoPlayer (Media3) - офіційний плеєр Google, правильно обробляє
    // потокове аудіо з усіма заголовками/форматами. Саме те, чого не
    // вистачало нестабільному ft.Audio у Flet-версії.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Для запуску пошуку у фоновому потоці, не блокуючи інтерфейс
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
