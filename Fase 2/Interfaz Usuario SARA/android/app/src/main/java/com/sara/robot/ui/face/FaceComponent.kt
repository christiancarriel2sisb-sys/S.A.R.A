package com.sara.robot.ui.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Replicación fiel del componente SVG FaceComponent a 60 FPS en Jetpack Compose.
 * Dimensiones lógicas idénticas al prototipo SVG: viewBox 800 x 480.
 * - Color principal: #6B5BFF
 * - Ojos: Circunferencias con strokeWidth de 22px en (290, 190) y (510, 190) con radio 52px.
 * - Boca: Path Bezier dinámico en (310, 338) a (490, 338) con curvatura según mouthOpen.
 * - Marco: rect 760x440 con rx 90px en (20,20), fill #05050A y stroke #14142A.
 */
@Composable
fun FaceComponent(
    state: String,
    modifier: Modifier = Modifier
) {
    val animator = remember { FaceAnimator() }
    var animResult by remember {
        mutableStateOf(
            FaceAnimResult(
                gazeX = 0f,
                gazeY = 0f,
                blinkScaleY = 1f,
                mouthOpen = 0f,
                glowDeviation = 6f,
                dimOpacity = 0.45f
            )
        )
    }

    var lastFrameTimeNanos by remember { mutableLongStateOf(0L) }

    // Bucle de animación a 60 FPS con withFrameNanos (similar a requestAnimationFrame)
    LaunchedEffect(state) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val dtMs = if (lastFrameTimeNanos > 0L) {
                    ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000f).coerceIn(1f, 50f)
                } else {
                    16.66f
                }
                lastFrameTimeNanos = frameTimeNanos
                animResult = animator.update(dtMs, state)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Escalar para cubrir toda la pantalla (maxOf) como en CSS background-size: cover
            val baseW = 800f
            val baseH = 480f
            val scaleFactor = maxOf(canvasW / baseW, canvasH / baseH)
            val offsetX = (canvasW - baseW * scaleFactor) / 2f
            val offsetY = (canvasH - baseH * scaleFactor) / 2f

            withTransform({
                translate(left = offsetX, top = offsetY)
                scale(scaleX = scaleFactor, scaleY = scaleFactor, pivot = Offset.Zero)
            }) {
                // Fondo interior del robot
                drawRoundRect(
                    color = Color(0xFF05050A),
                    topLeft = Offset(20f, 20f),
                    size = Size(760f, 440f),
                    cornerRadius = CornerRadius(90f, 90f)
                )
                drawRoundRect(
                    color = Color(0xFF14142A),
                    topLeft = Offset(20f, 20f),
                    size = Size(760f, 440f),
                    cornerRadius = CornerRadius(90f, 90f),
                    style = Stroke(width = 2f)
                )

                // Grupo de la cara con opacidad dinámica (dimOpacity)
                val primaryColor = Color(0xFF6B5BFF).copy(alpha = animResult.dimOpacity)

                // Ojo Izquierdo en (290, 190)
                drawEye(
                    centerX = 290f,
                    centerY = 190f,
                    gazeX = animResult.gazeX,
                    gazeY = animResult.gazeY,
                    blinkScaleY = animResult.blinkScaleY,
                    radius = 52f,
                    strokeWidth = 22f,
                    glowRadius = animResult.glowDeviation * 2.5f,
                    color = primaryColor
                )

                // Ojo Derecho en (510, 190)
                drawEye(
                    centerX = 510f,
                    centerY = 190f,
                    gazeX = animResult.gazeX,
                    gazeY = animResult.gazeY,
                    blinkScaleY = animResult.blinkScaleY,
                    radius = 52f,
                    strokeWidth = 22f,
                    glowRadius = animResult.glowDeviation * 2.5f,
                    color = primaryColor
                )

                // Boca dinámica (Path Bezier adaptativo con mouthOpen)
                drawMouth(
                    mouthOpen = animResult.mouthOpen,
                    glowRadius = animResult.glowDeviation * 2.5f,
                    color = primaryColor
                )
            }
        }
    }
}

private fun DrawScope.drawEye(
    centerX: Float,
    centerY: Float,
    gazeX: Float,
    gazeY: Float,
    blinkScaleY: Float,
    radius: Float,
    strokeWidth: Float,
    glowRadius: Float,
    color: Color
) {
    val eyeX = centerX + gazeX
    val eyeY = centerY + gazeY

    withTransform({
        scale(scaleX = 1f, scaleY = blinkScaleY, pivot = Offset(eyeX, eyeY))
    }) {
        // Resplandor exterior (Glow) usando Paint nativo con BlurMaskFilter
        drawIntoCanvas { canvas ->
            val frameworkPaint = Paint().asFrameworkPaint().apply {
                this.color = android.graphics.Color.argb(
                    (color.alpha * 120).toInt(),
                    107, 91, 255
                )
                this.style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = strokeWidth + glowRadius
                if (glowRadius > 1f) {
                    this.maskFilter = android.graphics.BlurMaskFilter(
                        glowRadius,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
            }
            canvas.nativeCanvas.drawCircle(eyeX, eyeY, radius, frameworkPaint)
        }

        // Círculo nítido del ojo
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(eyeX, eyeY),
            style = Stroke(width = strokeWidth)
        )
    }
}

private fun DrawScope.drawMouth(
    mouthOpen: Float,
    glowRadius: Float,
    color: Color
) {
    val o = mouthOpen
    val w = 180f - (20f * o)
    val x1 = 400f - (w / 2f)
    val x2 = 400f + (w / 2f)
    val topCurveY = 338f - (8f * o)
    val topControlY = 352f - (4f * o)
    val botControlY = 364f + (55f * o)
    val botEdgeY = 350f + (12f * o)

    val mouthPath = Path().apply {
        moveTo(x1, topCurveY)
        quadraticBezierTo(400f, topControlY, x2, topCurveY)
        lineTo(x2, botEdgeY)
        quadraticBezierTo(400f, botControlY, x1, botEdgeY)
        close()
    }

    // Glow de la boca
    drawIntoCanvas { canvas ->
        val frameworkPaint = Paint().asFrameworkPaint().apply {
            this.color = android.graphics.Color.argb(
                (color.alpha * 140).toInt(),
                107, 91, 255
            )
            this.style = android.graphics.Paint.Style.FILL
            if (glowRadius > 1f) {
                this.maskFilter = android.graphics.BlurMaskFilter(
                    glowRadius,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
        }
        val androidPath = mouthPath.asAndroidPath()
        canvas.nativeCanvas.drawPath(androidPath, frameworkPaint)
    }

    // Boca sólida rellena
    drawPath(
        path = mouthPath,
        color = color
    )
}
