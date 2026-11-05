package com.sara.robot.navigation.reasoning

import com.sara.robot.core.AppLogger
import kotlin.math.exp
import kotlin.math.max

/**
 * Módulo de Decisión Probabilística - Modelo Jev (Capa 4).
 * Realiza inferencia vectorial ultrarrápida (<5 ms) en memoria local sin carga de red.
 * Transforma las distancias sectoriales en una distribución de probabilidades [P(Avanzar), P(Girar_Izq), P(Girar_Der), P(Detenerse)].
 * Si el sensor frontal detecta un obstáculo a menos de 0.40m, el SafetySupervisor fuerza OVERRIDE_STOP.
 */
class JevDecisionEngine {

    private val maxPerceptionRangeMeters = 3.5f

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

        // 3. Ponderación de pesos sinápticos del modelo Jev
        // - El centro abierto (normCenter alto) favorece AVANZAR
        // - Si el centro se cierra, se evalúa cuál lado tiene mayor holgura (normLeft vs normRight)
        // - La trayectoria previa penaliza oscilaciones bruscas (histéresis de giro)

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

