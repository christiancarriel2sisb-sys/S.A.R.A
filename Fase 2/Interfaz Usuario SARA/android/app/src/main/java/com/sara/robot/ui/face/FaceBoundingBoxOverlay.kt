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
 * directamente encima del rostro detectado, calculando la proyección visual exacta (FILL_CENTER)
 * tanto sobre el video en tiempo real como sobre la interfaz del robot.
 */
@Composable
fun FaceBoundingBoxOverlay(
    trackingData: FaceTrackingData,
    modifier: Modifier = Modifier
) {
    // Transición suave de opacidad al detectar / perder el rostro
    val alphaAnim by animateFloatAsState(
        targetValue = if (trackingData.faceDetected) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "faceOverlayAlpha"
    )

    if (alphaAnim > 0.01f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val imgW = if (trackingData.imageWidth > 0f) trackingData.imageWidth else canvasW
            val imgH = if (trackingData.imageHeight > 0f) trackingData.imageHeight else canvasH

            // Escalado idéntico a PreviewView.ScaleType.FILL_CENTER
            val scale = maxOf(canvasW / imgW, canvasH / imgH)
            val offsetX = (canvasW - imgW * scale) / 2f
            val offsetY = (canvasH - imgH * scale) / 2f

            // Coordenadas con efecto espejo si es cámara frontal
            val mappedLeft = if (trackingData.isFrontCamera) {
                (imgW - trackingData.rawRight) * scale + offsetX
            } else {
                trackingData.rawLeft * scale + offsetX
            }

            val mappedRight = if (trackingData.isFrontCamera) {
                (imgW - trackingData.rawLeft) * scale + offsetX
            } else {
                trackingData.rawRight * scale + offsetX
            }

            val mappedTop = trackingData.rawTop * scale + offsetY
            val mappedBottom = trackingData.rawBottom * scale + offsetY

            val leftPx = minOf(mappedLeft, mappedRight)
            val rightPx = maxOf(mappedLeft, mappedRight)
            val topPx = minOf(mappedTop, mappedBottom)
            val bottomPx = maxOf(mappedTop, mappedBottom)

            val boxWidth = (rightPx - leftPx).coerceAtLeast(50.dp.toPx())
            val boxHeight = (bottomPx - topPx).coerceAtLeast(50.dp.toPx())

            val primaryColor = Color(0xFF00F0FF).copy(alpha = alphaAnim)
            val glowColor = Color(0x2500F0FF).copy(alpha = alphaAnim * 0.45f)
            val strokeWidthPx = 3f.dp.toPx()
            val bracketLength = (boxWidth * 0.22f).coerceIn(16.dp.toPx(), 45.dp.toPx())

            // 1. Relleno traslúcido interior suave
            drawRoundRect(
                color = glowColor,
                topLeft = Offset(leftPx, topPx),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(12.dp.toPx())
            )

            // 2. Líneas punteadas finas de conexión del marco
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            drawRoundRect(
                color = primaryColor.copy(alpha = alphaAnim * 0.5f),
                topLeft = Offset(leftPx, topPx),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 1.5f.dp.toPx(), pathEffect = dashEffect)
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
            val crossSize = 10.dp.toPx()
            drawLine(primaryColor.copy(alpha = alphaAnim * 0.8f), Offset(centerX - crossSize, centerY), Offset(centerX + crossSize, centerY), strokeWidth = 2.dp.toPx())
            drawLine(primaryColor.copy(alpha = alphaAnim * 0.8f), Offset(centerX, centerY - crossSize), Offset(centerX, centerY + crossSize), strokeWidth = 2.dp.toPx())

            // 5. Etiqueta superior con estado y ángulo
            val angleText = "ROSTRO FIJADO [${"%.1f".format(trackingData.angleOffsetDegrees)}°]"
            drawIntoCanvas { canvas ->
                val textPaint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 12.sp.toPx()
                    isFakeBoldText = true
                    isAntiAlias = true
                    alpha = (alphaAnim * 255).toInt()
                }

                val badgeBgPaint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.argb((alphaAnim * 220).toInt(), 8, 16, 26)
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
                val badgeHeight = 24.dp.toPx()
                val badgeX = leftPx + (boxWidth - badgeWidth) / 2f
                val badgeY = (topPx - badgeHeight - 8.dp.toPx()).coerceAtLeast(10.dp.toPx())

                val badgeRect = android.graphics.RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight)
                canvas.nativeCanvas.drawRoundRect(badgeRect, 6.dp.toPx(), 6.dp.toPx(), badgeBgPaint)
                canvas.nativeCanvas.drawRoundRect(badgeRect, 6.dp.toPx(), 6.dp.toPx(), badgeBorderPaint)
                canvas.nativeCanvas.drawText(
                    angleText,
                    badgeX + 12.dp.toPx(),
                    badgeY + badgeHeight - 7.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}
