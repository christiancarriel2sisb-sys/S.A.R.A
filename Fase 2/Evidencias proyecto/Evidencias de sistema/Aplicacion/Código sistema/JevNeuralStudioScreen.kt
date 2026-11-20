package com.sara.robot.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.sara.robot.navigation.reasoning.JevIntentModule
import com.sara.robot.navigation.reasoning.JevNeuralIntentClassifier

/**
 * Laboratorio de Pruebas y Entrenamiento de la Red Neuronal Jev (Exclusivo Perfil Desarrollador).
 * Permite:
 * 1. Escribir cualquier frase en la barra de texto para probar la inferencia neuronal en tiempo real.
 * 2. Inspeccionar la distribución de probabilidades Softmax en los 5 módulos y los pesos del diccionario.
 * 3. Entrenar la red en vivo con nuevas frases mediante Backpropagation (Gradiente Descendente).
 */
@Composable
fun JevNeuralStudioScreen(
    onSwitchToRobotFace: () -> Unit,
    onSwitchToVision: () -> Unit,
    onSwitchToReminders: () -> Unit,
    onSwitchToMusic: () -> Unit,
    onExecuteInSaraOrchestrator: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var testPhrase by remember { mutableStateOf("a las 10, tengo que tomar mi medicamento") }
    var prediction by remember { mutableStateOf(JevNeuralIntentClassifier.predict(testPhrase)) }
    var metrics by remember { mutableStateOf(JevNeuralIntentClassifier.getMetrics()) }
    var selectedTrainModule by remember { mutableStateOf(JevIntentModule.RECORDATORIO) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showDictionaryModal by remember { mutableStateOf(false) }

    val quickTestPhrases = listOf(
        "a las 10, tengo que tomar mi medicamento",
        "no se me puede olvidar el remedio a las 8",
        "ponme una canción de los prisioneros",
        "dile a claudia que ya almorcé",
        "busca donde está christian y acércate",
        "me siento un poco triste hoy, cuéntame algo"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070712))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- BARRA SUPERIOR DEL LABORATORIO DE RED NEURONAL JEV ---
            Surface(
                color = Color(0xFF101022),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFFFA502).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color(0xFFFFA502),
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "JEV NEURAL INTENT STUDIO • ORQUESTADOR 0-TOKENS",
                                color = Color(0xFFFFA502),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Vocabulario: ${metrics.vocabularySize} raíces/bigramas | Muestras: ${metrics.totalSamples} | Épocas: ${metrics.totalEpochsTrained} | Precisión: ${"%.1f".format(metrics.trainingAccuracy * 100)}% | Loss: ${"%.4f".format(metrics.currentLoss)}",
                                color = Color(0xFF00FFCC),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Botones rápidos para saltar a los demás módulos del Desarrollador
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showDictionaryModal = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Ver Diccionario", color = Color(0xFF00F0FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onSwitchToRobotFace,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x22FFFFFF))
                        ) {
                            Icon(Icons.Default.Face, contentDescription = "Sara", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onSwitchToVision,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x22FFFFFF))
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Visión", tint = Color(0xFF00F0FF), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onSwitchToReminders,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x22FFFFFF))
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = "Recordatorios", tint = Color(0xFFFFD32A), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onSwitchToMusic,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x22FFFFFF))
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = "Música", tint = Color(0xFFFF4757), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { showLogDialog = true },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x22FFFFFF))
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = Color(0xFF2ED573), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // --- CONTENIDO PRINCIPAL EN 2 COLUMNAS (LANDSCAPE) ---
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // COLUMNA IZQUIERDA: Barra de Texto de Testeo + Chips Rápidos + Entrenamiento en Vivo
                Surface(
                    color = Color(0xFF0D0D1D),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1. BARRA DE TESTEO DE INFERENCIA NEURONAL",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        OutlinedTextField(
                            value = testPhrase,
                            onValueChange = {
                                testPhrase = it
                                prediction = JevNeuralIntentClassifier.predict(it)
                            },
                            label = { Text("Escribe una frase para evaluar en la Red Jev...", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    prediction = JevNeuralIntentClassifier.predict(testPhrase)
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Evaluar", tint = Color(0xFF2ED573))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFA502),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Ejemplos rápidos de prueba con 1 toque
                        Text(
                            text = "Frases de prueba rápida (toca una):",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (sample in quickTestPhrases) {
                                Surface(
                                    color = Color(0xFF1A1A32),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0x4400F0FF)),
                                    modifier = Modifier.clickable {
                                        testPhrase = sample
                                        prediction = JevNeuralIntentClassifier.predict(sample)
                                    }
                                ) {
                                    Text(
                                        text = sample,
                                        color = Color(0xFF00F0FF),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0x22FFFFFF))

                        // Panel de Entrenamiento en Vivo (Backpropagation)
                        Text(
                            text = "2. ENTRENAR LA RED CON ESTA FRASE (BACKPROPAGATION)",
                            color = Color(0xFFFFA502),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Si deseas enseñarle esta frase a la red o corregir su clasificación, selecciona el módulo correcto y presiona Entrenar:",
                            color = Color.LightGray,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )

                        // Selector de etiqueta objetivo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (mod in JevIntentModule.entries) {
                                val isSel = (selectedTrainModule == mod)
                                val modColor = Color(mod.badgeColorHex)
                                Surface(
                                    color = if (isSel) modColor else Color(0xFF16162A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, modColor),
                                    modifier = Modifier.clickable { selectedTrainModule = mod }
                                ) {
                                    Text(
                                        text = mod.name,
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (testPhrase.isBlank()) return@Button
                                    metrics = JevNeuralIntentClassifier.aprenderNuevaFrase(testPhrase, selectedTrainModule)
                                    prediction = JevNeuralIntentClassifier.predict(testPhrase)
                                    Toast.makeText(
                                        context,
                                        "¡Red entrenada! Ahora asocia la frase a ${selectedTrainModule.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ENTRENAR FRASE EN LA RED",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    metrics = JevNeuralIntentClassifier.reconstruirDiccionarioYEntrenar(epochs = 50, learningRate = 0.7f)
                                    prediction = JevNeuralIntentClassifier.predict(testPhrase)
                                    Toast.makeText(context, "+50 épocas de Backpropagation completadas", Toast.LENGTH_SHORT).show()
                                },
                                border = BorderStroke(1.dp, Color(0xFFFFA502)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+50 Épocas", color = Color(0xFFFFA502), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // COLUMNA DERECHA: Resultado de la Red, Distribución Softmax y Tokens Activados
                Surface(
                    color = Color(0xFF0D0D1D),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val winnerColor = Color(prediction.winningModule.badgeColorHex)

                        // Tarjeta del Módulo Ganador
                        Surface(
                            color = winnerColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, winnerColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "MÓDULO DECIDIDO POR JEV:",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Tiempo: ${prediction.inferenceTimeMicros} µs",
                                        color = Color(0xFF00FFCC),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "${prediction.winningModule.displayName} (${"%.1f".format(prediction.confidence * 100)}%)",
                                    color = winnerColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (prediction.shouldExecuteLocallyZeroTokens) {
                                        "⚡ ACCIÓN: Ejecución 100% Local por Jev (0 TOKENS consumidos)"
                                    } else {
                                        "☁️ ACCIÓN: Derivado a Gemini Cloud (Conversación abierta o baja certeza)"
                                    },
                                    color = if (prediction.shouldExecuteLocallyZeroTokens) Color(0xFF2ED573) else Color(0xFFFFD32A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (onExecuteInSaraOrchestrator != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { onExecuteInSaraOrchestrator(testPhrase) },
                                        colors = ButtonDefaults.buttonColors(containerColor = winnerColor),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.fillMaxWidth().height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "EJECUTAR AHORA EN SARA CON JEV ORQUESTADOR",
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        // Barras de Probabilidad Softmax para los 5 Módulos
                        Text(
                            text = "DISTRIBUCIÓN DE PROBABILIDADES SOFTMAX:",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        for (mod in JevIntentModule.entries) {
                            val prob = prediction.probabilities[mod] ?: 0f
                            val mColor = Color(mod.badgeColorHex)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = mod.displayName,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${"%.1f".format(prob * 100)}%",
                                        color = mColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { prob.coerceIn(0f, 1f) },
                                    color = mColor,
                                    trackColor = Color(0xFF1E1E36),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0x22FFFFFF))

                        // Tokens del Diccionario Activados
                        Text(
                            text = "RAÍCES Y BIGRAMAS ACTIVADOS EN EL DICCIONARIO:",
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        if (prediction.activatedDictionaryTokens.isEmpty()) {
                            Text(
                                text = "Ninguna raíz del diccionario operativo coincidió -> Fallback seguro a Gemini.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        } else {
                            for (act in prediction.activatedDictionaryTokens) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF16162A), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Token: '${act.token}'",
                                        color = Color(0xFF00FFCC),
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "TF-IDF: ${"%.2f".format(act.tfIdfValue)} | Peso W: ${"%+.2f".format(act.synapticWeightForWinner)}",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Modal para inspeccionar las palabras con mayor peso sináptico aprendidas por cada módulo
        if (showDictionaryModal) {
            val topWordsByMod = remember(metrics) { JevNeuralIntentClassifier.getTopDictionaryWordsPerModule(10) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF101024),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF00F0FF)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DICCIONARIO DE DATOS Y PESOS SINÁPTICOS APRENDIDOS POR LA RED",
                                color = Color(0xFF00F0FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Button(
                                onClick = { showDictionaryModal = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4757))
                            ) {
                                Text("Cerrar", color = Color.White)
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(JevIntentModule.entries.toList()) { mod ->
                                val words = topWordsByMod[mod] ?: emptyList()
                                val mColor = Color(mod.badgeColorHex)
                                Surface(
                                    color = Color(0xFF181830),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, mColor.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = mod.displayName,
                                            color = mColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = words.joinToString("   •   ") { "${it.first} (W=${"%+.2f".format(it.second)})" },
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showLogDialog) {
            LogPanelDialog(onDismiss = { showLogDialog = false })
        }
    }
}
