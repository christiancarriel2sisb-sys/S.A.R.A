package com.sara.robot.ui.face

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sara.robot.vision.FaceTrackingData

/**
 * Overlay HUD de alta tecnología que dibuja un marco de fijación de objetivo (Bounding Box)
 * directamente encima del rostro de Sara en la pantalla principal de voz.
 * Confirma al usuario que el robot está detectando su rostro activamente.
 */
@Composable
fun FaceBoundingBoxOverlay(
    trackingData: FaceTrackingData,
    modifier: Modifier = Modifier
) {
    // Transición suave de opacidad al detectar / perder el rostro
    val alphaAnim by animateFloatAsState(
        targetValue = if (trackingData.faceDetected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "faceOverlayAlpha"
    )

    // Animación suave de coordenadas para evitar jittering (temblor de fotogramas)
    val leftAnim by animateFloatAsState(
        targetValue = trackingData.normalizedLeft,
        animationSpec = tween(durationMillis = 100),
        label = "boxLeft"
    )
    val topAnim by animateFloatAsState(
        targetValue = trackingData.normalizedTop,
        animationSpec = tween(durationMillis = 100),
        label = "boxTop"
    )
    val rightAnim by animateFloatAsState(
        targetValue = trackingData.normalizedRight,
        animationSpec = tween(durationMillis = 100),
        label = "boxRight"
    )
    val bottomAnim by animateFloatAsState(
        targetValue = trackingData.normalizedBottom,
        animationSpec = tween(durationMillis = 100),
        label = "boxBottom"
    )

    if (alphaAnim > 0.01f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val leftPx = leftAnim * canvasW
            val topPx = topAnim * canvasH
            val rightPx = rightAnim * canvasW
            val bottomPx = bottomAnim * canvasH

            val boxWidth = (rightPx - leftPx).coerceAtLeast(60.dp.toPx())
            val boxHeight = (bottomPx - topPx).coerceAtLeast(60.dp.toPx())

            val primaryColor = Color(0xFF00F0FF).copy(alpha = alphaAnim)
            val glowColor = Color(0x3300F0FF).copy(alpha = alphaAnim * 0.4f)
            val strokeWidthPx = 2.5f.dp.toPx()
            val bracketLength = (boxWidth * 0.22f).coerceIn(16.dp.toPx(), 42.dp.toPx())

            // 1. Relleno traslúcido interior suave
            drawRoundRect(
                color = glowColor,
                topLeft = Offset(leftPx, topPx),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(12.dp.toPx())
            )

            // 2. Líneas punteadas finas de conexión del marco
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawRoundRect(
                color = primaryColor.copy(alpha = alphaAnim * 0.45f),
                topLeft = Offset(leftPx, topPx),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 1.2f.dp.toPx(), pathEffect = dashEffect)
            )

            // 3. Esquinas reforzadas estilo retícula de visor robótico (Brackets)
            // Esquina Superior Izquierda
            val pathTopLeft = Path().apply {
                moveTo(leftPx, topPx + bracketLength)
                lineTo(leftPx, topPx)
                lineTo(leftPx + bracketLength, topPx)
            }
            drawPath(pathTopLeft, primaryColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))

            // Esquina Superior Derecha
            val pathTopRight = Path().apply {
                moveTo(rightPx - bracketLength, topPx)
                lineTo(rightPx, topPx)
                lineTo(rightPx, topPx + bracketLength)
            }
            drawPath(pathTopRight, primaryColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))

            // Esquina Inferior Izquierda
            val pathBottomLeft = Path().apply {
                moveTo(leftPx, bottomPx - bracketLength)
                lineTo(leftPx, bottomPx)
                lineTo(leftPx + bracketLength, bottomPx)
            }
            drawPath(pathBottomLeft, primaryColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))

            // Esquina Inferior Derecha
            val pathBottomRight = Path().apply {
                moveTo(rightPx - bracketLength, bottomPx)
                lineTo(rightPx, bottomPx)
                lineTo(rightPx, bottomPx - bracketLength)
            }
            drawPath(pathBottomRight, primaryColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))

            // 4. Cruz de enfoque en el centroide facial
            val centerX = leftPx + boxWidth / 2f
            val centerY = topPx + boxHeight / 2f
            val crossSize = 8.dp.toPx()
            drawLine(primaryColor.copy(alpha = alphaAnim * 0.7f), Offset(centerX - crossSize, centerY), Offset(centerX + crossSize, centerY), strokeWidth = 1.5f.dp.toPx())
            drawLine(primaryColor.copy(alpha = alphaAnim * 0.7f), Offset(centerX, centerY - crossSize), Offset(centerX, centerY + crossSize), strokeWidth = 1.5f.dp.toPx())

            // 5. Etiqueta superior con estado y ángulo
            val angleText = "ROSTRO DETECTADO [${"%.1f".format(trackingData.angleOffsetDegrees)}°]"
            drawIntoCanvas { canvas ->
                val textPaint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 11.sp.toPx()
                    isFakeBoldText = true
                    isAntiAlias = true
                    alpha = (alphaAnim * 255).toInt()
                }

                val badgeBgPaint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.argb((alphaAnim * 200).toInt(), 10, 20, 30)
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }

                val badgeBorderPaint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.argb((alphaAnim * 255).toInt(), 0, 240, 255)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.5f.dp.toPx()
                    isAntiAlias = true
                }

                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(angleText, 0, angleText.length, textBounds)
                val badgeWidth = textBounds.width() + 24.dp.toPx()
                val badgeHeight = 22.dp.toPx()
                val badgeX = leftPx + (boxWidth - badgeWidth) / 2f
                val badgeY = (topPx - badgeHeight - 6.dp.toPx()).coerceAtLeast(10.dp.toPx())

                val badgeRect = android.graphics.RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight)
                canvas.nativeCanvas.drawRoundRect(badgeRect, 6.dp.toPx(), 6.dp.toPx(), badgeBgPaint)
                canvas.nativeCanvas.drawRoundRect(badgeRect, 6.dp.toPx(), 6.dp.toPx(), badgeBorderPaint)
                canvas.nativeCanvas.drawText(
                    angleText,
                    badgeX + 12.dp.toPx(),
                    badgeY + badgeHeight - 6.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}
