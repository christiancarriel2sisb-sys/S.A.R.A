package com.sara.robot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.data.BiometriaRepository
import com.sara.robot.data.RegisteredPersonProfile
import com.sara.robot.vision.DetectedFaceInfo
import com.sara.robot.vision.UnifiedVisionAnalyzer
import com.sara.robot.vision.UnifiedVisionResult
import com.sara.robot.voice.TextToSpeechManager
import com.sara.robot.voice.VoiceInputManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private enum class RegistrationDialogStep {
    NONE,
    CONSENT,
    ENTER_NAME
}

/**
 * Módulo de Reconocimiento y Diferenciación Facial Multirrostro (Perfil Admin).
 * - Sin detección de objetos (100% dedicado a reconocimiento facial fluido a 640x360 px).
 * - Sin nombres pre-asumidos: cualquier rostro desconocido aparece como "Sin registro"
 *   y Sara solicita consentimiento informado antes de pedirle su nombre para registrarlo.
 * - Diferenciación multirrostro: reconoce a múltiples personas simultáneamente en cámara
 *   (asignación exclusiva 1 a 1) y permite seleccionar a qué persona debe dirigirse Sara.
 */
@SuppressLint("RestrictedApi")
@Composable
fun UnifiedVisionScreen(
    onSwitchToRobotFace: () -> Unit,
    onSwitchToReminders: (() -> Unit)? = null,
    onSwitchToMusic: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val biometriaRepo = remember { BiometriaRepository(context) }

    // Al arrancar, asegurar que no existan nombres pre-asumidos ("Christian"/"Juan") de pruebas anteriores
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("sara_biometria_prefs", Context.MODE_PRIVATE)
        biometriaRepo.asegurarInicioSinNombresPreestablecidos(prefs)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            AppLogger.info("UnifiedVision", "Permiso de cámara concedido")
        } else {
            AppLogger.error("UnifiedVision", "Permiso de cámara denegado")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Control de cámara activa y sensor real conectado
    var useFrontCamera by remember { mutableStateOf(true) }
    var isActuallyFrontCamera by remember { mutableStateOf(false) }
    var activeCameraLabel by remember { mutableStateOf("Inicializando...") }

    var visionResult by remember { mutableStateOf(UnifiedVisionResult()) }
    var showLogDialog by remember { mutableStateOf(false) }

    // Lista en tiempo real de personas registradas con vector biométrico en SQLite
    val registeredProfiles by biometriaRepo.observePerfilesRegistrados().collectAsState(initial = emptyList())

    // Persona objetivo hacia la cual Sara debe dirigirse ("Ir a buscar a X")
    var targetPacienteId by remember { mutableStateOf<Long?>(null) }

    // Flujo de Consentimiento Informado + Ingreso de Nombre para rostros "Sin registro"
    var dialogStep by remember { mutableStateOf(RegistrationDialogStep.NONE) }
    var isListeningVoice by remember { mutableStateOf(false) }
    var pendingFaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var inputNombre by remember { mutableStateOf("") }
    var inputApellido by remember { mutableStateOf("") }
    var autoAskEnabled by remember { mutableStateOf(true) }
    val promptedTrackingIds = remember { mutableStateListOf<Int>() }

    val promptConsentimiento = "Hola. Veo que tu rostro no está registrado. Para poder reconocerte cuando me llames, saludarte por tu nombre y acercarme a ayudarte, me gustaría guardar tu rostro en mi memoria de forma privada y segura. ¿Me autorizas a escanear tu rostro?"

    var voiceManager by remember { mutableStateOf<VoiceInputManager?>(null) }

    // TTS: Cuando Sara termina de hablar la pregunta de consentimiento, abre el micrófono
    val ttsManager = remember {
        TextToSpeechManager(context) {
            if (isListeningVoice && dialogStep == RegistrationDialogStep.CONSENT) {
                AppLogger.info("Biometria", "Sara terminó de pedir permiso. Escuchando respuesta ('Sí' / 'No')...")
                voiceManager?.startListening()
            }
        }
    }

    fun detenerEscuchaVoz() {
        isListeningVoice = false
        voiceManager?.stopListening()
    }

    fun pasarAIngresoDeNombre() {
        detenerEscuchaVoz()
        // Capturar el recorte más reciente del rostro sin registro si aún no lo teníamos
        if (pendingFaceBitmap == null) {
            pendingFaceBitmap = visionResult.unregisteredFaces.firstOrNull()?.faceCropBitmap
                ?: visionResult.faces.firstOrNull()?.faceCropBitmap
        }
        inputNombre = ""
        inputApellido = ""
        dialogStep = RegistrationDialogStep.ENTER_NAME
        AppLogger.info("Biometria", "Consentimiento biométrico OTORGADO. Solicitando nombre de la persona para el registro...")
        ttsManager.speak("Muchas gracias. Por favor ingresa tu nombre en pantalla para asociarlo a tu rostro.")
    }

    fun rechazarConsentimiento() {
        detenerEscuchaVoz()
        dialogStep = RegistrationDialogStep.NONE
        pendingFaceBitmap = null
        AppLogger.warn("Biometria", "Consentimiento biométrico DENEGADO por la persona. No se guardará ningún dato.")
        ttsManager.speak("Comprendo perfectamente. No guardaré ninguna información de tu rostro.")
    }

    fun iniciarSolicitudRegistro(rostroSinRegistro: DetectedFaceInfo?) {
        val targetFace = rostroSinRegistro
            ?: visionResult.unregisteredFaces.firstOrNull()
            ?: visionResult.faces.firstOrNull()

        if (targetFace == null) {
            AppLogger.warn("Biometria", "No se detecta ningún rostro en cámara para registrar")
            ttsManager.speak("No veo ningún rostro frente a mí. Por favor ponte frente a la cámara.")
            return
        }

        pendingFaceBitmap = targetFace.faceCropBitmap
        dialogStep = RegistrationDialogStep.CONSENT
        isListeningVoice = true
        AppLogger.info("Biometria", "Rostro 'Sin registro' detectado (TrackID=${targetFace.trackingId}). Solicitando consentimiento informado...")
        ttsManager.speak(promptConsentimiento)
    }

    DisposableEffect(Unit) {
        voiceManager = VoiceInputManager(
            context = context,
            onResultText = { textoReconocido ->
                val lower = textoReconocido.lowercase().trim()
                AppLogger.info("Biometria", "Respuesta por voz capturada: \"$textoReconocido\"")

                if (dialogStep == RegistrationDialogStep.CONSENT) {
                    val esAfirmativo = lower.contains("sí") || lower.contains("si") ||
                            lower.contains("bueno") || lower.contains("claro") ||
                            lower.contains("acepto") || lower.contains("autorizo") ||
                            lower.contains("dale") || lower.contains("ya")

                    val esNegativo = lower.contains("no") || lower.contains("rechazo") ||
                            lower.contains("nunca") || lower.contains("después")

                    if (esAfirmativo) {
                        pasarAIngresoDeNombre()
                    } else if (esNegativo) {
                        rechazarConsentimiento()
                    }
                } else if (dialogStep == RegistrationDialogStep.ENTER_NAME) {
                    // Si el usuario dictó su nombre con el micrófono en el paso 2
                    val partes = textoReconocido.trim().split(" ")
                    if (partes.isNotEmpty()) {
                        inputNombre = partes.first().replaceFirstChar { it.uppercase() }
                        if (partes.size > 1) {
                            inputApellido = partes.drop(1).joinToString(" ").replaceFirstChar { it.uppercase() }
                        }
                    }
                    isListeningVoice = false
                }
            },
            onErrorCountExceeded = {
                isListeningVoice = false
            }
        )
        onDispose {
            voiceManager?.stopListening()
            ttsManager.shutdown()
        }
    }

    val analyzer = remember {
        UnifiedVisionAnalyzer(
            mobileFaceNetHelper = biometriaRepo.mobileFaceNetHelper,
            isFrontCamera = useFrontCamera,
            onFrameProcessed = { res ->
                visionResult = res
            }
        )
    }

    // Sincronizar perfiles registrados en SQLite hacia la RAM del analizador en tiempo real
    LaunchedEffect(registeredProfiles) {
        analyzer.updateRegisteredProfiles(registeredProfiles)
        if (targetPacienteId != null && registeredProfiles.none { it.paciente.Id_paciente == targetPacienteId }) {
            targetPacienteId = null
        }
    }

    // Detección automática de rostro "Sin registro": si aparece una persona desconocida estable por 1.2s, Sara le ofrece registrarse
    LaunchedEffect(visionResult.unregisteredFaces, dialogStep, autoAskEnabled) {
        if (!autoAskEnabled || dialogStep != RegistrationDialogStep.NONE) return@LaunchedEffect
        val unknownFace = visionResult.unregisteredFaces.firstOrNull { it.trackingId !in promptedTrackingIds }
        if (unknownFace != null) {
            delay(1200)
            // Verificar que el rostro sin registro siga frente a la cámara tras 1.2s
            val stillThere = visionResult.unregisteredFaces.firstOrNull { it.trackingId == unknownFace.trackingId }
            if (stillThere != null && dialogStep == RegistrationDialogStep.NONE) {
                promptedTrackingIds.add(stillThere.trackingId)
                iniciarSolicitudRegistro(stillThere)
            }
        }
    }

    // Cálculo de instrucción de navegación hacia la persona objetivo (entre múltiples rostros)
    val targetProfile = remember(registeredProfiles, targetPacienteId) {
        registeredProfiles.firstOrNull { it.paciente.Id_paciente == targetPacienteId }
    }

    val navigationStatusText = remember(visionResult, targetProfile) {
        if (targetProfile != null) {
            val targetName = targetProfile.paciente.Nombre
            val matchedFace = visionResult.faces.firstOrNull { it.pacienteId == targetProfile.paciente.Id_paciente }
            if (matchedFace != null) {
                val dist = matchedFace.distanceMeters
                val angle = matchedFace.angleOffsetDegrees
                when {
                    dist <= 0.75f -> "🎯 LLEGADA A ${targetName.uppercase()} (${"%.1f".format(dist)}m) • DETENERSE"
                    angle < -8f -> "🎯 GIRAR IZQ (${"%.0f".format(angle)}°) HACIA ${targetName.uppercase()} (${"%.1f".format(dist)}m)"
                    angle > 8f -> "🎯 GIRAR DER (+${"%.0f".format(angle)}°) HACIA ${targetName.uppercase()} (${"%.1f".format(dist)}m)"
                    else -> "🎯 AVANZAR HACIA ${targetName.uppercase()} (${"%.1f".format(dist)}m)"
                }
            } else {
                "🔄 BUSCANDO A ${targetName.uppercase()} (ROTAR 360°)"
            }
        } else {
            val total = visionResult.faces.size
            val reg = visionResult.registeredFaces.size
            val unreg = visionResult.unregisteredFaces.size
            "ROSTROS: $total (Reg: $reg | Sin reg: $unreg)"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            key(useFrontCamera) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        val cameraExecutor = Executors.newSingleThreadExecutor()
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder()
                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            val analysisResolutionSelector = ResolutionSelector.Builder()
                                .setAspectRatioStrategy(
                                    AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                                )
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        android.util.Size(640, 360),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setResolutionSelector(analysisResolutionSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor, analyzer)
                                }

                            try {
                                val hasFront = try { cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) } catch (_: Exception) { false }
                                val hasBack = try { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } catch (_: Exception) { false }

                                if (!hasFront && !hasBack) {
                                    activeCameraLabel = "SIN CÁMARA"
                                    AppLogger.warn("UnifiedVision", "No se detectó cámara activa en el sistema (Cámaras: 0)")
                                    return@addListener
                                }

                                val (targetSelector, isFrontBound) = when {
                                    useFrontCamera && hasFront -> {
                                        activeCameraLabel = "FRONTAL"
                                        Pair(CameraSelector.DEFAULT_FRONT_CAMERA, true)
                                    }
                                    !useFrontCamera && hasBack -> {
                                        activeCameraLabel = "TRASERA"
                                        Pair(CameraSelector.DEFAULT_BACK_CAMERA, false)
                                    }
                                    hasFront -> {
                                        activeCameraLabel = "FRONTAL (Fallback)"
                                        Pair(CameraSelector.DEFAULT_FRONT_CAMERA, true)
                                    }
                                    else -> {
                                        activeCameraLabel = "TRASERA (Fallback)"
                                        Pair(CameraSelector.DEFAULT_BACK_CAMERA, false)
                                    }
                                }

                                isActuallyFrontCamera = isFrontBound
                                analyzer.isFrontCamera = isFrontBound

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    targetSelector,
                                    preview,
                                    imageAnalysis
                                )
                                AppLogger.success("UnifiedVision", "Cámara activa: $activeCameraLabel vinculada a 640x360 (Espejo frontal: $isFrontBound)")
                            } catch (e: Exception) {
                                activeCameraLabel = "ERROR CÁMARA"
                                AppLogger.error("UnifiedVision", "Error al vincular cámara: ${e.message}")
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Canvas Overlay Multirrostro: Dibuja y diferencia cada rostro detectado en la escena
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val imgW = visionResult.frameWidth.toFloat()
                val imgH = visionResult.frameHeight.toFloat()

                if (imgW <= 0f || imgH <= 0f) return@Canvas

                val scale = maxOf(canvasW / imgW, canvasH / imgH)
                val offsetX = (canvasW - imgW * scale) / 2f
                val offsetY = (canvasH - imgH * scale) / 2f

                val isMirrored = isActuallyFrontCamera

                for (face in visionResult.faces) {
                    val bbox = face.boundingBox
                    val rawLeft = bbox.left.toFloat()
                    val rawRight = bbox.right.toFloat()
                    val rawTop = bbox.top.toFloat()
                    val rawBottom = bbox.bottom.toFloat()

                    val mappedLeft = if (isMirrored) (imgW - rawRight) * scale + offsetX else rawLeft * scale + offsetX
                    val mappedRight = if (isMirrored) (imgW - rawLeft) * scale + offsetX else rawRight * scale + offsetX
                    val mappedTop = rawTop * scale + offsetY
                    val mappedBottom = rawBottom * scale + offsetY

                    val left = minOf(mappedLeft, mappedRight)
                    val top = minOf(mappedTop, mappedBottom)
                    val width = maxOf(mappedLeft, mappedRight) - left
                    val height = maxOf(mappedTop, mappedBottom) - top

                    val isTarget = (targetPacienteId != null && face.pacienteId == targetPacienteId)

                    // Colores diferenciados:
                    // - Verde Esmeralda si es la persona Objetivo que Sara está buscando
                    // - Celeste Neón si es una persona Registrada reconocida por sus datos biométricos
                    // - Naranja Ámbar si es una persona "Sin registro"
                    val faceColor = when {
                        isTarget -> Color(0xFF2ED573)
                        face.isRegistered -> Color(0xFF00FFCC)
                        else -> Color(0xFFFFA502)
                    }

                    // Fondo tenue del recuadro facial
                    drawRoundRect(
                        color = faceColor.copy(alpha = if (isTarget) 0.14f else 0.08f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )

                    // Borde del cuadro facial
                    drawRoundRect(
                        color = faceColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = if (isTarget) 3.dp.toPx() else 2.dp.toPx())
                    )

                    // Etiqueta sobre el cuadro con el nombre real (o "Sin registro"), distancia y ángulo
                    val prefix = when {
                        isTarget -> "🎯 "
                        face.isRegistered -> "👤 "
                        else -> "⚠️ "
                    }
                    val labelText = "$prefix${face.displayLabel} • ${"%.1f".format(face.distanceMeters)}m (${"%.0f".format(face.angleOffsetDegrees)}°)"

                    val textLayout = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )

                    val padX = 8.dp.toPx()
                    val padY = 4.dp.toPx()
                    val badgeW = textLayout.size.width + padX * 2
                    val badgeH = textLayout.size.height + padY * 2
                    val badgeX = left.coerceIn(8f, (canvasW - badgeW - 8f).coerceAtLeast(8f))
                    val badgeY = if (top - badgeH - 6.dp.toPx() >= 8f) {
                        top - badgeH - 6.dp.toPx()
                    } else {
                        top + 6.dp.toPx()
                    }

                    // Fondo de la placa del nombre
                    drawRoundRect(
                        color = Color(0xF00D0D1A),
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    // Borde de la placa
                    drawRoundRect(
                        color = faceColor,
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    // Texto nítido
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(badgeX + padX, badgeY + padY)
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Esperando permiso de cámara...", color = Color.White, fontSize = 14.sp)
            }
        }

        // --- BARRA SUPERIOR MINIMALISTA Y TRANSLÚCIDA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSwitchToRobotFace,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x88000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        useFrontCamera = !useFrontCamera
                        AppLogger.info("UnifiedVision", "Cambiando cámara a: ${if (useFrontCamera) "FRONTAL" else "TRASERA"}")
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isActuallyFrontCamera) Color(0x9900F0FF) else Color(0x88000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Cambiar Cámara",
                        tint = if (isActuallyFrontCamera) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showLogDialog = true },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x88000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Logs",
                        tint = Color(0xFF2ED573),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onSwitchToReminders != null) {
                    IconButton(
                        onClick = onSwitchToReminders,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Recordatorios",
                            tint = Color(0xFFFFD32A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (onSwitchToMusic != null) {
                    IconButton(
                        onClick = onSwitchToMusic,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Música",
                            tint = Color(0xFFFF4757),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Telemetría Superior: Cámara activa + Estado multirrostro / Navegación hacia persona objetivo
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CAM: $activeCameraLabel",
                        color = Color(0xFF00FFCC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = navigationStatusText,
                        color = if (targetProfile != null) Color(0xFF2ED573) else Color(0xFFFFD32A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- BARRA INFERIOR FLOTANTE COMPACTA: Registro de "Sin registro" y Selector "Ir hacia [Persona]" ---
        Surface(
            color = Color(0xCC0D0D1A),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                .wrapContentWidth()
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón para registrar un rostro "Sin registro"
                Button(
                    onClick = {
                        iniciarSolicitudRegistro(visionResult.unregisteredFaces.firstOrNull())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (visionResult.unregisteredFaces.isNotEmpty()) Color(0xFFFFA502) else Color(0xFF00F0FF)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (visionResult.unregisteredFaces.isNotEmpty()) "REGISTRAR NUEVO" else "REGISTRAR ROSTRO",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Interruptor de auto-invitación a rostros sin registro
                FilterChip(
                    selected = autoAskEnabled,
                    onClick = { autoAskEnabled = !autoAskEnabled },
                    label = {
                        Text(
                            text = if (autoAskEnabled) "Auto-pedir: ON" else "Auto-pedir: OFF",
                            fontSize = 10.sp
                        )
                    },
                    modifier = Modifier.height(30.dp)
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(22.dp)
                        .width(1.dp),
                    color = Color(0x33FFFFFF)
                )

                if (registeredProfiles.isEmpty()) {
                    Text(
                        text = "0 personas registradas en BD",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "🎯 Ir a:",
                        color = Color(0xFF00FFCC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    for (profile in registeredProfiles) {
                        val isSelected = (targetPacienteId == profile.paciente.Id_paciente)
                        val isVisibleNow = visionResult.faces.any { it.pacienteId == profile.paciente.Id_paciente }
                        Surface(
                            color = when {
                                isSelected -> Color(0xFF2ED573)
                                isVisibleNow -> Color(0x3300FFCC)
                                else -> Color(0x22FFFFFF)
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color.White else if (isVisibleNow) Color(0xFF00FFCC) else Color(0x44FFFFFF)
                            ),
                            modifier = Modifier
                                .height(30.dp)
                                .clickable {
                                    if (isSelected) {
                                        targetPacienteId = null
                                        AppLogger.info("Biometria", "Seguimiento de objetivo desactivado")
                                    } else {
                                        targetPacienteId = profile.paciente.Id_paciente
                                        AppLogger.info("Biometria", "Objetivo fijado: Sara buscará e irá hacia '${profile.paciente.Nombre}' (ID=${profile.paciente.Id_paciente})")
                                        ttsManager.speak("Buscando a ${profile.paciente.Nombre} para acercarme.")
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isVisibleNow) Color(0xFF00FFCC) else Color.Gray)
                                )
                                Text(
                                    text = profile.paciente.Nombre,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Eliminar registro de ${profile.paciente.Nombre}",
                                    tint = if (isSelected) Color.Black else Color(0xFFFF4757),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                biometriaRepo.eliminarBiometria(profile.paciente.Id_paciente)
                                                ttsManager.speak("He eliminado el registro biométrico de ${profile.paciente.Nombre}.")
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- DIÁLOGO PASO 1: CONSENTIMIENTO INFORMADO ---
        AnimatedVisibility(
            visible = dialogStep == RegistrationDialogStep.CONSENT,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color(0xF50D0D1A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFA502)),
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 440.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFFFFA502),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "ROSTRO SIN REGISTRO • CONSENTIMIENTO",
                            color = Color(0xFFFFA502),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "\"$promptConsentimiento\"",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (isListeningVoice) {
                        Surface(
                            color = Color(0x332ED573),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF2ED573)
                                )
                                Text(
                                    text = "Sara te está hablando y escuchará tu respuesta ('Sí' o 'No')...",
                                    color = Color(0xFF2ED573),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { rechazarConsentimiento() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4757)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4757)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("NO, RECHAZAR", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { pasarAIngresoDeNombre() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SÍ, AUTORIZO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // --- DIÁLOGO PASO 2: INGRESAR EL NOMBRE DE LA PERSONA ---
        AnimatedVisibility(
            visible = dialogStep == RegistrationDialogStep.ENTER_NAME,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color(0xF50D0D1A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00FFCC)),
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 440.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "¿CÓMO TE LLAMAS?",
                        color = Color(0xFF00FFCC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "Ingresa tu nombre para asociarlo a tu vector biométrico (192-d):",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputNombre,
                            onValueChange = { inputNombre = it },
                            label = { Text("Nombre (ej: Christian)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                isListeningVoice = true
                                voiceManager?.startListening()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isListeningVoice) Color(0xFF2ED573) else Color(0x3300FFCC))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Dictar nombre por voz",
                                tint = if (isListeningVoice) Color.Black else Color(0xFF00FFCC)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputApellido,
                        onValueChange = { inputApellido = it },
                        label = { Text("Apellido (opcional)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00FFCC),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                detenerEscuchaVoz()
                                dialogStep = RegistrationDialogStep.NONE
                                pendingFaceBitmap = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCELAR", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val cleanName = inputNombre.trim()
                                if (cleanName.isBlank()) {
                                    ttsManager.speak("Por favor escribe tu nombre antes de guardar.")
                                    return@Button
                                }

                                val bitmapToUse = pendingFaceBitmap
                                    ?: visionResult.unregisteredFaces.firstOrNull()?.faceCropBitmap
                                    ?: visionResult.faces.firstOrNull()?.faceCropBitmap

                                if (bitmapToUse == null) {
                                    AppLogger.warn("Biometria", "No hay rostro visible en cámara para extraer el vector.")
                                    ttsManager.speak("Por favor mira hacia la cámara un momento y vuelve a presionar guardar.")
                                    return@Button
                                }

                                detenerEscuchaVoz()
                                dialogStep = RegistrationDialogStep.NONE

                                coroutineScope.launch {
                                    val result = biometriaRepo.registrarNuevoPacienteConBiometria(
                                        nombre = cleanName,
                                        apellido = inputApellido.trim(),
                                        faceBitmap = bitmapToUse
                                    )
                                    result.onSuccess { nuevoPac ->
                                        pendingFaceBitmap = null
                                        ttsManager.speak("¡Mucho gusto, ${nuevoPac.Nombre}! He guardado tu rostro en mi memoria.")
                                    }.onFailure { err ->
                                        AppLogger.error("Biometria", "Fallo al registrar persona: ${err.message}")
                                        ttsManager.speak("Hubo un problema al guardar tu rostro. Intenta de nuevo.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GUARDAR REGISTRO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (showLogDialog) {
            LogPanelDialog(onDismiss = { showLogDialog = false })
        }
    }
}
