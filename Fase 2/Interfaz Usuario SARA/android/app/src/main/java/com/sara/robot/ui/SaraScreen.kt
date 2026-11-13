package com.sara.robot.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.ui.face.FaceBoundingBoxOverlay
import com.sara.robot.ui.face.FaceComponent
import com.sara.robot.vision.FaceAnalyzer
import com.sara.robot.vision.FaceTrackingData
import java.util.concurrent.Executors

@Composable
fun SaraScreen(
    viewModel: SaraViewModel,
    modifier: Modifier = Modifier,
    onSwitchToVision: (() -> Unit)? = null,
    onSwitchToNavigation: (() -> Unit)? = null,
    onSwitchToReminders: (() -> Unit)? = null,
    onSwitchToMusic: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.reproductorManager.playerState.collectAsState()
    var showLogDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                AppLogger.success("SaraScreen", "Permiso de CÁMARA concedido para detección facial")
            } else {
                AppLogger.warn("SaraScreen", "Permiso de CÁMARA no otorgado; cuadro de detección desactivado")
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var trackingData by remember {
        mutableStateOf(
            FaceTrackingData(
                faceDetected = false,
                angleOffsetDegrees = 0f,
                boundingBoxWidth = 0,
                boundingBoxHeight = 0
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (state.isSpeaking || state.isThinking) {
                    viewModel.interruptSara()
                } else if (state.isListening) {
                    viewModel.stopListening()
                } else {
                    viewModel.startListening()
                }
            }
    ) {
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
                                it.setAnalyzer(cameraExecutor, FaceAnalyzer(isFrontCamera = true) { data ->
                                    trackingData = data
                                })
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            AppLogger.info("SaraScreen", "CameraX vinculada en segundo plano con FaceAnalyzer")
                        } catch (e: Exception) {
                            AppLogger.error("SaraScreen", "Fallo al vincular cámara para detección facial: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.001f)
            )
        }

        FaceComponent(
            state = state.faceState,
            modifier = Modifier.fillMaxSize()
        )

        FaceBoundingBoxOverlay(
            trackingData = trackingData,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onSwitchToVision != null) {
                IconButton(
                    onClick = onSwitchToVision,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x990D0D1A))
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Abrir Seguimiento Facial",
                        tint = Color(0xFF70A1FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (onSwitchToNavigation != null) {
                IconButton(
                    onClick = onSwitchToNavigation,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x990D0D1A))
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Abrir Módulo de Navegación",
                        tint = Color(0xFF2ED573),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onSwitchToReminders != null) {
                IconButton(
                    onClick = onSwitchToReminders,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x990D0D1A))
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Abrir Módulo de Recordatorios",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onSwitchToMusic != null) {
                IconButton(
                    onClick = onSwitchToMusic,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x990D0D1A))
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Abrir Módulo de Música",
                        tint = Color(0xFF8A7DFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Mini Reproductor Flotante en tiempo real si hay música activa
        if (playerState.currentVideoId.isNotBlank() && onSwitchToMusic != null) {
            Surface(
                onClick = onSwitchToMusic,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                color = Color(0xCC0D0D1A),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8A7DFF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "🎵 ${playerState.currentTitle}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                    IconButton(
                        onClick = {
                            if (playerState.isPlaying) {
                                viewModel.reproductorManager.pausar()
                            } else {
                                viewModel.reproductorManager.reanudar()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pausar" else "Reanudar",
                            tint = Color(0xFF00F0FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.reproductorManager.detener() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Detener música",
                            tint = Color(0xFFFF4757),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = { showLogDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0x990D0D1A))
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Abrir Terminal de Logs",
                tint = Color(0xFF8A7DFF),
                modifier = Modifier.size(22.dp)
            )
        }

        if (state.faceState == "IDLE" || state.faceState == "ERROR") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xD905050A),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 32.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "Toca la pantalla para hablar con Sara",
                        color = Color(0xFF8A7DFF),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            val pulseAnim = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by pulseAnim.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            val buttonBg = when {
                state.isListening -> Color(0xFFFF4757).copy(alpha = pulseAlpha)
                state.isThinking -> Color(0xFFFFA502).copy(alpha = pulseAlpha)
                state.isSpeaking -> Color(0xFF2ED573).copy(alpha = pulseAlpha)
                else -> Color(0xFF6B5BFF)
            }

            val statusText = when {
                state.isListening -> "Escuchando tu voz..."
                state.isThinking -> "Sara está pensando..."
                state.isSpeaking -> "Sara está hablando..."
                else -> "Toca para hablar"
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .shadow(elevation = 16.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(buttonBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (state.isSpeaking || state.isThinking) {
                                viewModel.interruptSara()
                            } else if (state.isListening) {
                                viewModel.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isListening -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Micrófono activo",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        state.isThinking -> {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Pensando",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        state.isSpeaking -> {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Hablando",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Activar micrófono",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xB305050A),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }

        if (showLogDialog) {
            LogPanelDialog(
                onDismiss = { showLogDialog = false }
            )
        }
    }
}