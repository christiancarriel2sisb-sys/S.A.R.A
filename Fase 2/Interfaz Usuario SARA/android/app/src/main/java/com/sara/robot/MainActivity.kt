package com.sara.robot

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.ui.FaceDetectionScreen
import com.sara.robot.ui.NavigationScreen
import com.sara.robot.ui.SaraScreen
import com.sara.robot.ui.SaraViewModel

enum class AppScreen {
    NAVIGATION,  // Iteración 3: Detección de objetos hospitalarios, proyección 3D, modelo Jev y serial (CU-Navegación)
    VISION,      // Iteración 2: Detección facial, CameraX y telemetría de chasis (CU-15 / CU-18)
    ROBOT_FACE   // Iteración 1: Rostro animado y conversación por voz con Barge-In
}

class MainActivity : ComponentActivity() {

    private val viewModel: SaraViewModel by viewModels()

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            AppLogger.success("Permissions", "Permiso de audio RECORD_AUDIO CONCEDIDO")
        } else {
            AppLogger.error("Permissions", "Permiso de audio RECORD_AUDIO DENEGADO")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLogger.info("App", "MainActivity iniciada (onCreate) - Iteración 3 Activa (Navegación On-Device)")

        // Forzar orientación horizontal (Landscape) para la interfaz del robot
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Verificar y solicitar permisos de audio nativos
        val currentPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        )
        if (currentPermission != PackageManager.PERMISSION_GRANTED) {
            AppLogger.warn("Permissions", "Solicitando permiso RECORD_AUDIO al usuario...")
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            AppLogger.success("Permissions", "Permiso RECORD_AUDIO concedido")
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Por defecto se inicia en el módulo de Cara y Voz de Sara
                    var currentScreen by remember { mutableStateOf(AppScreen.ROBOT_FACE) }

                    when (currentScreen) {
                        AppScreen.NAVIGATION -> {
                            NavigationScreen(
                                onSwitchToRobotFace = { currentScreen = AppScreen.ROBOT_FACE },
                                onSwitchToFaceTracking = { currentScreen = AppScreen.VISION }
                            )
                        }
                        AppScreen.VISION -> {
                            FaceDetectionScreen(
                                onSwitchToRobotFace = { currentScreen = AppScreen.ROBOT_FACE },
                                onSwitchToNavigation = { currentScreen = AppScreen.NAVIGATION }
                            )
                        }
                        AppScreen.ROBOT_FACE -> {
                            SaraScreen(
                                viewModel = viewModel,
                                onSwitchToVision = { currentScreen = AppScreen.VISION },
                                onSwitchToNavigation = { currentScreen = AppScreen.NAVIGATION }
                            )
                        }
                    }
                }
            }
        }
    }
}