# Guía de Implementación: Detección Facial con CameraX y Google ML Kit en Android (Kotlin)

**Autor:** Christian  
**Módulos del Sistema Sara:** CU-15 (Detección de presencia) y CU-18 (Seguimiento / Orientación de chasis)  
**Objetivo:** Capturar video en tiempo real mediante CameraX, procesar los fotogramas en memoria con Google ML Kit Face Detection, calcular la desviación angular para el chasis y verificar el pipeline en PC con el emulador de Android Studio.

---

## 1. Configuración de Dependencias y Permisos

### 1.1 `app/build.gradle.kts`
Asegúrate de agregar las dependencias requeridas para CameraX y ML Kit Face Detection:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sara.robot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sara.robot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // CameraX Core y View
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Google ML Kit Face Detection (On-Device empaquetado)
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Jetpack Compose y Ciclo de Vida
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Coroutines para procesamiento asíncrono
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

### 1.2 `AndroidManifest.xml`
Declara los permisos y características de hardware necesarios:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Sara Robot"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoTitleBar">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## 2. Lógica del Analizador de Rostros (`FaceAnalyzer.kt`)

Este componente analiza cada frame recibido de `CameraX` sin bloquear el hilo principal. Si detecta un rostro:
1. Extrae el `BoundingBox`.
2. Calcula el centroide del rostro respecto al ancho de la imagen.
3. Determina el ángulo de desviación $\Delta\theta$ en grados ($^\circ$) para enviarlo al chasis motriz (positivo: derecha, negativo: izquierda).
4. **Libera el frame de inmediato (`imageProxy.close()`)** para no consumir memoria y garantizar que ningún video se persista en disco (cumplimiento RF-07.1).

```kotlin
package com.sara.robot.vision

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

data class FaceTrackingData(
    val faceDetected: Boolean,
    val angleOffsetDegrees: Float,
    val boundingBoxWidth: Int,
    val boundingBoxHeight: Int
)

class FaceAnalyzer(
    private val onFaceTracked: (FaceTrackingData) -> Unit
) : ImageAnalysis.Analyzer {

    // Configuración optimizada para detección en tiempo real (Fast / Streaming)
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .build()

    private val detector = FaceDetection.getClient(options)

    // Campo de visión horizontal estimado en cámaras de smartphones convencionales (~65°)
    private val cameraHFOV = 65.0f

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // Tomamos el rostro de mayor tamaño relativo como objetivo prioritario
                    val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    primaryFace?.let { face ->
                        val frameWidth = inputImage.width.toFloat()
                        val faceCenterX = face.boundingBox.centerX().toFloat()

                        // Normalización entre -1.0 (extremo izquierdo) y +1.0 (extremo derecho)
                        val normalizedX = (faceCenterX - (frameWidth / 2f)) / (frameWidth / 2f)

                        // Desviación angular en grados respecto al eje central
                        val angleOffset = normalizedX * (cameraHFOV / 2f)

                        val data = FaceTrackingData(
                            faceDetected = true,
                            angleOffsetDegrees = angleOffset,
                            boundingBoxWidth = face.boundingBox.width(),
                            boundingBoxHeight = face.boundingBox.height()
                        )
                        onFaceTracked(data)
                        Log.d("SaraVision", "Target detectado: Desviación = ${"%.2f".format(angleOffset)}°")
                    }
                } else {
                    onFaceTracked(FaceTrackingData(false, 0f, 0, 0))
                }
            }
            .addOnFailureListener { e ->
                Log.e("SaraVision", "Error analizando frame facial: ${e.message}")
            }
            .addOnCompleteListener {
                // OBLIGATORIO: Liberar el buffer para habilitar el siguiente frame
                imageProxy.close()
            }
    }
}
```

---

## 3. Interfaz de Usuario y Controlador de Cámara (`FaceDetectionScreen.kt`)

Componente en Jetpack Compose que solicita el permiso en tiempo de ejecución, levanta `ProcessCameraProvider` y muestra la previsualización junto a los datos calculados de seguimiento.

