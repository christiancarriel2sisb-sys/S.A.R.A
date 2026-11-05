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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.vision.FaceAnalyzer
import com.sara.robot.vision.FaceTrackingData
import java.util.concurrent.Executors

/**
 * Pantalla de Visión Computacional y Detección Facial (Iteración 2 / CU-15 y CU-18).
 * - Transmisión en tiempo real de CameraX vinculada a ML Kit Face Detection.
 * - Cálculo de ángulo de desviación horizontal y generación de comandos motrices para el chasis de Sara.
 * - Telemetría en pantalla con estado, grados y comando sugerido.
 */
@Composable
fun FaceDetectionScreen(
    onSwitchToRobotFace: (() -> Unit)? = null,
    onSwitchToNavigation: (() -> Unit)? = null
) {
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

    var useFrontCamera by remember { mutableStateOf(true) }
    var showLogDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) {
                AppLogger.success("SaraVision", "Permiso de CÁMARA concedido")
            } else {
                AppLogger.error("SaraVision", "Permiso de CÁMARA denegado por el usuario")
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            AppLogger.info("SaraVision", "Solicitando permiso CAMERA...")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            // Previsualización de CameraX en tiempo real
            key(useFrontCamera) {
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

                            val cameraSelector = if (useFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                AppLogger.info("SaraVision", "CameraX vinculada al ciclo de vida correctamente (Frontal=$useFrontCamera)")
                            } catch (exc: Exception) {
                                AppLogger.error("SaraVision", "Fallo al vincular CameraX: ${exc.message}")
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Barra superior con controles y navegación
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botones para alternar a Cara Robot o Navegación
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSwitchToRobotFace != null) {
                        IconButton(
                            onClick = onSwitchToRobotFace,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x990D0D1A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Modo Cara Robot",
                                tint = Color(0xFF8A7DFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (onSwitchToNavigation != null) {
                        IconButton(
                            onClick = onSwitchToNavigation,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x990D0D1A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Modo Navegación",
                                tint = Color(0xFF2ED573),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Alternar cámara Frontal / Trasera
                    IconButton(
                        onClick = { useFrontCamera = !useFrontCamera },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x990D0D1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Cambiar cámara",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Abrir terminal de logs
                    IconButton(
                        onClick = { showLogDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x990D0D1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Logs",
                            tint = Color(0xFF8A7DFF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Panel de telemetría de visión y chasis superpuesto
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0xDD070712), shape = RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (trackingData.faceDetected) "OBJETIVO FIJADO" else "BUSCANDO ROSTRO...",
                        color = if (trackingData.faceDetected) Color(0xFF2ED573) else Color(0xFFFFA502),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )

                    if (trackingData.faceDetected) {
                        Text(
                            text = "Tamaño: ${trackingData.boundingBoxWidth}x${trackingData.boundingBoxHeight}px",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Desviación angular: ${"%.2f".format(trackingData.angleOffsetDegrees)}°",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                val motorCmd = when {
                    !trackingData.faceDetected -> "CMD:PATROL"
                    trackingData.angleOffsetDegrees > 5f -> "CMD:ROTATE_RIGHT,${"%.1f".format(trackingData.angleOffsetDegrees)}"
                    trackingData.angleOffsetDegrees < -5f -> "CMD:ROTATE_LEFT,${"%.1f".format(-trackingData.angleOffsetDegrees)}"
                    else -> "CMD:APPROACH"
                }

                Text(
                    text = "Comando motriz sugerido: $motorCmd",
                    color = Color(0xFF70A1FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Se requiere permiso de cámara para la visión de Sara.", color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    IconButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF6B5BFF)).padding(8.dp)
                    ) {
                        Text("Conceder Permiso", color = Color.White)
                    }
                }
            }
        }

        // Modal de Logs
        if (showLogDialog) {
            LogPanelDialog(onDismiss = { showLogDialog = false })
        }
    }
}

