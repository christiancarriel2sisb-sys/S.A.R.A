package com.sara.robot.navigation.perception

import kotlin.math.max

data class SpatialPoint3D(
    val xRelMeters: Float,
    val zDistMeters: Float
)

/**
 * Estimación espacial 3D monocular mediante óptica pinhole (Capa 3).
 * Infiere la distancia métrica Z y el desplazamiento horizontal X_rel a partir
 * de la altura física esperada del objeto y las dimensiones de la caja delimitadora (bbox).
 */
class SpatialProjector(
    private val frameWidth: Float = 320f,
    private val frameHeight: Float = 320f,
    private val cameraHFOV: Float = 65.0f
) {
    // Longitud focal calculada: f = (w / 2) / tan(HFOV / 2)
    private val focalLengthPx: Float = (frameWidth / 2f) / kotlin.math.tan(Math.toRadians(cameraHFOV.toDouble() / 2.0)).toFloat()

    // Alturas físicas estándar en entornos hospitalarios (en metros)
    private val expectedHeightsMeters = mapOf(
        "persona" to 1.70f,
        "person" to 1.70f,
        "silla" to 0.90f,
        "chair" to 0.90f,
        "cama" to 0.85f,
        "bed" to 0.85f,
        "pared" to 1.20f,
        "obstacle" to 1.00f
    )

    fun project(
        label: String,
        bboxWidthPx: Float,
        bboxHeightPx: Float,
        bboxCenterX: Float
    ): SpatialPoint3D {
        val hReal = expectedHeightsMeters[label.lowercase()] ?: 1.00f
        val hBboxClamped = max(bboxHeightPx, 10f)

        // Z ≈ (H_real * f_y) / h_bbox
        val zDist = (hReal * focalLengthPx) / hBboxClamped

        // X_rel ≈ ((X_centro - X_medio) * Z) / f_x
        val xCenterOffset = bboxCenterX - (frameWidth / 2f)
        val xRel = (xCenterOffset * zDist) / focalLengthPx

        return SpatialPoint3D(
            xRelMeters = xRel,
            zDistMeters = zDist.coerceIn(0.15f, 15.0f)
        )
    }
}

