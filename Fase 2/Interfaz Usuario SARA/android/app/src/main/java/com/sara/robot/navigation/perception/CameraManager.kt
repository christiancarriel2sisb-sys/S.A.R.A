package com.sara.robot.navigation.perception

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.sara.robot.core.AppLogger
import java.util.concurrent.Executors

/**
 * Gestor de captura y control de framerate de CameraX (Capa 1).
 * Configura resolución ligera de 320x320 o 416x416 y limita la tasa de inferencia
 * a 8-10 FPS con descarte DROP_LATEST para no saturar memoria ni CPU en teléfonos de gama baja.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val yoloDetector: YoloDetector
) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastInferenceTimestamp = 0L
    private val minFrameIntervalMs = 100L // ~10 FPS máx

    fun startCamera(
        previewView: PreviewView,
        useFrontCamera: Boolean = false,
        onFrameResults: (List<com.sara.robot.navigation.reasoning.DetectedObstacle>, Float, Float, Float, Long) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Resolución ligera optimizada para inferencia en tiempo real (Capa 1)
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 320))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val now = System.currentTimeMillis()
                        // Control de framerate: 8 a 10 FPS
                        if (now - lastInferenceTimestamp < minFrameIntervalMs) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        lastInferenceTimestamp = now

                        val startTime = System.currentTimeMillis()
                        yoloDetector.detectObjects(imageProxy) { obstacles, zLeft, zCenter, zRight ->
                            val inferenceTime = System.currentTimeMillis() - startTime
                            onFrameResults(obstacles, zLeft, zCenter, zRight, inferenceTime)
                        }
                    }
                }

            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                AppLogger.success("CameraManager", "Cámara de navegación vinculada con éxito (Frontal=$useFrontCamera, 320x320 @ 10 FPS)")
            } catch (e: Exception) {
                AppLogger.error("CameraManager", "Fallo al vincular cámara de navegación: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}

