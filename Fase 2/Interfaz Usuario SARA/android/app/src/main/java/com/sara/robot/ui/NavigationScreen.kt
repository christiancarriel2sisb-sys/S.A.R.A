package com.sara.robot.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sara.robot.core.AppLogger
import com.sara.robot.navigation.actuator.MotorCommandFormatter
import com.sara.robot.navigation.actuator.SerialDispatcher
import com.sara.robot.navigation.perception.CameraManager
import com.sara.robot.navigation.perception.SpatialProjector
import com.sara.robot.navigation.perception.YoloDetector
import com.sara.robot.navigation.reasoning.JevDecisionEngine
import com.sara.robot.navigation.reasoning.NavigationAction
import com.sara.robot.navigation.reasoning.NavigationState
import com.sara.robot.navigation.reasoning.SafetySupervisor

/**
 * Pantalla de Navegación y Detección de Objetos On-Device (Iteración 3).
 * Módulo de Percepción y Navegación Reactiva en Interiores Hospitalarios:
 * - Inferencia visual INT8 (CameraX @ 320x320, 8-10 FPS).
 * - Estimación espacial monocular 3D (X_rel, Z_dist en metros).
 * - Módulo de decisión probabilística (Modelo Jev) con SafetySupervisor (<0.40m).
 * - Despachador de tramas seriales a 10 Hz ("NAV:<angulo>,<velocidad>\n").
 */
@Composable
fun NavigationScreen(
    onSwitchToRobotFace: () -> Unit,
    onSwitchToFaceTracking: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var navState by remember { mutableStateOf(NavigationState()) }
    var showLogDialog by remember { mutableStateOf(false) }

    val jevEngine = remember { JevDecisionEngine() }
    val spatialProjector = remember { SpatialProjector(320f, 320f) }
    val yoloDetector = remember { YoloDetector(spatialProjector) }
    val serialDispatcher = remember { SerialDispatcher(context, coroutineScope) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (granted) AppLogger.success("Navigation", "Permiso de cámara concedido para navegación")
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            serialDispatcher.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            // 1. Previsualización de CameraX
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraManager = CameraManager(ctx, lifecycleOwner, yoloDetector)

                    cameraManager.startCamera(previewView, useFrontCamera = false) { obstacles, zLeft, zCenter, zRight, infTimeMs ->
                        // Evaluar con modelo Jev
                        val (probabilities, action) = jevEngine.evaluate(
                            zLeft,
                            zCenter,
                            zRight,
                            navState.previousTurnDirection
                        )

                        val isEmergency = SafetySupervisor.isEmergencyStopRequired(zCenter)
                        val motorCmd = MotorCommandFormatter.format(action, zLeft, zRight, isEmergency)

                        // Despachar trama serial a 10 Hz
                        serialDispatcher.postCommand(motorCmd.rawFrame)

                        val turnDirection = when (action) {
                            NavigationAction.GIRAR_IZQ -> -1.0f
                            NavigationAction.GIRAR_DER -> 1.0f
                            else -> 0.0f
                        }

                        navState = NavigationState(
                            zMinIzq = zLeft,
                            zMinCen = zCenter,
                            zMinDer = zRight,
                            previousTurnDirection = turnDirection,
                            probabilities = probabilities,
                            selectedAction = action,
                            isEmergencyStop = isEmergency,
                            targetTurnAngle = motorCmd.turnAngleDegrees,
                            targetPwmSpeed = motorCmd.pwmSpeed,
                            serialCommand = motorCmd.rawFrame,
                            obstacles = obstacles,
                            inferenceTimeMs = infTimeMs
                        )
                    }

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Overlay gráfico con Bounding Boxes y distancias en metros
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                for (obs in navState.obstacles) {
                    val left = obs.bboxLeft * canvasW
                    val top = obs.bboxTop * canvasH
                    val right = obs.bboxRight * canvasW
                    val bottom = obs.bboxBottom * canvasH

                    val boxColor = when {
                        obs.zDistMeters < 0.60f -> Color(0xFFFF4757)
                        obs.zDistMeters < 1.50f -> Color(0xFFFFA502)
                        else -> Color(0xFF2ED573)
                    }

                    // Caja delimitadora
                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // 3. Barra superior con selectores de modo y logs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selector de vistas: Cara Robot / Face Tracking / Navegación
                Row(
                    modifier = Modifier
                        .background(Color(0xD90D0D1A), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onSwitchToRobotFace,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Modo Cara Robot",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onSwitchToFaceTracking,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Modo Seguimiento Facial",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF6B5BFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Modo Navegación Activo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Botón Terminal de Logs
                IconButton(
                    onClick = { showLogDialog = true },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xD90D0D1A))
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Logs",
                        tint = Color(0xFF8A7DFF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 4. Panel de Telemetría y Modelo Jev (Inferior)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Alerta de parada de emergencia (<0.40m)
                AnimatedVisibility(visible = navState.isEmergencyStop) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xEEFF4757), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergencia",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "¡OVERRIDE_STOP! OBSTÁCULO FRONTAL CRÍTICO (< 0.40 m)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Panel principal de telemetría espacial
                Surface(
                    color = Color(0xDD070712),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Fila superior: Sensores sectoriales (Izquierda, Centro, Derecha)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SectorCard("IZQ", navState.zMinIzq)
                            SectorCard("CENTRO", navState.zMinCen, isCenter = true)
                            SectorCard("DER", navState.zMinDer)
                        }

                        HorizontalDivider(color = Color(0xFF24243A), modifier = Modifier.padding(vertical = 8.dp))

                        // Barras de Probabilidades del Modelo Jev
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Modelo Jev (Probabilidades de Acción):",
                                    color = Color(0xFF8A7DFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ProbBar("AVANZAR", navState.probabilities.pAvanzar, Color(0xFF2ED573))
                                ProbBar("GIRAR_IZQ", navState.probabilities.pGirarIzq, Color(0xFF70A1FF))
                                ProbBar("GIRAR_DER", navState.probabilities.pGirarDer, Color(0xFFFFA502))
                                ProbBar("DETENERSE", navState.probabilities.pDetenerse, Color(0xFFFF4757))
                            }

                            // Despacho serial y comando motriz
                            Column(
                                modifier = Modifier.width(180.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "Acción: ${navState.selectedAction}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Inferencia: ${navState.inferenceTimeMs} ms",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF0F0F1A), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "TX: ${navState.serialCommand.trim()}",
                                        color = Color(0xFF2ED573),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Se requiere permiso de cámara para el módulo de navegación.", color = Color.Gray)
            }
        }

        // 5. Modal de logs en vivo
        if (showLogDialog) {
            LogPanelDialog(onDismiss = { showLogDialog = false })
        }
    }
}

@Composable
private fun SectorCard(title: String, distanceMeters: Float, isCenter: Boolean = false) {
    val color = when {
        distanceMeters < 0.60f -> Color(0xFFFF4757)
        distanceMeters < 1.50f -> Color(0xFFFFA502)
        else -> Color(0xFF2ED573)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, color = if (isCenter) Color(0xFF8A7DFF) else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            text = if (distanceMeters >= 5.0f) ">5.0 m" else "${"%.2f".format(distanceMeters)} m",
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProbBar(label: String, probability: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.width(68.dp), fontFamily = FontFamily.Monospace)
        LinearProgressIndicator(
            progress = { probability },
            color = color,
            trackColor = Color(0xFF1E1E2E),
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${(probability * 100).toInt()}%",
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp)
        )
    }
}

