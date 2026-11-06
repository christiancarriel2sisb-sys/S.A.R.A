package com.sara.robot.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.sara.robot.core.AppLogger

data class FaceTrackingData(
    val faceDetected: Boolean,
    val angleOffsetDegrees: Float,
    val boundingBoxWidth: Int,
    val boundingBoxHeight: Int,
    val rawLeft: Float = 0f,
    val rawTop: Float = 0f,
    val rawRight: Float = 0f,
    val rawBottom: Float = 0f,
    val imageWidth: Float = 1f,
    val imageHeight: Float = 1f,
    val isFrontCamera: Boolean = true
)

/**
 * Analizador de visión en tiempo real para detección y seguimiento facial (CU-15 y CU-18).
 * Procesa frames de CameraX sin persistir video en disco (RF-07.1) y calcula el ángulo
 * de desviación para control del chasis motriz de Sara y renderizado del cuadro de fijación.
 */
class FaceAnalyzer(
    private val isFrontCamera: Boolean = true,
    private val onFaceTracked: (FaceTrackingData) -> Unit
) : ImageAnalysis.Analyzer {

    // Configuración optimizada para detección en tiempo real (Fast / Streaming)
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .build()

    private val detector = FaceDetection.getClient(options)

    // Campo de visión horizontal estimado en cámaras de smartphones convencionales (~65°)
    private val cameraHFOV = 65.0f

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val frameWidth = inputImage.width.toFloat()
        val frameHeight = inputImage.height.toFloat()

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // Tomamos el rostro de mayor tamaño relativo como objetivo prioritario
                    val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    primaryFace?.let { face ->
                        val faceCenterX = face.boundingBox.centerX().toFloat()

                        // Normalización entre -1.0 (extremo izquierdo) y +1.0 (extremo derecho)
                        val normalizedX = (faceCenterX - (frameWidth / 2f)) / (frameWidth / 2f)

                        // Desviación angular en grados respecto al eje central
                        val angleOffset = normalizedX * (cameraHFOV / 2f)

                        val data = FaceTrackingData(
                            faceDetected = true,
                            angleOffsetDegrees = angleOffset,
                            boundingBoxWidth = face.boundingBox.width(),
                            boundingBoxHeight = face.boundingBox.height(),
                            rawLeft = face.boundingBox.left.toFloat(),
                            rawTop = face.boundingBox.top.toFloat(),
                            rawRight = face.boundingBox.right.toFloat(),
                            rawBottom = face.boundingBox.bottom.toFloat(),
                            imageWidth = frameWidth,
                            imageHeight = frameHeight,
                            isFrontCamera = isFrontCamera
                        )
                        onFaceTracked(data)
                        AppLogger.info("SaraVision", "Target detectado: Desviación = ${"%.2f".format(angleOffset)}°")
                    }
                } else {
                    onFaceTracked(FaceTrackingData(false, 0f, 0, 0, imageWidth = frameWidth, imageHeight = frameHeight, isFrontCamera = isFrontCamera))
                }
            }
            .addOnFailureListener { e ->
                AppLogger.error("SaraVision", "Error analizando frame facial: ${e.message}")
            }
            .addOnCompleteListener {
                // OBLIGATORIO: Liberar el buffer para habilitar el siguiente frame
                imageProxy.close()
            }
    }
}

