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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.ui.AdminDashboardScreen
import com.sara.robot.ui.FamilyChatScreen
import com.sara.robot.ui.JevNeuralStudioScreen
import com.sara.robot.ui.MusicScreen
import com.sara.robot.ui.ReminderScreen
import com.sara.robot.ui.RoleSelectionScreen
import com.sara.robot.ui.SaraScreen
import com.sara.robot.ui.SaraViewModel
import com.sara.robot.ui.UnifiedVisionScreen

enum class UserRole {
    PACIENTE,       // Vista Paciente (Usuario): Solo rostro y habla de Sara (interacción 100% por voz, sin menús ni botones)
    ADMINISTRADOR,  // Vista Administrador: Panel de monitoreo de Pacientes, Familiares, Recordatorios, Música y Control de Sara
    FAMILIAR,       // Vista Familiar: Chat privado de texto con el paciente (sin IA ni conexión a Sara)
    DESARROLLADOR   // Vista Desarrollador (Christian): Acceso total a todos los módulos operativos de Sara + Red Neuronal Jev
}

enum class AppScreen {
    UNIFIED_VISION,    // Reconocimiento y diferenciación facial multirrostro + Biometría MobileFaceNet
    NAVIGATION,        // Navegación y visión
    VISION,            // Detección facial
    ROBOT_FACE,        // Rostro animado y conversación por voz
    REMINDERS,         // Recordatorios clínicos con AlarmManager y Buffer en RAM
    MUSIC,             // Control de música por voz (YouTube API + Media3 ExoPlayer)
    JEV_NEURAL_STUDIO  // Laboratorio de pruebas y entrenamiento de la Red Neuronal de Intenciones Jev
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

        AppLogger.info("App", "MainActivity iniciada (onCreate) - 4 Perfiles y Monitoreo Modular Activos")

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
            val baseDensity = LocalDensity.current
            // Escala global al 82% en dimensiones (dp) y 84% en textos (sp) para que todas las interfaces,
            // cuadros, formularios y tarjetas tengan mayor amplitud visual en pantallas horizontales.
            val compactDensity = remember(baseDensity) {
                Density(
                    density = baseDensity.density * 0.82f,
                    fontScale = baseDensity.fontScale * 0.84f
                )
            }

