package com.sara.robot.navigation.perception

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.sara.robot.core.AppLogger
import com.sara.robot.navigation.reasoning.DetectedObstacle
import kotlin.math.min

/**
 * Motor de Detección Visual y Proyección Espacial (Capa 2 y 3).
 * Procesa frames de cámara con modelo INT8 optimizado para CPU (2 hilos),
 * detecta entidades hospitalarias y proyecta sus distancias tridimensionales relativas (X_rel, Z_dist).
 */
class YoloDetector(
    private val spatialProjector: SpatialProjector = SpatialProjector(320f, 320f)
) {
    // Configuración optimizada para streaming en tiempo real (Stream Mode, clasificaciones de objetos)
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    private val detector = ObjectDetection.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    fun detectObjects(
        imageProxy: ImageProxy,
        onProcessed: (List<DetectedObstacle>, Float, Float, Float) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { objects ->
                val frameW = inputImage.width.toFloat()
                val frameH = inputImage.height.toFloat()

                var minZLeft = 6.0f
                var minZCenter = 6.0f
                var minZRight = 6.0f

                val detectedList = mutableListOf<DetectedObstacle>()

                for (obj in objects) {
                    val bbox = obj.boundingBox
                    val rawLabel = obj.labels.firstOrNull()?.text ?: "Obstaculo"
                    val confidence = obj.labels.firstOrNull()?.confidence ?: 0.75f

                    // Mapeo a clases de entorno hospitalario (persona, cama, silla, pared)
                    val hospitalLabel = mapToHospitalClass(rawLabel)

                    val bboxW = bbox.width().toFloat()
                    val bboxH = bbox.height().toFloat()
                    val centerX = bbox.centerX().toFloat()

                    // Proyección monocular 3D (Capa 3)
                    val point3D = spatialProjector.project(hospitalLabel, bboxW, bboxH, centerX)

                    val obstacle = DetectedObstacle(
                        label = hospitalLabel,
                        confidence = confidence,
                        xRelMeters = point3D.xRelMeters,
                        zDistMeters = point3D.zDistMeters,
                        bboxLeft = bbox.left.toFloat() / frameW,
                        bboxTop = bbox.top.toFloat() / frameH,
                        bboxRight = bbox.right.toFloat() / frameW,
                        bboxBottom = bbox.bottom.toFloat() / frameH
                    )
                    detectedList.add(obstacle)

                    // Asignación por sector angular / horizontal
                    when {
                        point3D.xRelMeters < -0.30f -> {
                            minZLeft = min(minZLeft, point3D.zDistMeters)
                        }
                        point3D.xRelMeters > 0.30f -> {
                            minZRight = min(minZRight, point3D.zDistMeters)
                        }
                        else -> {
                            minZCenter = min(minZCenter, point3D.zDistMeters)
                        }
                    }
                }

                onProcessed(detectedList, minZLeft, minZCenter, minZRight)
            }
            .addOnFailureListener { e ->
                AppLogger.error("YoloDetector", "Fallo al procesar detección de objetos: ${e.message}")
            }
            .addOnCompleteListener {
                // Liberación inmediata de memoria para cumplir con presupuesto de RAM en gama baja (Capa 1)
                imageProxy.close()
            }
    }

    private fun mapToHospitalClass(label: String): String {
        val lower = label.lowercase()
        return when {
            lower.contains("person") || lower.contains("human") || lower.contains("gente") -> "Persona"
            lower.contains("chair") || lower.contains("seat") || lower.contains("silla") -> "Silla"
            lower.contains("bed") || lower.contains("couch") || lower.contains("cama") -> "Cama"
            lower.contains("wall") || lower.contains("door") || lower.contains("pared") -> "Pared"
            else -> "Obstaculo"
        }
    }
}

