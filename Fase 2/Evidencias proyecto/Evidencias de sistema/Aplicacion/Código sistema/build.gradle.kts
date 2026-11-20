import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sara.robot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sara.robot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Cargar GEMINI_API_KEY y GOOGLE_CLOUD_API_KEY desde .env del proyecto o variables de entorno
        val envFile = rootProject.file(".env")
        val parentEnvFile = rootProject.file("../.env")
        val envProperties = Properties()
        if (parentEnvFile.exists()) {
            try {
                FileInputStream(parentEnvFile).use { envProperties.load(it) }
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (envFile.exists()) {
            try {
                FileInputStream(envFile).use { envProperties.load(it) }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val geminiApiKey = envProperties.getProperty("GEMINI_API_KEY") 
            ?: System.getenv("GEMINI_API_KEY") 
            ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

        val googleCloudApiKey = envProperties.getProperty("GOOGLE_CLOUD_API_KEY")
            ?: envProperties.getProperty("YOUTUBE_API_KEY")
            ?: envProperties.getProperty("GEMINI_API_KEY")
            ?: System.getenv("GOOGLE_CLOUD_API_KEY")
            ?: System.getenv("YOUTUBE_API_KEY")
            ?: ""
        buildConfigField("String", "GOOGLE_CLOUD_API_KEY", "\"$googleCloudApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose (BOM)
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // OkHttp (Gemini Direct HTTP Client según Sprint 1.md)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // AndroidX Security Crypto (EncryptedSharedPreferences con Keystore según Sprint 1.md)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")

    // CameraX Core y View (Iteración 2)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Google ML Kit Face Detection (Iteración 2)
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Detección de Objetos e Inferencia On-Device (Iteración 3)
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Libreria USB Serial para conectar con Arduino (115200 bps)
    implementation("com.github.mik3y:usb-serial-for-android:3.8.0")

    // Base de Datos Local Room (SQLite - Semana 2)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Módulo Multimedia / Reproductor de Música (ExoPlayer Media3 + Retrofit YouTube API)
    val media3Version = "1.2.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}

