package com.sara.robot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaraGeminiScreen(viewModel: SaraViewModel) {
    val state by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll al recibir mensajes
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sara Asistente Virtual", style = MaterialTheme.typography.titleMedium)
                        val statusText = when {
                            state.isListening -> "🎙️ Escuchando..."
                            state.isThinking -> "🧠 Procesando respuesta..."
                            state.isSpeaking -> "🔊 Sara está hablando..."
                            else -> "🟢 Sara lista"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkConnection() }) {
                        when (val status = state.connectionStatus) {
                            is ConnectionStatus.Checking -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            is ConnectionStatus.Connected -> Text("⚡${status.latencyMs}ms", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            is ConnectionStatus.Error -> Text("❌", fontSize = 13.sp)
                            else -> Text("📡", fontSize = 16.sp)
                        }
                    }
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurar API Key")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Banner de advertencia si no hay API key configurada
            if (!state.hasApiKey) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Se requiere configurar la API Key de Gemini en Ajustes.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { showApiKeyDialog = true }) {
                            Text("Ajustes")
                        }
                    }
                }
            }

            // Lista de mensajes (historial conversacional)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.messages) { message ->
                    val isUser = message.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isUser) 16.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 16.dp
                                    )
                                )
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = message.text,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Barra inferior con botón de micrófono y campo de texto
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón de Voz
                    IconButton(
                        onClick = {
                            if (state.isListening) {
                                viewModel.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (state.isListening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = if (state.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (state.isListening) "Detener Escucha" else "Hablar a Sara",
                            tint = if (state.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Entrada manual de texto (respaldo / accesibilidad)
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe o habla a Sara...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val text = inputText
                                inputText = ""
                                viewModel.processUserMessage(text)
                            }
                        },
                        enabled = inputText.isNotBlank() && !state.isThinking
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar mensaje")
                    }
                }
            }
        }
    }

    // Diálogo de Fallback (CU-1: cuando fallan 2 intentos consecutivos de voz)
    if (state.showFallbackMenu) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFallbackMenu() },
            title = { Text("¿Dificultades de audio?") },
            text = {
                Text("No hemos logrado reconocer tu voz con claridad. Puedes intentar hablar de nuevo o escribir tu consulta directamente en el teclado.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissFallbackMenu()
                    viewModel.startListening()
                }) {
                    Text("Reintentar Micrófono")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFallbackMenu() }) {
                    Text("Usar Teclado")
                }
            }
        )
    }

    // Diálogo seguro de API Key
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Configuración Segura de API Key") },
            text = {
                Column {
                    Text(
                        "La clave se guardará cifrada en el dispositivo mediante EncryptedSharedPreferences y Keystore (AES-256).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("Gemini API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempApiKey.isNotBlank()) {
                            viewModel.setApiKey(tempApiKey)
                            tempApiKey = ""
                            showApiKeyDialog = false
                        }
                    }
                ) {
                    Text("Guardar Seguro")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

