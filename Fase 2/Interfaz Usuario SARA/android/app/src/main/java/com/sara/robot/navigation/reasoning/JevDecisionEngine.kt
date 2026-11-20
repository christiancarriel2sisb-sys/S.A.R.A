package com.sara.robot.navigation.reasoning

import com.sara.robot.core.AppLogger
import kotlin.math.exp

/**
 * ============================================================================
 * MOTOR DE DECISIÓN Y ORQUESTACIÓN CENTRAL DEL SISTEMA - MODELO JEV
 * ============================================================================
 * Combina dos capacidades de inferencia probabilística 100% On-Device (< 1 ms, 0 tokens):
 *
 * 1. ORQUESTADOR DE INTENCIONES DEL SISTEMA (Reemplaza a Gemini como enrutador primario):
 *    - Utiliza la Red Neuronal entrenada (`JevNeuralIntentClassifier`) con diccionario TF-IDF,
 *      lematización en español (stemming), bigramas y activación Softmax.
 *    - Evalúa cada frase hablada por el usuario en el módulo de conversación y decide qué
 *      subsistema de Sara activar localmente a 0 tokens (`RECORDATORIO`, `MUSICA`,
 *      `CHAT_FAMILIAR`, `MOVIMIENTO_ROSTRO`), derivando a Gemini únicamente cuando la intención
 *      es `CONVERSACION_LIBRE` o la confianza es baja.
 *
 * 2. DECISIÓN CINEMÁTICA ESPACIAL (Navegación Reactiva):
 *    - Transforma las distancias sectoriales [Z_izq, Z_cen, Z_der] en probabilidades de acción
 *      motriz [P(Avanzar), P(Girar_Izq), P(Girar_Der), P(Detenerse)] para el controlador Arduino.
 */
class JevDecisionEngine {

    private val maxPerceptionRangeMeters = 3.5f

    /**
     * Orquesta la intención del usuario desde el módulo de conversación utilizando
     * la Red Neuronal de Jev (`JevNeuralIntentClassifier`).
     */
    fun orchestrateSystemIntent(userText: String): JevNeuralPrediction {
        val prediction = JevNeuralIntentClassifier.predict(userText)
        val pct = "%.1f".format(prediction.confidence * 100)

        if (prediction.shouldExecuteLocallyZeroTokens) {
            AppLogger.success(
                "JevOrchestrator",
                "⚡ [JEV ORQUESTADOR] Frase: \"$userText\" ➔ Módulo activado: ${prediction.winningModule.name} ($pct%) | Ejecución Local (0 TOKENS) en ${prediction.inferenceTimeMicros}µs"
            )
        } else {
            AppLogger.info(
                "JevOrchestrator",
                "☁️ [JEV ORQUESTADOR] Frase: \"$userText\" ➔ Clasificado como ${prediction.winningModule.name} ($pct%) | Derivando a Gemini Cloud"
            )
        }

        return prediction
    }

    /**
     * Inferencia vectorial espacial (< 1 ms) para navegación física del chasis.
     */
    fun evaluate(
        zMinLeft: Float,
        zMinCenter: Float,
        zMinRight: Float,
        previousTurnDirection: Float // -1.0 izq, 0.0 recto, +1.0 der
    ): Pair<ActionProbabilities, NavigationAction> {

        // 1. Verificación determinista de seguridad estricta
        if (SafetySupervisor.isEmergencyStopRequired(zMinCenter)) {
            val emergencyProb = ActionProbabilities(
                pAvanzar = 0.0f,
                pGirarIzq = 0.0f,
                pGirarDer = 0.0f,
                pDetenerse = 1.0f
            )
            return Pair(emergencyProb, NavigationAction.DETENERSE)
        }

        // 2. Normalización de distancias al rango [0.0, 1.0] donde 1.0 = totalmente despejado
        val normLeft = (zMinLeft / maxPerceptionRangeMeters).coerceIn(0.0f, 1.0f)
        val normCenter = (zMinCenter / maxPerceptionRangeMeters).coerceIn(0.0f, 1.0f)
        val normRight = (zMinRight / maxPerceptionRangeMeters).coerceIn(0.0f, 1.0f)

        // 3. Ponderación de pesos sinápticos espaciales del modelo Jev
        val scoreAvanzar = (normCenter * 3.5f) - (if (normCenter < 0.35f) 4.0f else 0.0f)
        val scoreGirarIzq = (normLeft * 2.2f) - (normCenter * 1.5f) + (if (previousTurnDirection < 0) 0.5f else 0.0f)
        val scoreGirarDer = (normRight * 2.2f) - (normCenter * 1.5f) + (if (previousTurnDirection > 0) 0.5f else 0.0f)

        val allBlockedFactor = 1.0f - maxOf(normLeft, normCenter, normRight)
        val scoreDetenerse = (allBlockedFactor * 2.8f) + (if (normCenter < 0.25f) 2.0f else 0.0f)

        // 4. Activación Softmax
        val expAvanzar = exp(scoreAvanzar.toDouble()).toFloat()
        val expGirarIzq = exp(scoreGirarIzq.toDouble()).toFloat()
        val expGirarDer = exp(scoreGirarDer.toDouble()).toFloat()
        val expDetenerse = exp(scoreDetenerse.toDouble()).toFloat()

        val sumExp = expAvanzar + expGirarIzq + expGirarDer + expDetenerse

        val probabilities = ActionProbabilities(
            pAvanzar = expAvanzar / sumExp,
            pGirarIzq = expGirarIzq / sumExp,
            pGirarDer = expGirarDer / sumExp,
            pDetenerse = expDetenerse / sumExp
        )

        val chosenAction = probabilities.bestAction()
        return Pair(probabilities, chosenAction)
    }
}
