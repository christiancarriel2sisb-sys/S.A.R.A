package com.sara.robot.navigation.actuator

import com.sara.robot.navigation.reasoning.NavigationAction
import kotlin.math.roundToInt

data class MotorCommand(
    val turnAngleDegrees: Float,
    val pwmSpeed: Int,
    val rawFrame: String
)

/**
 * Formateador de comandos para microcontrolador de chasis (Capa 5).
 * Genera tramas de texto compactas en formato estándar:
 * "NAV:<angulo_giro_grados>,<velocidad_pwm>\n"
 */
object MotorCommandFormatter {

    fun format(
        action: NavigationAction,
        zMinLeft: Float,
        zMinRight: Float,
        isEmergencyStop: Boolean
    ): MotorCommand {
        if (isEmergencyStop || action == NavigationAction.DETENERSE) {
            return MotorCommand(0f, 0, "NAV:0,0\n")
        }

        return when (action) {
            NavigationAction.AVANZAR -> {
                MotorCommand(0f, 120, "NAV:0,120\n")
            }
            NavigationAction.GIRAR_IZQ -> {
                // Cálculo de ángulo evasivo proporcional a la cercanía
                val angle = -30f
                MotorCommand(angle, 80, "NAV:${angle.roundToInt()},80\n")
            }
            NavigationAction.GIRAR_DER -> {
                val angle = 30f
                MotorCommand(angle, 80, "NAV:${angle.roundToInt()},80\n")
            }
            NavigationAction.DETENERSE -> {
                MotorCommand(0f, 0, "NAV:0,0\n")
            }
        }
    }
}

