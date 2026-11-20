package com.sara.robot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
 * Pantalla de Selección de Perfil (4 Vistas).
 *
 * Permite seleccionar entre:
 * 1. Usuario (Paciente): Solo rostro de Sara, interacción 100% por voz sin menús.
 * 2. Administrador: Panel de monitoreo clínico (Pacientes, Familiares, Recordatorios, Música y Control de Sara).
 * 3. Familiar: Chat privado de texto con el paciente (sin IA ni conexión a Sara).
 * 4. Desarrollador: Acceso completo a todos los módulos operativos de Sara + Laboratorio de Red Neuronal Jev.
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
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera Principal
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "S.A.R.A",
                        fontSize = 26.sp,
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
                            text = "SELECCIÓN DE PERFIL (4 ROLES)",
                            color = Color(0xFF00F0FF),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sistema Asistente Robótico de Apoyo • Selecciona el perfil de operación",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Los 4 Perfiles en Fila (Landscape)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. USUARIO (PACIENTE)
                RoleCard(
                    title = "Usuario",
                    badgeText = "PACIENTE",
                    subtitle = "Interacción 100% por voz",
                    description = "Rostro de Sara a pantalla completa. Conversación, música y avisos a familiares hablándole.",
                    icon = Icons.Default.RecordVoiceOver,
                    accentColor = Color(0xFF2ED573),
                    buttonText = "Modo Usuario",
                    onClick = { onSelectRole(UserRole.PACIENTE) },
                    modifier = Modifier.weight(1f)
                )

                // 2. ADMINISTRADOR
                RoleCard(
                    title = "Administrador",
                    badgeText = "GESTIÓN Y MONITOR",
                    subtitle = "Monitoreo de pacientes y robot",
                    description = "Gestión de Pacientes (RUT), Contactos de Emergencia, Recordatorios, Música y Control de Módulos.",
                    icon = Icons.Default.AdminPanelSettings,
                    accentColor = Color(0xFF00F0FF),
                    buttonText = "Panel Admin",
                    onClick = { onSelectRole(UserRole.ADMINISTRADOR) },
                    modifier = Modifier.weight(1f)
                )

                // 3. FAMILIAR
                RoleCard(
                    title = "Familiar",
                    badgeText = "CHAT PRIVADO",
                    subtitle = "Mensajería con el paciente",
                    description = "Chat de texto privado directo con el paciente sin intervención de IA.",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    accentColor = Color(0xFF8A7DFF),
                    buttonText = "Abrir Chat",
                    onClick = { onSelectRole(UserRole.FAMILIAR) },
                    modifier = Modifier.weight(1f)
                )

                // 4. DESARROLLADOR (CHRISTIAN)
                RoleCard(
                    title = "Desarrollador",
                    badgeText = "LAB & TESTING",
                    subtitle = "Todos los módulos + Red Jev",
                    description = "Acceso a todos los módulos operativos de Sara, Biometría Facial y Entrenamiento de Red Neuronal Jev.",
                    icon = Icons.Default.DeveloperMode,
                    accentColor = Color(0xFFFFA502),
                    buttonText = "Modo Desarrollador",
                    onClick = { onSelectRole(UserRole.DESARROLLADOR) },
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
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(18.dp)),
        color = Color(0xFF0D0D1E),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = accentColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )

                Text(
                    text = subtitle,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = description,
                color = Color.LightGray.copy(alpha = 0.85f),
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Text(
                    text = buttonText,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
