package com.sara.robot.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.robot.core.AppLogger
import com.sara.robot.reminders.AlarmScheduler
import com.sara.robot.reminders.InMemoryReminder
import com.sara.robot.reminders.ReminderMemoryBuffer
import com.sara.robot.reminders.ReminderStatus
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de Gestión de Recordatorios en Tiempo Real (Iteración 8).
 * Utiliza LocalDateTime para la configuración precisa de fecha (día, mes, año) y hora,
 * calculando los milisegundos requeridos por el AlarmManager nativo de Android.
 * Los datos se gestionan en un buffer temporal en memoria RAM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(
    onSwitchToRobotFace: () -> Unit,
    onSwitchToVision: () -> Unit,
    onSwitchToNavigation: () -> Unit,
    onTestSaraVoice: (String) -> Unit
) {
    val context = LocalContext.current
    val scheduler = remember { AlarmScheduler(context) }
    val reminders by ReminderMemoryBuffer.remindersFlow.collectAsState()

    var reminderTitle by remember { mutableStateOf("") }
    var showLogDialog by remember { mutableStateOf(false) }

    // Fecha y hora objetivo configuradas con LocalDateTime (por defecto 2 minutos en el futuro)
    var targetDateTime by remember {
        mutableStateOf(
            LocalDateTime.now()
                .plusMinutes(2)
                .withSecond(0)
                .withNano(0)
        )
    }

    // Ticker para actualizar los segundos restantes de las alarmas cada segundo
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTime = System.currentTimeMillis()
        }
    }

    // Funciones auxiliares para desplegar selectores nativos de fecha y hora
    fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                targetDateTime = targetDateTime
                    .withYear(year)
                    .withMonth(month + 1)
                    .withDayOfMonth(dayOfMonth)
            },
            targetDateTime.year,
            targetDateTime.monthValue - 1,
            targetDateTime.dayOfMonth
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000L
        datePickerDialog.show()
    }

    fun showTimePicker() {
        val timePickerDialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                targetDateTime = targetDateTime
                    .withHour(hourOfDay)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)
            },
            targetDateTime.hour,
            targetDateTime.minute,
            true // Modo 24 horas
        )
        timePickerDialog.show()
    }

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
                // Título y Badge de Memoria RAM
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
                                text = "MÓDULO DE RECORDATORIOS",
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
                                    text = "BUFFER EN RAM",
                                    color = Color(0xFF00F0FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "AlarmManager Nativo • Configuración con LocalDateTime (Día, Mes, Hora)",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Botones de Módulos (Visión, Navegación, Logs)
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

            // Contenido Principal Dividido en 2 Columnas (Layout Horizontal para Robot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Columna Izquierda: Formulario de Registro con LocalDateTime
                val formScrollState = rememberScrollState()
                Surface(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    color = Color(0xDD0D0D1A),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338A7DFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .verticalScroll(formScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NUEVA ALARMA TEMPORAL",
                            color = Color(0xFF8A7DFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = reminderTitle,
                            onValueChange = { reminderTitle = it },
                            label = { Text("¿Qué debe recordar Sara?", color = Color.Gray, fontSize = 12.sp) },
                            placeholder = { Text("Ej. Tomar medicamento o control médico", color = Color(0x66FFFFFF), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00F0FF),
                                unfocusedBorderColor = Color(0x448A7DFF),
                                focusedLabelColor = Color(0xFF00F0FF)
                            )
                        )

                        Text(
                            text = "CONFIGURACIÓN DE FECHA Y HORA (LOCALDATETIME):",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Selectores interactivos de Día, Mes y Hora
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Selector de Fecha (Día y Mes)
                            Surface(
                                onClick = { showDatePicker() },
                                modifier = Modifier.weight(1.2f),
                                color = Color(0x228A7DFF),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x558A7DFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Cambiar Fecha",
                                        tint = Color(0xFF8A7DFF),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "FECHA (DÍA Y MES)",
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = targetDateTime.format(DateTimeFormatter.ofPattern("dd 'de' MMM", Locale("es", "ES"))),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Año ${targetDateTime.year} • Tocar para cambiar",
                                            color = Color(0xFF00F0FF),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            // Selector de Hora
                            Surface(
                                onClick = { showTimePicker() },
                                modifier = Modifier.weight(1f),
                                color = Color(0x228A7DFF),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x558A7DFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Cambiar Hora",
                                        tint = Color(0xFF00F0FF),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "HORA",
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = targetDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "24 hrs • Tocar",
                                            color = Color(0xFF00F0FF),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Botones de accesos rápidos para testeo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "+30 seg" to { targetDateTime = LocalDateTime.now().plusSeconds(30).withNano(0) },
                                "+1 min" to { targetDateTime = LocalDateTime.now().plusMinutes(1).withNano(0) },
                                "+5 min" to { targetDateTime = LocalDateTime.now().plusMinutes(5).withNano(0) },
                                "+1 día" to { targetDateTime = targetDateTime.plusDays(1) }
                            ).forEach { (label, action) ->
                                AssistChip(
                                    onClick = action,
                                    label = { Text(label, fontSize = 10.sp, color = Color.White) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x22FFFFFF)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338A7DFF))
                                )
                            }
                        }

                        // Panel de cálculo en tiempo real con LocalDateTime
                        val targetEpochMillis = remember(targetDateTime) {
                            targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        val diffMillis = targetEpochMillis - currentTime
                        val diffSeconds = (diffMillis / 1000).coerceAtLeast(0)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0x1500F0FF),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3300F0FF))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CÁLCULO CON LOCALDATETIME:",
                                        color = Color(0xFF00F0FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (diffMillis > 0) {
                                        Text(
                                            text = if (diffSeconds >= 60) "Faltan ${diffSeconds / 60}m ${diffSeconds % 60}s" else "Faltan ${diffSeconds}s",
                                            color = Color(0xFF2ED573),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "FECHA PASADA",
                                            color = Color(0xFFFF4757),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "Objetivo: ${targetDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Milisegundos AlarmManager: $targetEpochMillis ms",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Botón de Programación con altura fija garantizada
                        Button(
                            onClick = {
                                val calculatedEpoch = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val nowMillis = System.currentTimeMillis()
                                val diff = calculatedEpoch - nowMillis

                                if (diff <= 0) {
                                    Toast.makeText(context, "La fecha y hora deben ser futuras", Toast.LENGTH_SHORT).show()
                                    AppLogger.warn("ReminderScreen", "Intento de programar alarma en el pasado: $targetDateTime")
                                    return@Button
                                }

                                val text = if (reminderTitle.isNotBlank()) reminderTitle.trim() else "Recordatorio médico programado"
                                val secondsUntil = diff / 1000

                                AppLogger.info("ReminderScreen", "1. Solicitud de registro con LocalDateTime: $targetDateTime")
                                AppLogger.info("ReminderScreen", "   -> Día: ${targetDateTime.dayOfMonth}, Mes: ${targetDateTime.monthValue} (${targetDateTime.month.name}), Hora: ${targetDateTime.hour}:${targetDateTime.minute}")
                                AppLogger.info("ReminderScreen", "   -> Milisegundos calculados para AlarmManager: $calculatedEpoch ms (disparo en $secondsUntil seg)")

                                // 1. Guardar en RAM
                                val reminder = ReminderMemoryBuffer.addReminder(text, calculatedEpoch)

                                // 2. Programar en AlarmManager nativo
                                scheduler.schedule(reminder)

                                Toast.makeText(
                                    context,
                                    "Alarma programada para ${targetDateTime.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}",
                                    Toast.LENGTH_SHORT
                                ).show()

                                reminderTitle = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B5BFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AlarmAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PROGRAMAR ALARMA",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Columna Derecha: Lista de Alarmas Activas en el Buffer de RAM
                Surface(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight(),
                    color = Color(0xDD0D0D1A),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3300F0FF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ALARMAS EN BUFFER (${reminders.size})",
                                color = Color(0xFF00F0FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (reminders.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        reminders.forEach { scheduler.cancel(it.id) }
                                        ReminderMemoryBuffer.clearAll()
                                    }
                                ) {
                                    Text("Vaciar RAM", color = Color(0xFFFF4757), fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (reminders.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsNone,
                                        contentDescription = null,
                                        tint = Color.DarkGray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No hay alarmas en el buffer temporal",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Selecciona día, mes y hora para programar",
                                        color = Color(0xFF444466),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(reminders, key = { it.id }) { reminder ->
                                    ReminderCard(
                                        reminder = reminder,
                                        currentTime = currentTime,
                                        onCancel = {
                                            scheduler.cancel(reminder.id)
                                            ReminderMemoryBuffer.cancelReminder(reminder.id)
                                        },
                                        onDelete = {
                                            scheduler.cancel(reminder.id)
                                            ReminderMemoryBuffer.removeReminder(reminder.id)
                                        },
                                        onTestNow = {
                                            onTestSaraVoice(reminder.title)
                                        }
                                    )
                                }
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

@Composable
private fun ReminderCard(
    reminder: InMemoryReminder,
    currentTime: Long,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onTestNow: () -> Unit
) {
    val remainingSeconds = ((reminder.targetTimeMillis - currentTime) / 1000).coerceAtLeast(0)

    val (badgeColor, statusText) = when (reminder.status) {
        ReminderStatus.PROGRAMADA -> {
            if (remainingSeconds > 3600) {
                Color(0xFF00F0FF) to "EN ${remainingSeconds / 3600}H ${(remainingSeconds % 3600) / 60}M"
            } else if (remainingSeconds > 60) {
                Color(0xFF00F0FF) to "EN ${remainingSeconds / 60}M ${remainingSeconds % 60}S"
            } else if (remainingSeconds > 0) {
                Color(0xFF00F0FF) to "EN $remainingSeconds SEG"
            } else {
                Color(0xFF2ED573) to "DISPARANDO..."
            }
        }
        ReminderStatus.DISPARADA -> Color(0xFF2ED573) to "DISPARADA"
        ReminderStatus.CANCELADA -> Color(0xFFFF4757) to "CANCELADA"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF141424),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📅 ${reminder.formattedDate}",
                        color = Color(0xFF8A7DFF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "⏰ ${reminder.formattedTime}",
                        color = Color(0xFF00F0FF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = statusText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = onTestNow,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Probar voz de Sara",
                        tint = Color(0xFF70A1FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (reminder.status == ReminderStatus.PROGRAMADA) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar Alarma",
                            tint = Color(0xFFFF4757),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar de RAM",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
