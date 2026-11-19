package com.sara.robot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.robot.UserRole

/**
 * Pantalla de Selección de Perfil (3 Vistas).
 *
 * Permite seleccionar entre:
 * 1. Usuario (Paciente): Solo rostro de Sara, interacción 100% por voz sin menús.
 * 2. Administrador: Acceso a todos los módulos técnicos (menos chat).
 * 3. Familiar: Chat privado de texto con el paciente (sin IA ni conexión a Sara).
 */
@Composable
fun RoleSelectionScreen(
    onSelectRole: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070712),
                        Color(0xFF0F0E24),
                        Color(0xFF070712)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera Principal
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "S.A.R.A",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.sp
                    )
                    Surface(
                        color = Color(0x3300F0FF),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, Color(0xFF00F0FF), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "SELECCIÓN DE PERFIL",
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sistema Asistente Robótico de Apoyo • Selecciona el modo de uso para comenzar",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Los 3 Botones / Tarjetas Principales en Fila (Landscape)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. USUARIO (PACIENTE)
                RoleCard(
                    title = "Usuario",
                    badgeText = "PACIENTE",
                    subtitle = "Interacción 100% por voz con Sara",
                    description = "Sin botones ni menús técnicos. Sara responde, reproduce música y envía mensajes a la familia solo hablándole.",
                    icon = Icons.Default.RecordVoiceOver,
                    accentColor = Color(0xFF2ED573),
                    buttonText = "Ingresar como Usuario",
                    onClick = { onSelectRole(UserRole.PACIENTE) },
                    modifier = Modifier.weight(1f)
                )

                // 2. ADMINISTRADOR
                RoleCard(
                    title = "Administrador",
                    badgeText = "ACCESO TOTAL",
                    subtitle = "Control técnico de todos los módulos",
                    description = "Supervisión de Visión HUD, Navegación Jev (Arduino), Recordatorios Clínicos, Música y Terminal de Logs.",
                    icon = Icons.Default.AdminPanelSettings,
                    accentColor = Color(0xFF00F0FF),
                    buttonText = "Ingresar como Admin",
                    onClick = { onSelectRole(UserRole.ADMINISTRADOR) },
                    modifier = Modifier.weight(1f)
                )

                // 3. FAMILIAR
                RoleCard(
                    title = "Familiar",
                    badgeText = "CHAT PRIVADO",
                    subtitle = "Comunicación directa con el paciente",
                    description = "Chat de texto privado. Lee los mensajes que el paciente le dicta a Sara y responde escribiendo (sin IA ni voz).",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accentColor = Color(0xFF8A7DFF),
                    buttonText = "Ingresar al Chat",
                    onClick = { onSelectRole(UserRole.FAMILIAR) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Pie de página discreto
            Text(
                text = "Duoc UC • SARA Robot Asistencial 2026",
                color = Color(0xFF4A4A6A),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    badgeText: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp)),
        color = Color(0xFF0D0D1E),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono e indicador superior
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )

                Text(
                    text = subtitle,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            // Descripción central
            Text(
                text = description,
                color = Color.LightGray.copy(alpha = 0.8f),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Botón de acción inferior
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = buttonText,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
