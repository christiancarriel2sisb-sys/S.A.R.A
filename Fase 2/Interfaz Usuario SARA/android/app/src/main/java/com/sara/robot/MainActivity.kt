package com.sara.robot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.ui.FaceDetectionScreen
import com.sara.robot.ui.MusicScreen
import com.sara.robot.ui.NavigationScreen
import com.sara.robot.ui.ReminderScreen
import com.sara.robot.ui.SaraScreen
import com.sara.robot.ui.SaraViewModel

enum class AppScreen {
    NAVIGATION,  // Iteración 3: Detección de objetos hospitalarios, proyección 3D, modelo Jev y serial (CU-Navegación)
    VISION,      // Iteración 2: Detección facial, CameraX y telemetría de chasis (CU-15 / CU-18)
    ROBOT_FACE,  // Iteración 1: Rostro animado y conversación por voz con Barge-In
    REMINDERS,   // Iteración 8: Gestión de recordatorios clínicos con AlarmManager y Buffer en RAM
    MUSIC        // Módulo Multimedia: Control de música por voz (YouTube API + Media3 ExoPlayer)
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

        AppLogger.info("App", "MainActivity iniciada (onCreate) - Iteración 8 Activa (Recordatorios AlarmManager)")

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

        // Procesar alarma si la app fue despertada por AlarmManager
        handleReminderIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Por defecto se inicia en el módulo de Cara y Voz de Sara
                    var currentScreen by remember { mutableStateOf(AppScreen.ROBOT_FACE) }

                    Box(modifier = Modifier.fillMaxSize()) {
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
                                    onSwitchToNavigation = { currentScreen = AppScreen.NAVIGATION },
                                    onSwitchToReminders = { currentScreen = AppScreen.REMINDERS },
                                    onSwitchToMusic = { currentScreen = AppScreen.MUSIC }
                                )
                            }
                            AppScreen.REMINDERS -> {
                                ReminderScreen(
                                    onSwitchToRobotFace = { currentScreen = AppScreen.ROBOT_FACE },
                                    onSwitchToVision = { currentScreen = AppScreen.VISION },
                                    onSwitchToNavigation = { currentScreen = AppScreen.NAVIGATION },
                                    onTestSaraVoice = { message ->
                                        currentScreen = AppScreen.ROBOT_FACE
                                        viewModel.hablarRecordatorio(message)
                                    }
                                )
                            }
                            AppScreen.MUSIC -> {
                                MusicScreen(
                                    viewModel = viewModel,
                                    onSwitchToRobotFace = { currentScreen = AppScreen.ROBOT_FACE },
                                    onSwitchToVision = { currentScreen = AppScreen.VISION },
                                    onSwitchToNavigation = { currentScreen = AppScreen.NAVIGATION },
                                    onSwitchToReminders = { currentScreen = AppScreen.REMINDERS }
                                )
                            }
                        }

                        // Reproductor Global YouTube en segundo plano (activo en todo el ciclo de vida de la app)
                        GlobalYouTubePlayer(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        val reminderMsg = intent?.getStringExtra("TRIGGER_REMINDER_MESSAGE")
        if (!reminderMsg.isNullOrBlank()) {
            AppLogger.success("MainActivity", "Procesando recordatorio despertado por AlarmManager: '$reminderMsg'")
            viewModel.hablarRecordatorio(reminderMsg)
            intent.removeExtra("TRIGGER_REMINDER_MESSAGE")
        }
    }
}

/**
 * Reproductor persistente de YouTube que mantiene la música en segundo plano
 * sin importar en qué pantalla o módulo (Conversación, Visión, Navegación, etc.)
 * se encuentre el usuario o el robot SARA.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlobalYouTubePlayer(viewModel: SaraViewModel) {
    val reproductorManager = viewModel.reproductorManager
    val playerState by reproductorManager.playerState.collectAsState()

    if (playerState.currentVideoId.isNotBlank()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                val videoId = playerState.currentVideoId
                if (webView.tag != videoId) {
                    webView.tag = videoId
                    if (videoId.isNotBlank()) {
                        val embedHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body, html { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                                    iframe { width:100%; height:100%; border:none; }
                                </style>
                            </head>
                            <body>
                                <iframe 
                                    id="ytplayer"
                                    src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&enablejsapi=1&playsinline=1&controls=0" 
                                    allow="autoplay; encrypted-media" 
                                    allowfullscreen>
                                </iframe>
                            </body>
                            </html>
                        """.trimIndent()
                        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                    } else {
                        webView.loadUrl("about:blank")
                    }
                } else if (videoId.isNotBlank()) {
                    val action = if (playerState.isPlaying) "playVideo" else "pauseVideo"
                    val volume = (playerState.volume * 100).toInt().coerceIn(0, 100)
                    webView.evaluateJavascript(
                        """
                        var f = document.getElementById('ytplayer');
                        if (f && f.contentWindow) {
                            f.contentWindow.postMessage(JSON.stringify({event: 'command', func: '$action', args: ''}), '*');
                            f.contentWindow.postMessage(JSON.stringify({event: 'command', func: 'setVolume', args: [$volume]}), '*');
                        }
                        """.trimIndent(),
                        null
                    )
                }
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
        )
    }
}
