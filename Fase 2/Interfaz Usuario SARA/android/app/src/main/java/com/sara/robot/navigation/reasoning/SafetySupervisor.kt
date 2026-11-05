package com.sara.robot.navigation.reasoning

import com.sara.robot.core.AppLogger

/**
 * Supervisor de seguridad determinista (Capa 4).
 * Si la distancia mínima en el sector frontal es menor al umbral crítico (< 0.40 m),
 * anula inmediatamente cualquier recomendación del modelo probabilístico y ordena
 * frenado de emergencia estricto (OVERRIDE_STOP) para proteger la integridad del robot y personas.
 */
object SafetySupervisor {

    const val CRITICAL_DISTANCE_METERS = 0.40f

    fun isEmergencyStopRequired(zMinCenter: Float): Boolean {
        val stopRequired = zMinCenter < CRITICAL_DISTANCE_METERS
        if (stopRequired) {
            AppLogger.warn("SafetySupervisor", "🚨 ¡OVERRIDE_STOP ACTIVADO! Obstáculo frontal a ${"%.2f".format(zMinCenter)}m (< ${CRITICAL_DISTANCE_METERS}m)")
        }
        return stopRequired
    }
}

