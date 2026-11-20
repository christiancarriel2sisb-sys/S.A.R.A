package com.sara.robot.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.robot.core.AppLogger
import com.sara.robot.music.ReproductorManager

/**
 * Pantalla del Módulo de Música y Multimedia para el Robot SARA.
 * Integrada con YouTube Data API v3 y Media3 ExoPlayer (según modulo de musica.md).
 * Orientada a adultos mayores y controlable tanto por voz como por interfaz táctil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: SaraViewModel,
    onSwitchToRobotFace: () -> Unit,
    onSwitchToVision: () -> Unit,
    onSwitchToNavigation: () -> Unit,
    onSwitchToReminders: () -> Unit
) {
    val reproductorManager = viewModel.reproductorManager
    val playerState by reproductorManager.playerState.collectAsState()
    val hasApiKey = viewModel.youTubeMusicRepository.hasApiKey()

    var searchQuery by remember { mutableStateOf("") }
    var showLogDialog by remember { mutableStateOf(false) }

    // Animación de rotación del disco cuando está reproduciendo
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070712))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Barra Superior de Controles y Navegación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Título y Badges
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(
                        onClick = onSwitchToRobotFace,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x990D0D1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Volver al Rostro de Sara",
                            tint = Color(0xFF8A7DFF),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "MÓDULO DE MÚSICA",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Surface(
                                color = Color(0x3300F0FF),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, Color(0xFF00F0FF), RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = "EXOPLAYER MEDIA3",
                                    color = Color(0xFF00F0FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = if (hasApiKey) Color(0x332ED573) else Color(0x33FFA502),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(
                                    1.dp,
                                    if (hasApiKey) Color(0xFF2ED573) else Color(0xFFFFA502),
                                    RoundedCornerShape(6.dp)
                                )
                            ) {
                                Text(
                                    text = if (hasApiKey) "YOUTUBE API ACTIVA" else "CONFIGURAR .ENV",
                                    color = if (hasApiKey) Color(0xFF2ED573) else Color(0xFFFFA502),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Reproducción por voz o táctil • YouTube Data API v3 + Búfer de baja latencia",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Botones de Módulos (Visión, Navegación, Recordatorios, Logs)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onSwitchToVision,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x990D0D1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Módulo Visión",
                            tint = Color(0xFF70A1FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onSwitchToNavigation,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x990D0D1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Módulo Navegación",
                            tint = Color(0xFF2ED573),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onSwitchToReminders,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x990D0D1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Módulo Recordatorios",
                            tint = Color(0xFFFFA502),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showLogDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x990D0D1A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Abrir Terminal de Logs",
                            tint = Color(0xFF8A7DFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Contenido en 2 Columnas (Layout Horizontal para Robot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Columna Izquierda: Reproductor Principal y Controles
                Surface(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    color = Color(0xDD0D0D1A),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338A7DFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Disco de Vinilo animado en el centro del reproductor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF22223A),
                                                Color(0xFF111122),
                                                Color(0xFF070712)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 3.dp,
                                        brush = Brush.sweepGradient(
                                            colors = listOf(
                                                Color(0xFF8A7DFF),
                                                Color(0xFF00F0FF),
                                                Color(0xFF8A7DFF)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .rotate(if (playerState.isPlaying) rotationAngle else 0f),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF8A7DFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Título y Artista
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (playerState.currentTitle.isNotBlank()) playerState.currentTitle else "Sin reproducción activa",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (playerState.currentArtist.isNotBlank()) playerState.currentArtist else "Di: 'Sara, pon boleros' o busca una canción",
                                color = Color(0xFF00F0FF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Botones de Control (Pausa, Play, Stop, Volumen)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Botón Stop
                            IconButton(
                                onClick = { reproductorManager.detener() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FF4757))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Detener",
                                    tint = Color(0xFFFF4757),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Botón Play / Pause Central
                            IconButton(
                                onClick = {
                                    if (playerState.isPlaying) {
                                        reproductorManager.pausar()
                                    } else {
                                        if (playerState.currentTitle.isNotBlank()) {
                                            reproductorManager.reanudar()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF6B5BFF), Color(0xFF00F0FF))
                                        )
                                    )
                            ) {
                                if (playerState.isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = Color.White,
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playerState.isPlaying) "Pausar" else "Reproducir",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            // Control de volumen / Mute
                            IconButton(
                                onClick = {
                                    if (playerState.volume > 0f) reproductorManager.setVolumen(0f)
                                    else reproductorManager.setVolumen(1f)
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x338A7DFF))
                            ) {
                                Icon(
                                    imageVector = if (playerState.volume > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Silenciar",
                                    tint = Color(0xFF8A7DFF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Columna Derecha: Búsqueda y Géneros para el Adulto Mayor
                val rightScrollState = rememberScrollState()
                Surface(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                    color = Color(0xDD0D0D1A),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3300F0FF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rightScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "BÚSQUEDA Y GÉNEROS MUSICALES",
                            color = Color(0xFF00F0FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Barra de Búsqueda Manual
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Buscar canción o artista...", color = Color.Gray, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00F0FF),
                                    unfocusedBorderColor = Color(0x448A7DFF)
                                )
                            )

                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.ejecutarComandoMultimedia("reproducir", searchQuery)
                                        searchQuery = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B5BFF)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White)
                            }
                        }

                        Text(
                            text = "GÉNEROS FAVORITOS PARA EL PACIENTE:",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Lista de Géneros recomendados para Adultos Mayores (sin jazz ni piano preconfigurados)
                        val genres = listOf(
                            Triple("🌹 Boleros del Recuerdo", "boleros del recuerdo", Color(0xFFFF6B81)),
                            Triple("💃 Tangos Clásicos", "tangos clásicos", Color(0xFFFFA502)),
                            Triple("🌿 Instrumental Relajante", "música instrumental relajante", Color(0xFF2ED573)),
                            Triple("🎵 Éxitos del Ayer", "música del recuerdo grandes éxitos", Color(0xFF70A1FF)),
                            Triple("🎶 Baladas de Oro", "baladas de oro en español", Color(0xFF8A7DFF))
                        )

                        genres.forEach { (label, query, accentColor) ->
                            Surface(
                                onClick = {
                                    viewModel.ejecutarComandoMultimedia("reproducir", query)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF141424),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Reproducir",
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Nota informativa sobre la clave en .env
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0x158A7DFF),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338A7DFF))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💡 INTEGRACIÓN CON .ENV:",
                                    color = Color(0xFF8A7DFF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Configura GOOGLE_CLOUD_API_KEY en tu archivo .env para consultas en vivo a YouTube Data API v3.",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Diálogo Flotante de Registro de Logs
        if (showLogDialog) {
            LogPanelDialog(onDismiss = { showLogDialog = false })
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
