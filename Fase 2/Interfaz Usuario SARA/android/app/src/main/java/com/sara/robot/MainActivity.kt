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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.ui.FaceDetectionScreen
import com.sara.robot.ui.FamilyChatScreen
import com.sara.robot.ui.MusicScreen
import com.sara.robot.ui.NavigationScreen
import com.sara.robot.ui.ReminderScreen
import com.sara.robot.ui.RoleSelectionScreen
import com.sara.robot.ui.SaraScreen
import com.sara.robot.ui.SaraViewModel
import com.sara.robot.ui.UnifiedVisionScreen

enum class UserRole {
    PACIENTE,       // Vista Paciente (Usuario): Solo rostro y habla de Sara (interacción 100% por voz, sin menús ni botones)
    ADMINISTRADOR,  // Vista Administrador: Acceso a todos los módulos técnicos (menos el chat)
    FAMILIAR        // Vista Familiar: Chat privado de texto con el paciente (sin IA ni conexión a Sara)
}

enum class AppScreen {
    UNIFIED_VISION, // Iteración 11: Visión unificada (Objetos + Rostros en cámara frontal + Biometría MobileFaceNet)
    NAVIGATION,   // Iteración 3: Detección de objetos, proyección 3D, modelo Jev y serial
    VISION,       // Iteración 2: Detección facial, CameraX y telemetría
    ROBOT_FACE,   // Iteración 1: Rostro animado y conversación por voz
    REMINDERS,    // Iteración 8: Recordatorios clínicos con AlarmManager y Buffer en RAM
    MUSIC         // Iteración 9: Control de música por voz (YouTube API + Media3 ExoPlayer)
}

class MainActivity : ComponentActivity() {

    private val viewModel: SaraViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

        if (audioGranted) {
            AppLogger.success("Permissions", "Permiso RECORD_AUDIO CONCEDIDO")
        } else {
            AppLogger.warn("Permissions", "Permiso RECORD_AUDIO no concedido")
        }

        if (cameraGranted) {
            AppLogger.success("Permissions", "Permiso CAMERA CONCEDIDO")
        } else {
            AppLogger.warn("Permissions", "Permiso CAMERA no concedido")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLogger.info("App", "MainActivity iniciada (onCreate) - Visión Unificada y Biometría Activa")

        // Forzar orientación horizontal (Landscape) para la interfaz del robot
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Verificar y solicitar permisos nativos esenciales (Audio y Cámara)
        val neededPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.CAMERA)
        }

        if (neededPermissions.isNotEmpty()) {
            AppLogger.info("Permissions", "Solicitando permisos al usuario: ${neededPermissions.joinToString()}")
            requestPermissionsLauncher.launch(neededPermissions.toTypedArray())
        } else {
            AppLogger.success("Permissions", "Todos los permisos esenciales (Audio y Cámara) están concedidos")
        }

        // Procesar alarma si la app fue despertada por AlarmManager
        handleReminderIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentRole by remember { mutableStateOf<UserRole?>(null) }
                    var adminScreen by remember { mutableStateOf(AppScreen.ROBOT_FACE) }

                    // Control de modo paciente y ciclo de escucha
                    LaunchedEffect(currentRole) {
                        val isPatient = (currentRole == UserRole.PACIENTE)
                        viewModel.isPatientModeActive = isPatient
                        if (!isPatient) {
                            viewModel.stopListening()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentRole == null) {
                            // VISTA CON 3 BOTONES: USUARIO, ADMIN, FAMILIAR
                            RoleSelectionScreen(
                                onSelectRole = { selectedRole ->
                                    currentRole = selectedRole
                                    if (selectedRole == UserRole.ADMINISTRADOR) {
                                        adminScreen = AppScreen.ROBOT_FACE
                                    }
                                }
                            )
                        } else {
                            // BackHandler nativo: al presionar Atrás vuelve a la selección de perfiles
                            BackHandler {
                                if (currentRole == UserRole.ADMINISTRADOR && adminScreen != AppScreen.ROBOT_FACE) {
                                    adminScreen = AppScreen.ROBOT_FACE
                                } else {
                                    currentRole = null
                                }
                            }

                            when (currentRole!!) {
                                UserRole.PACIENTE -> {
                                    // USUARIO: No tiene acceso a nada, solo al módulo de Sara. Sara ejecuta todo con comandos de voz.
                                    SaraScreen(
                                        viewModel = viewModel,
                                        onSwitchToVision = null,
                                        onSwitchToNavigation = null,
                                        onSwitchToReminders = null,
                                        onSwitchToMusic = null,
                                        onSwitchToChat = null,
                                        onExitToRoleSelection = null
                                    )
                                }
                                UserRole.FAMILIAR -> {
                                    // FAMILIAR: Se dirige directamente al chat. Sin IA, sin conexión con Sara, solo texto.
                                    FamilyChatScreen(
                                        chatRepository = viewModel.chatRepository,
                                        onBackToRoleSelection = { currentRole = null }
                                    )
                                }
                                UserRole.ADMINISTRADOR -> {
                                    // ADMINISTRADOR: Acceso a todos los módulos, MENOS AL CHAT ("porque el admin no chatea")
                                    when (adminScreen) {
                                        AppScreen.ROBOT_FACE -> {
                                            SaraScreen(
                                                viewModel = viewModel,
                                                onSwitchToVision = { adminScreen = AppScreen.UNIFIED_VISION },
                                                onSwitchToNavigation = { adminScreen = AppScreen.UNIFIED_VISION },
                                                onSwitchToReminders = { adminScreen = AppScreen.REMINDERS },
                                                onSwitchToMusic = { adminScreen = AppScreen.MUSIC },
                                                onSwitchToChat = null, // EL ADMIN NO TIENE CHAT
                                                onExitToRoleSelection = { currentRole = null }
                                            )
                                        }
                                        AppScreen.UNIFIED_VISION, AppScreen.VISION, AppScreen.NAVIGATION -> {
                                            UnifiedVisionScreen(
                                                onSwitchToRobotFace = { adminScreen = AppScreen.ROBOT_FACE },
                                                onSwitchToReminders = { adminScreen = AppScreen.REMINDERS },
                                                onSwitchToMusic = { adminScreen = AppScreen.MUSIC }
                                            )
                                        }
                                        AppScreen.REMINDERS -> {
                                            ReminderScreen(
                                                onSwitchToRobotFace = { adminScreen = AppScreen.ROBOT_FACE },
                                                onSwitchToVision = { adminScreen = AppScreen.VISION },
                                                onSwitchToNavigation = { adminScreen = AppScreen.NAVIGATION },
                                                onTestSaraVoice = { message ->
                                                    adminScreen = AppScreen.ROBOT_FACE
                                                    viewModel.hablarRecordatorio(message)
                                                },
                                                recordatorioRepository = viewModel.recordatorioRepository
                                            )
                                        }
                                        AppScreen.MUSIC -> {
                                            MusicScreen(
                                                viewModel = viewModel,
                                                onSwitchToRobotFace = { adminScreen = AppScreen.ROBOT_FACE },
                                                onSwitchToVision = { adminScreen = AppScreen.VISION },
                                                onSwitchToNavigation = { adminScreen = AppScreen.NAVIGATION },
                                                onSwitchToReminders = { adminScreen = AppScreen.REMINDERS }
                                            )
                                        }
                                    }
                                }
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
