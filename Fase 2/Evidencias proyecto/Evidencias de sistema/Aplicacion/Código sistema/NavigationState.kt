package com.sara.robot.navigation.reasoning

enum class NavigationAction {
    AVANZAR,
    GIRAR_IZQ,
    GIRAR_DER,
    DETENERSE
}

data class ActionProbabilities(
    val pAvanzar: Float,
    val pGirarIzq: Float,
    val pGirarDer: Float,
    val pDetenerse: Float
) {
    fun bestAction(): NavigationAction {
        val maxP = maxOf(pAvanzar, pGirarIzq, pGirarDer, pDetenerse)
        return when (maxP) {
            pAvanzar -> NavigationAction.AVANZAR
            pGirarIzq -> NavigationAction.GIRAR_IZQ
            pGirarDer -> NavigationAction.GIRAR_DER
            else -> NavigationAction.DETENERSE
        }
    }
}

data class DetectedObstacle(
    val label: String,
    val confidence: Float,
    val xRelMeters: Float,
    val zDistMeters: Float,
    val bboxLeft: Float,
    val bboxTop: Float,
    val bboxRight: Float,
    val bboxBottom: Float
)

data class NavigationState(
    val zMinIzq: Float = 5.0f,
    val zMinCen: Float = 5.0f,
    val zMinDer: Float = 5.0f,
    val previousTurnDirection: Float = 0f, // -1.0 izq, 0.0 recto, +1.0 der
    val probabilities: ActionProbabilities = ActionProbabilities(1f, 0f, 0f, 0f),
    val selectedAction: NavigationAction = NavigationAction.AVANZAR,
    val isEmergencyStop: Boolean = false,
    val targetTurnAngle: Float = 0f,
    val targetPwmSpeed: Int = 120,
    val serialCommand: String = "NAV:0,120\n",
    val obstacles: List<DetectedObstacle> = emptyList(),
    val inferenceTimeMs: Long = 0L,
    val fps: Float = 0f
)

