package com.sara.robot.navigation.actuator

import com.sara.robot.navigation.reasoning.NavigationAction

data class MotorCommand(
    val turnAngleDegrees: Float,
    val pwmSpeed: Int,
    val rawFrame: String,
    val actionCode: Int,
    val actionName: String
)

/**
 * Formateador de comandos para microcontrolador de chasis (Capa 5).
 * Genera tramas numéricas compatibles con prueba_sara_v2.ino de Arduino:
 * '1\n' -> Avanzar
 * '2\n' -> Giro Izquierda
 * '3\n' -> Giro Derecha
 * '5\n' -> Retroceder
 * '6\n' -> Detener / Freno de Emergencia
 */
object MotorCommandFormatter {

    fun format(
        action: NavigationAction,
        zMinLeft: Float,
        zMinRight: Float,
        isEmergencyStop: Boolean
    ): MotorCommand {
        if (isEmergencyStop || action == NavigationAction.DETENERSE) {
            return MotorCommand(
                turnAngleDegrees = 0f,
                pwmSpeed = 0,
                rawFrame = "6\n",
                actionCode = 6,
                actionName = "DETENER (6)"
            )
        }

        return when (action) {
            NavigationAction.AVANZAR -> {
                MotorCommand(
                    turnAngleDegrees = 0f,
                    pwmSpeed = 120,
                    rawFrame = "1\n",
                    actionCode = 1,
                    actionName = "AVANZAR (1)"
                )
            }
            NavigationAction.GIRAR_IZQ -> {
                MotorCommand(
                    turnAngleDegrees = -30f,
                    pwmSpeed = 80,
                    rawFrame = "2\n",
                    actionCode = 2,
                    actionName = "GIRO IZQ (2)"
                )
            }
            NavigationAction.GIRAR_DER -> {
                MotorCommand(
                    turnAngleDegrees = 30f,
                    pwmSpeed = 80,
                    rawFrame = "3\n",
                    actionCode = 3,
                    actionName = "GIRO DER (3)"
                )
            }
            NavigationAction.DETENERSE -> {
                MotorCommand(
                    turnAngleDegrees = 0f,
                    pwmSpeed = 0,
                    rawFrame = "6\n",
                    actionCode = 6,
                    actionName = "DETENER (6)"
                )
            }
        }
    }
}