            CompositionLocalProvider(LocalDensity provides compactDensity) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                    var currentRole by remember { mutableStateOf<UserRole?>(null) }
                    var developerScreen by remember { mutableStateOf(AppScreen.ROBOT_FACE) }

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
                            // VISTA CON 4 BOTONES: USUARIO, ADMIN, FAMILIAR, DESARROLLADOR
                            RoleSelectionScreen(
                                onSelectRole = { selectedRole ->
                                    currentRole = selectedRole
                                    if (selectedRole == UserRole.DESARROLLADOR) {
                                        developerScreen = AppScreen.ROBOT_FACE
                                    }
                                }
                            )
                        } else {
                            // BackHandler nativo: al presionar Atrás vuelve a la selección de perfiles
                            BackHandler {
                                if (currentRole == UserRole.DESARROLLADOR && developerScreen != AppScreen.ROBOT_FACE) {
                                    developerScreen = AppScreen.ROBOT_FACE
                                } else {
                                    currentRole = null
                                }
                            }

                            when (currentRole!!) {
                                UserRole.PACIENTE -> {
                                    // 1. USUARIO: Solo rostro y voz de Sara, sin menús técnicos ni cuadro azul de visión.
                                    SaraScreen(
                                        viewModel = viewModel,
                                        showFaceBoundingBox = false,
                                        onSwitchToVision = null,
                                        onSwitchToNavigation = null,
                                        onSwitchToReminders = null,
                                        onSwitchToMusic = null,
                                        onSwitchToChat = null,
                                        onSwitchToJevStudio = null,
                                        onExitToRoleSelection = null
                                    )
                                }

                                UserRole.ADMINISTRADOR -> {
                                    // 2. ADMINISTRADOR: Panel interactivo con barra lateral (Pacientes, Familiares, Recordatorios, Música, Control de Sara)
                                    AdminDashboardScreen(
                                        viewModel = viewModel,
                                        onExitToRoleSelection = { currentRole = null }
                                    )
                                }

                                UserRole.FAMILIAR -> {
                                    // 3. FAMILIAR: Chat privado de texto directo con el paciente.
                                    FamilyChatScreen(
                                        chatRepository = viewModel.chatRepository,
                                        onBackToRoleSelection = { currentRole = null }
                                    )
                                }

                                UserRole.DESARROLLADOR -> {
                                    // 4. DESARROLLADOR (CHRISTIAN): Todos los módulos operativos de Sara + Laboratorio Red Neuronal Jev
                                    when (developerScreen) {
                                        AppScreen.ROBOT_FACE -> {
                                            // En el módulo de conversación de Sara se desactiva el cuadro azul de visión
                                            // para que no se sobreponga; el testeo facial se realiza en UNIFIED_VISION.
                                            SaraScreen(
                                                viewModel = viewModel,
                                                showFaceBoundingBox = false,
                                                onSwitchToVision = { developerScreen = AppScreen.UNIFIED_VISION },
                                                onSwitchToNavigation = { developerScreen = AppScreen.UNIFIED_VISION },
                                                onSwitchToReminders = { developerScreen = AppScreen.REMINDERS },
                                                onSwitchToMusic = { developerScreen = AppScreen.MUSIC },
                                                onSwitchToChat = null,
                                                onSwitchToJevStudio = { developerScreen = AppScreen.JEV_NEURAL_STUDIO },
                                                onExitToRoleSelection = { currentRole = null }
                                            )
                                        }
                                        AppScreen.UNIFIED_VISION, AppScreen.VISION, AppScreen.NAVIGATION -> {
                                            UnifiedVisionScreen(
                                                onSwitchToRobotFace = { developerScreen = AppScreen.ROBOT_FACE },
                                                onSwitchToReminders = { developerScreen = AppScreen.REMINDERS },
                                                onSwitchToMusic = { developerScreen = AppScreen.MUSIC }
                                            )
                                        }
                                        AppScreen.REMINDERS -> {
                                            ReminderScreen(
                                                onSwitchToRobotFace = { developerScreen = AppScreen.ROBOT_FACE },
                                                onSwitchToVision = { developerScreen = AppScreen.UNIFIED_VISION },
                                                onSwitchToNavigation = { developerScreen = AppScreen.JEV_NEURAL_STUDIO },
                                                onTestSaraVoice = { message ->
                                                    developerScreen = AppScreen.ROBOT_FACE
                                                    viewModel.hablarRecordatorio(message)
                                                },
                                                recordatorioRepository = viewModel.recordatorioRepository
                                            )
                                        }
                                        AppScreen.MUSIC -> {
                                            MusicScreen(
                                                viewModel = viewModel,
                                                onSwitchToRobotFace = { developerScreen = AppScreen.ROBOT_FACE },
                                                onSwitchToVision = { developerScreen = AppScreen.UNIFIED_VISION },
                                                onSwitchToNavigation = { developerScreen = AppScreen.JEV_NEURAL_STUDIO },
                                                onSwitchToReminders = { developerScreen = AppScreen.REMINDERS }
                                            )
                                        }
                                        AppScreen.JEV_NEURAL_STUDIO -> {
                                            JevNeuralStudioScreen(
                                                onSwitchToRobotFace = { developerScreen = AppScreen.ROBOT_FACE },
                                                onSwitchToVision = { developerScreen = AppScreen.UNIFIED_VISION },
                                                onSwitchToReminders = { developerScreen = AppScreen.REMINDERS },
                                                onSwitchToMusic = { developerScreen = AppScreen.MUSIC },
                                                onExecuteInSaraOrchestrator = { phrase ->
                                                    developerScreen = AppScreen.ROBOT_FACE
                                                    viewModel.processUserMessage(phrase)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Reproductor Global YouTube en segundo plano
                        GlobalYouTubePlayer(viewModel = viewModel)
                    }
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
                            f.contentWindow.postMessage('{"event":"command","func":"$action","args":""}', '*');
                            f.contentWindow.postMessage('{"event":"command","func":"setVolume","args":[$volume]}', '*');
                        }
                        """.trimIndent(),
                        null
                    )
                }
            }
        )
    }
}
