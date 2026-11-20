package com.sara.robot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.robot.data.ChatRepository
import com.sara.robot.data.local.entity.ContactoEmergenciaEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de Chat Privado para la Vista del Familiar.
 *
 * Módulo 100% de texto plano:
 * - NO contiene inteligencia artificial ni comandos de voz.
 * - NO tiene conexión con Sara ni síntesis TTS en este módulo.
 * - El familiar únicamente lee los mensajes que el paciente le dicta a Sara por voz,
 *   y escribe respuestas de texto para enviarlas directamente a la base de datos local.
 */
@Composable
fun FamilyChatScreen(
    chatRepository: ChatRepository,
    onBackToRoleSelection: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val contactos by chatRepository.getContactos().collectAsState(initial = emptyList())
    var paciente by remember { mutableStateOf<com.sara.robot.data.local.entity.PacienteEntity?>(null) }

    var contactoSeleccionado by remember { mutableStateOf<ContactoEmergenciaEntity?>(null) }
    var textoMensaje by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Seleccionar automáticamente el contacto con el mensaje más reciente (que viene primero en la lista)
    LaunchedEffect(contactos) {
        if (contactos.isNotEmpty() && (contactoSeleccionado == null || contactos.none { it.id_contacto == contactoSeleccionado?.id_contacto })) {
            contactoSeleccionado = contactos.first()
        }
    }

    // Actualizar los datos del paciente asociado al familiar seleccionado
    LaunchedEffect(contactoSeleccionado) {
        val pacId = contactoSeleccionado?.Paciente_Id_paciente
        paciente = if (pacId != null) {
            chatRepository.getPacienteById(pacId) ?: chatRepository.getPaciente()
        } else {
            chatRepository.getPaciente()
        }
    }

    val contactoId = contactoSeleccionado?.id_contacto ?: -1L
    val mensajesContacto by remember(contactoId) {
        if (contactoId != -1L) {
            chatRepository.getMensajesPorContacto(contactoId)
        } else {
            flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Auto-scroll al último mensaje cuando entra uno nuevo
    LaunchedEffect(mensajesContacto.size) {
        if (mensajesContacto.isNotEmpty()) {
            listState.animateScrollToItem(mensajesContacto.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070712))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Barra Superior de la Vista Familiar (Limpia, sin IA ni robot)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1B4B))
                            .border(1.dp, Color(0xFF8A7DFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat Familiar",
                            tint = Color(0xFF8A7DFF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "CHAT PRIVADO FAMILIAR",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Surface(
                                color = Color(0x332ED573),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, Color(0xFF2ED573), RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = "100% LOCAL Y PRIVADO",
                                    color = Color(0xFF2ED573),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Paciente: ${paciente?.let { "${it.Nombre} ${it.ape_p}" } ?: "Christian Carriel"} • Mensajería directa de texto",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Botón para volver a la selección de roles
                Button(
                    onClick = onBackToRoleSelection,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF4757)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4757))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Cambiar Perfil",
                        tint = Color(0xFFFF4757),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cambiar Perfil",
                        color = Color(0xFFFF4757),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector Horizontal de Contactos Familiares
            Text(
                text = "SELECCIONA EL FAMILIAR:",
                color = Color(0xFF8A7DFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(contactos) { contacto ->
                    val isSelected = contactoSeleccionado?.id_contacto == contacto.id_contacto
                    Surface(
                        onClick = { contactoSeleccionado = contacto },
                        color = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF0F0E24),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF8A7DFF) else Color(0x338A7DFF)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF8A7DFF) else Color(0xFF2A2850)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contacto.nombre.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = "${contacto.nombre} ${contacto.apellido}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "Tel: ${contacto.numero_tel}",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Área Central del Chat (Burbujas de conversación)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = Color(0xDD0D0D1A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338A7DFF))
            ) {
                if (mensajesContacto.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color(0xFF4A4A6A),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aún no hay mensajes en esta conversación privada.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Escribe un mensaje abajo para enviárselo al paciente.",
                                color = Color(0xFF8A7DFF),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(mensajesContacto) { msg ->
                            val esPaciente = msg.emisor.equals("PACIENTE", ignoreCase = true)
                            val horaStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.fecha_hora))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (esPaciente) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    color = if (esPaciente) Color(0xFF1E1B4B) else Color(0xFF0D253A),
                                    shape = RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (esPaciente) 2.dp else 14.dp,
                                        bottomEnd = if (esPaciente) 14.dp else 2.dp
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (esPaciente) Color(0x668A7DFF) else Color(0x6600F0FF)
                                    ),
                                    modifier = Modifier.widthIn(max = 480.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (esPaciente) Icons.Default.Mic else Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (esPaciente) Color(0xFF8A7DFF) else Color(0xFF00F0FF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (esPaciente) "Paciente (dictado por voz a Sara)" else "Tú (${contactoSeleccionado?.nombre ?: "Familiar"})",
                                                color = if (esPaciente) Color(0xFF8A7DFF) else Color(0xFF00F0FF),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = msg.mensaje,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = horaStr,
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (!esPaciente) {
                                                Icon(
                                                    imageVector = if (msg.leido) Icons.Default.DoneAll else Icons.Default.Done,
                                                    contentDescription = null,
                                                    tint = if (msg.leido) Color(0xFF2ED573) else Color.Gray,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra Inferior de Entrada de Texto para el Familiar (Solo texto plano)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textoMensaje,
                    onValueChange = { textoMensaje = it },
                    placeholder = {
                        Text(
                            text = if (contactoSeleccionado != null) "Escribe un mensaje de texto para el paciente..." else "Selecciona un contacto y escribe...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8A7DFF),
                        unfocusedBorderColor = Color(0x448A7DFF)
                    ),
                    singleLine = true
                )

                // Botón Enviar Mensaje
                Button(
                    onClick = {
                        val cid = contactoSeleccionado?.id_contacto
                        if (cid != null && textoMensaje.isNotBlank()) {
                            val textToSend = textoMensaje.trim()
                            textoMensaje = ""
                            coroutineScope.launch {
                                chatRepository.enviarMensajeDesdeFamiliar(cid, textToSend)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A7DFF)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = textoMensaje.isNotBlank() && contactoSeleccionado != null
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enviar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
