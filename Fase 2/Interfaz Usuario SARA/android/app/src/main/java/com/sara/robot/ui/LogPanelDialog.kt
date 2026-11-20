package com.sara.robot.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.robot.core.AppLogger
import com.sara.robot.core.LogEntry
import com.sara.robot.core.LogLevel
import com.sara.robot.core.SaraModule

/**
 * Panel de depuración e inspección de logs en tiempo real con separación por módulo.
 * Permite monitorear ya sea el flujo consolidado o el log independiente de cada módulo de Sara.
 */
@Composable
fun LogPanelDialog(
    onDismiss: () -> Unit
) {
    val allLogs by AppLogger.logs.collectAsState()
    val moduleStatuses by AppLogger.moduleStatuses.collectAsState()
    var selectedModuleFilter by remember { mutableStateOf<SaraModule?>(null) }

    val displayedLogs = remember(allLogs, moduleStatuses, selectedModuleFilter) {
        if (selectedModuleFilter == null) {
            allLogs
        } else {
            moduleStatuses[selectedModuleFilter]?.logs ?: emptyList()
        }
    }

    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(displayedLogs.size, selectedModuleFilter) {
        if (displayedLogs.isNotEmpty()) {
            listState.animateScrollToItem(displayedLogs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F0F1A),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Barra superior del panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Terminal de Procesos y Logs Independientes por Módulo",
                            color = Color(0xFF8A7DFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Mostrando: ${selectedModuleFilter?.displayName ?: "Todos los Módulos"} (${displayedLogs.size} eventos)",
                            color = Color(0xFFA0A0B0),
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (displayedLogs.isNotEmpty()) {
                                    val allLogsText = displayedLogs.joinToString("\n") { entry ->
                                        "[${entry.timestamp}] [${entry.module.shortCode}] [${entry.tag}] ${entry.message}"
                                    }
                                    clipboardManager.setText(AnnotatedString(allLogsText))
                                    Toast.makeText(context, "Logs copiados al portapapeles", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No hay logs para copiar", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar logs",
                                tint = Color(0xFF2ED573)
                            )
                        }

                        IconButton(onClick = { AppLogger.clear() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Limpiar logs",
                                tint = Color(0xFFFFA502)
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar panel",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Pestañas de selección de Log Independiente por Módulo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isAll = (selectedModuleFilter == null)
                    Surface(
                        color = if (isAll) Color(0xFF8A7DFF) else Color(0xFF1A1A2E),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { selectedModuleFilter = null }
                    ) {
                        Text(
                            text = "TODOS (${allLogs.size})",
                            color = if (isAll) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    for (mod in SaraModule.entries) {
                        val isSel = (selectedModuleFilter == mod)
                        val count = moduleStatuses[mod]?.logs?.size ?: 0
                        Surface(
                            color = if (isSel) Color(0xFF00F0FF) else Color(0xFF1A1A2E),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSel) Color.White else Color(0x33FFFFFF)),
                            modifier = Modifier.clickable { selectedModuleFilter = mod }
                        ) {
                            Text(
                                text = "${mod.shortCode} ($count)",
                                color = if (isSel) Color.Black else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF2A2A40))
                Spacer(modifier = Modifier.height(6.dp))

                if (displayedLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay eventos registrados para este módulo todavía...",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayedLogs, key = { it.id }) { entry ->
                            LogRow(
                                entry = entry,
                                onCopy = {
                                    val formatted = "[${entry.timestamp}] [${entry.module.shortCode}] [${entry.tag}] ${entry.message}"
                                    clipboardManager.setText(AnnotatedString(formatted))
                                    Toast.makeText(context, "Log copiado", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(
    entry: LogEntry,
    onCopy: () -> Unit
) {
    val (bgColor, textColor, badgeColor) = when (entry.level) {
        LogLevel.INFO -> Triple(Color(0xFF161625), Color(0xFFE0E0E0), Color(0xFF4A90E2))
        LogLevel.SUCCESS -> Triple(Color(0xFF12281F), Color(0xFF7BED9F), Color(0xFF2ED573))
        LogLevel.WARN -> Triple(Color(0xFF2A2412), Color(0xFFFFD32A), Color(0xFFFFA502))
        LogLevel.ERROR -> Triple(Color(0xFF2C1418), Color(0xFFFF6B81), Color(0xFFFF4757))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(6.dp))
            .clickable { onCopy() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.timestamp,
            color = Color(0xFF808095),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 6.dp)
        )

        Surface(
            color = badgeColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = "${entry.module.shortCode}:${entry.tag}",
                color = badgeColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = entry.message,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copiar log",
            tint = Color(0xFF606075),
            modifier = Modifier
                .size(16.dp)
                .padding(start = 4.dp)
        )
    }
}
