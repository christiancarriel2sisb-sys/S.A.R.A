package com.sara.robot.ui.face

import kotlin.math.sin
import kotlin.random.Random

data class FaceAnimResult(
    val gazeX: Float,
    val gazeY: Float,
    val blinkScaleY: Float,
    val mouthOpen: Float,
    val glowDeviation: Float,
    val dimOpacity: Float
)

/**
 * Port directo a Kotlin de FaceAnimator (60 FPS).
 * Controla micro-movimientos de mirada, parpadeo con probabilidad de doble parpadeo,
 * sincronización de boca al hablar (mouthOpen) y estados visuales (IDLE, LISTENING, THINKING, SPEAKING, ERROR).
 */
class FaceAnimator {
    private var gazeX = 0f
    private var gazeY = 0f
    private var targetGazeX = 0f
    private var targetGazeY = 0f

    private var blinkScaleY = 1f
    private var isBlinking = false
    private var blinkTimer = 0f
    private var nextBlinkInterval = 3000f
    private var doubleBlinkCount = 0

    private var currentMouthOpen = 0f
    private var targetMouthOpen = 0f
    private var mouthTimer = 0f
    private var lastMicromoveTime = 0L

    init {
        scheduleNextBlink()
    }

    private fun scheduleNextBlink() {
        nextBlinkInterval = 2500f + Random.nextFloat() * 3500f
    }

    fun update(dtMs: Float, state: String): FaceAnimResult {
        val now = System.currentTimeMillis()

        when (state) {
            "THINKING" -> {
                targetGazeX = 14f
                targetGazeY = -9f
            }
            "LISTENING" -> {
                if (now - lastMicromoveTime > 2000) {
                    lastMicromoveTime = now
                    targetGazeX = (Random.nextFloat() - 0.5f) * 10f
                    targetGazeY = (Random.nextFloat() - 0.5f) * 10f
                }
            }
            "SPEAKING" -> {
                if (now - lastMicromoveTime > 1500) {
                    lastMicromoveTime = now
                    targetGazeX = (Random.nextFloat() - 0.5f) * 6f
                    targetGazeY = (Random.nextFloat() - 0.5f) * 6f
                }
            }
            "ERROR" -> {
                targetGazeX = 0f
                targetGazeY = 8f
            }
            else -> { // IDLE
                targetGazeX = 0f
                targetGazeY = 0f
            }
        }

        gazeX += (targetGazeX - gazeX) * 0.12f
        gazeY += (targetGazeY - gazeY) * 0.12f

        blinkTimer += dtMs
        if (!isBlinking && blinkTimer >= nextBlinkInterval) {
            isBlinking = true
            blinkTimer = 0f
        }

        if (isBlinking) {
            val blinkProgress = blinkTimer / 140f
            when {
                blinkProgress < 0.5f -> {
                    blinkScaleY = 1f - (blinkProgress * 2f) * 0.92f
                }
                blinkProgress < 1.0f -> {
                    blinkScaleY = 0.08f + ((blinkProgress - 0.5f) * 2f) * 0.92f
                }
                else -> {
                    blinkScaleY = 1f
                    isBlinking = false
                    blinkTimer = 0f
                    if (Random.nextFloat() < 0.15f && doubleBlinkCount == 0) {
                        doubleBlinkCount = 1
                        nextBlinkInterval = 200f
                    } else {
                        doubleBlinkCount = 0
                        scheduleNextBlink()
                    }
                }
            }
        } else {
            blinkScaleY = 1f
        }

        if (state == "SPEAKING") {
            mouthTimer += dtMs
            if (mouthTimer > 100f) {
                mouthTimer = 0f
                targetMouthOpen = 0.2f + Random.nextFloat() * 0.8f
            }
        } else {
            targetMouthOpen = 0f
        }

        currentMouthOpen += (targetMouthOpen - currentMouthOpen) * 0.35f

        var glowDeviation = 6f
        if (state == "LISTENING") {
            glowDeviation = (5f + sin(now / 300.0) * 3f).toFloat()
        }

        var dimOpacity = 1f
        if (state == "IDLE") dimOpacity = 0.45f
        if (state == "ERROR") dimOpacity = 0.35f

        return FaceAnimResult(
            gazeX = gazeX,
            gazeY = gazeY,
            blinkScaleY = blinkScaleY,
            mouthOpen = currentMouthOpen,
            glowDeviation = glowDeviation,
            dimOpacity = dimOpacity
        )
    }
}

