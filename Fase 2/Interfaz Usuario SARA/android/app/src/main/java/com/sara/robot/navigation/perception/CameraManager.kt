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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestor de captura y control de framerate de CameraX (Capa 1).
 * Implementa control de saturación atómico (isProcessing) para evitar el agotamiento de buffers
 * y descarte DROP_LATEST para no saturar memoria ni CPU en teléfonos de gama baja.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val yoloDetector: YoloDetector
) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastInferenceTimestamp = 0L
    private val minFrameIntervalMs = 100L // ~10 FPS máx
    private val isProcessing = AtomicBoolean(false)

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

                        // 1. Control de cadencia temporal (8 a 10 FPS)
                        // 2. Control atómico de saturación: si el detector ML Kit aún está procesando
                        // el fotograma previo, descartamos INMEDIATAMENTE este frame para evitar buffer starvation.
                        if (now - lastInferenceTimestamp < minFrameIntervalMs || isProcessing.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        if (!isProcessing.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        lastInferenceTimestamp = now
                        val startTime = System.currentTimeMillis()

                        try {
                            yoloDetector.detectObjects(
                                imageProxy,
                                onProcessed = { obstacles, zLeft, zCenter, zRight ->
                                    val inferenceTime = System.currentTimeMillis() - startTime
                                    // Despachar al hilo principal de UI para evitar colisiones en Compose
                                    ContextCompat.getMainExecutor(context).execute {
                                        onFrameResults(obstacles, zLeft, zCenter, zRight, inferenceTime)
                                    }
                                },
                                onFinished = {
                                    isProcessing.set(false)
                                }
                            )
                        } catch (e: Exception) {
                            AppLogger.error("CameraManager", "Error en detección: ${e.message}")
                            try {
                                imageProxy.close()
                            } catch (_: Exception) {}
                            isProcessing.set(false)
                        }
                    }
                }

            val cameraSelector = if (useFrontCamera) {
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            } else {
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
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
        try {
            cameraExecutor.shutdown()
        } catch (_: Exception) {}
    }
}
