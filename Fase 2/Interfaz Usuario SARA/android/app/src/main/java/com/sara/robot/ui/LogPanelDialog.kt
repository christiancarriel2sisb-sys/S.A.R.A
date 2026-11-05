package com.sara.robot.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.sara.robot.core.LogLevel

/**
 * Panel de depuración e inspección de logs en tiempo real.
 * Permite monitorear paso a paso y copiar logs individuales o completos al portapapeles.
 */
@Composable
fun LogPanelDialog(
    onDismiss: () -> Unit
) {
    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
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
                .padding(vertical = 8.dp),
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
                            text = "Terminal de Procesos y Logs de SARA",
                            color = Color(0xFF8A7DFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Total de eventos: ${logs.size} • Toca un log o el icono para copiar",
                            color = Color(0xFFA0A0B0),
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botón para copiar TODOS los logs juntos
                        IconButton(
                            onClick = {
                                if (logs.isNotEmpty()) {
                                    val allLogsText = logs.joinToString("\n") { entry ->
                                        "[${entry.timestamp}] [${entry.tag}] ${entry.message}"
                                    }
                                    clipboardManager.setText(AnnotatedString(allLogsText))
                                    Toast.makeText(context, "Todos los logs copiados al portapapeles", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No hay logs para copiar", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar todos los logs",
                                tint = Color(0xFF2ED573)
                            )
                        }

                        // Botón limpiar logs
                        IconButton(onClick = { AppLogger.clear() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Limpiar logs",
                                tint = Color(0xFFFFA502)
                            )
                        }

                        // Botón cerrar
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF24243A), modifier = Modifier.padding(vertical = 6.dp))

                // Lista de logs en consola
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay eventos registrados aún...",
                            color = Color.DarkGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF07070D), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        items(logs) { entry ->
                            val color = when (entry.level) {
                                LogLevel.INFO -> Color(0xFF70A1FF)
                                LogLevel.SUCCESS -> Color(0xFF2ED573)
                                LogLevel.WARN -> Color(0xFFFFA502)
                                LogLevel.ERROR -> Color(0xFFFF4757)
                            }

                            val fullLine = "[${entry.timestamp}] [${entry.tag}] ${entry.message}"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(fullLine))
                                        Toast.makeText(context, "Log copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "[${entry.timestamp}]",
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[${entry.tag}]",
                                    color = Color(0xFF8A7DFF),
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = entry.message,
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Icono pequeño de copia rápida por línea
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(fullLine))
                                        Toast.makeText(context, "Log copiado", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar línea",
                                        tint = Color(0xFF6B5BFF),
                                        modifier = Modifier.size(14.dp)
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