```kotlin
package com.sara.robot.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.vision.FaceAnalyzer
import com.sara.robot.vision.FaceTrackingData
import java.util.concurrent.Executors

@Composable
fun FaceDetectionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var trackingData by remember {
        mutableStateOf(FaceTrackingData(faceDetected = false, angleOffsetDegrees = 0f, boundingBoxWidth = 0, boundingBoxHeight = 0))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraExecutor = Executors.newSingleThreadExecutor()
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, FaceAnalyzer { data ->
                                    trackingData = data
                                })
                            }

                        // Usar cámara frontal por defecto para pruebas en PC o robot
                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            Log.e("SaraVision", "Fallo al vincular casos de uso de CameraX", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Panel de estado superpuesto (Telemetría de Visión)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0xCC000000), shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = if (trackingData.faceDetected) "OBJETIVO FIJADO" else "BUSCANDO ROSTRO...",
                    color = if (trackingData.faceDetected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Desviación angular: ${"%.2f".format(trackingData.angleOffsetDegrees)}°",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "Comando motriz sugerido: " + when {
                        !trackingData.faceDetected -> "CMD:PATROL"
                        trackingData.angleOffsetDegrees > 5f -> "CMD:ROTATE_RIGHT,${"%.1f".format(trackingData.angleOffsetDegrees)}"
                        trackingData.angleOffsetDegrees < -5f -> "CMD:ROTATE_LEFT,${"%.1f".format(-trackingData.angleOffsetDegrees)}"
                        else -> "CMD:APPROACH"
                    },
                    color = Color(0xFF03A9F4),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Se requiere permiso de cámara para la visión de Sara.", color = Color.Gray)
            }
        }
    }
}
```

### 2.3 `MainActivity.kt`
Punto de entrada de la actividad:

```kotlin
package com.sara.robot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sara.robot.ui.FaceDetectionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceDetectionScreen()
        }
    }
}
```

---

## 4. Configuración del Emulador de Android Studio para Pruebas en PC

Para validar todo el código sin instalarlo en un teléfono físico todavía:

### Opción A: Usar la Webcam física de tu PC (Recomendada)
1. En Android Studio, ve a **Tools** $\rightarrow$ **Device Manager**.
2. Selecciona tu emulador existente (ej. *Pixel 7 API 34/35*) y haz clic en el ícono de **Editar (Lápiz)**.
3. Haz clic en **Show Advanced Settings** (abajo a la izquierda).
4. Desplázate hasta la sección **Camera**:
   * **Front:** Cambia de `Emulated` a **`Webcam0`**.
   * **Back:** `Webcam0` (o `VirtualScene`).
5. Presiona **Finish** y levanta el emulador con el botón **Play**.
6. Corre la app: al iniciar, la pantalla mostrará tu propio rostro a través de la webcam de tu ordenador y verás actualizarse los grados en el panel inferior.

### Opción B: Usar Escena Virtual (*VirtualScene*) sin Webcam
1. En la configuración del dispositivo virtual, deja **Front Camera** o **Back Camera** en **`VirtualScene`**.
2. Al ejecutar la app, mantén presionada la tecla **`Alt`** (o `Option` en macOS) dentro de la ventana del emulador:
   * Usa el mouse para rotar la vista dentro de la habitación 3D virtual.
   * Usa las teclas **W, A, S, D** para caminar hacia el cuadro que tiene un rostro en la pared.
3. Al encuadrar el póster con la persona, ML Kit disparará la detección y verás cambiar el estado a "OBJETIVO FIJADO".

---

## 5. Criterios de Aceptación del Módulo

| Prueba | Acción | Resultado Esperado |
| :--- | :--- | :--- |
| **Detección continua** | Ponerse frente a la webcam. | El estado cambia a *OBJETIVO FIJADO* en menos de 100 ms y se muestra el cuadro verde. |
| **Cálculo angular** | Moverse hacia la derecha de la cámara. | El ángulo muestra valores positivos y el comando cambia a `CMD:ROTATE_RIGHT`. |
| **Sin retención de datos** | Revisar memoria tras 10 min de uso. | La memoria de la app se mantiene estable sin acumular Bitmaps y ningún archivo MP4/JPG se guarda en almacenamiento local. |