package com.sara.robot.vision

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.sara.robot.core.AppLogger
import com.sara.robot.data.RegisteredPersonProfile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.tan

/**
 * Información individual de cada rostro detectado en el cuadro actual.
 * Permite a Sara diferenciar a múltiples personas simultáneamente en pantalla,
 * saber quién está registrado y quién es "Sin registro", y conocer el ángulo (θ)
 * y distancia (Z) hacia la persona objetivo para desplazarse hacia ella.
 */
data class DetectedFaceInfo(
    val trackingId: Int,
    val boundingBox: Rect,
    val angleOffsetDegrees: Float,
    val distanceMeters: Float,
    val isRegistered: Boolean,
    val pacienteId: Long? = null,
    val nombre: String = "Sin registro",
    val displayLabel: String = "Sin registro",
    val similarity: Float = 0f,
    val faceCropBitmap: Bitmap? = null
)

/**
 * Resultado del análisis facial multirrostro en tiempo real.
 */
data class UnifiedVisionResult(
    val faces: List<DetectedFaceInfo> = emptyList(),
    val frameWidth: Int = 640,
    val frameHeight: Int = 360,
    val inferenceTimeMs: Long = 0L
) {
    val faceDetected: Boolean get() = faces.isNotEmpty()
    val unregisteredFaces: List<DetectedFaceInfo> get() = faces.filter { !it.isRegistered }
    val registeredFaces: List<DetectedFaceInfo> get() = faces.filter { it.isRegistered }
}

/**
 * Analizador Dedicado de Reconocimiento y Diferenciación Facial Multirrostro (Cámara Frontal).
 * - Se eliminó la detección de objetos para dedicar el 100% del presupuesto de cómputo
 *   al seguimiento e identificación biométrica fluida a 640x360 px.
 * - Detecta TODOS los rostros presentes en el campo visual simultáneamente.
 * - Extrae el vector de 192 dimensiones con MobileFaceNet para cada rostro y ejecuta
 *   emparejamiento exclusivo 1 a 1 (Bipartite Greedy Matching) contra los perfiles de SQLite.
 * - Si un rostro no coincide con ningún registro en la BD, se etiqueta estrictamente como "Sin registro".
 */
class UnifiedVisionAnalyzer(
    private val mobileFaceNetHelper: MobileFaceNetHelper,
    private val cameraHFOV: Float = 65.0f,
    var isFrontCamera: Boolean = true,
    private val onFrameProcessed: (UnifiedVisionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val faceOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.12f)
        .enableTracking()
        .build()

    private val faceDetector = FaceDetection.getClient(faceOptions)

    private val isProcessing = AtomicBoolean(false)
    private var lastInferenceTimestamp = 0L
    private val minFrameIntervalMs = 65L // ~15 FPS fluidos (solo rostros a 640x360)

    // Perfiles registrados en SQLite mantenidos en RAM para comparación instantánea
    @Volatile
    private var registeredProfiles: List<RegisteredPersonProfile> = emptyList()

    // Caché temporal de embeddings por trackingId para mantener 15 FPS sin recalcular MobileFaceNet en cada milisegundo
    private data class CachedFaceData(
        val timestamp: Long,
        val embedding: FloatArray,
        val cropBitmap: Bitmap
    )
    private val trackingCache = ConcurrentHashMap<Int, CachedFaceData>()
    private val embeddingRefreshIntervalMs = 350L

    fun updateRegisteredProfiles(profiles: List<RegisteredPersonProfile>) {
        registeredProfiles = profiles
        trackingCache.clear() // Forzar re-evaluación inmediata al registrar o borrar una persona
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        if (now - lastInferenceTimestamp < minFrameIntervalMs || isProcessing.get()) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        lastInferenceTimestamp = now
        val startTime = System.currentTimeMillis()

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = try {
            InputImage.fromMediaImage(mediaImage, rotationDegrees)
        } catch (e: Exception) {
            AppLogger.error("UnifiedVision", "Error convirtiendo frame: ${e.message}")
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val frameW = inputImage.width.toFloat()
        val frameH = inputImage.height.toFloat()

        faceDetector.process(inputImage)
            .addOnCompleteListener { faceTask ->
                try {
                    val detectedFaces = if (faceTask.isSuccessful) {
                        faceTask.result ?: emptyList()
                    } else {
                        emptyList()
                    }

                    if (detectedFaces.isEmpty()) {
                        val elapsedMs = System.currentTimeMillis() - startTime
                        onFrameProcessed(
                            UnifiedVisionResult(
                                faces = emptyList(),
                                frameWidth = frameW.toInt(),
                                frameHeight = frameH.toInt(),
                                inferenceTimeMs = elapsedMs
                            )
                        )
                        return@addOnCompleteListener
                    }

                    // Convertir frame una sola vez para recortar todos los rostros detectados
                    val uprightFrameBitmap = try {
                        rotateBitmap(imageProxy.toBitmap(), rotationDegrees)
                    } catch (e: Exception) {
                        AppLogger.error("UnifiedVision", "Error rotando bitmap de cámara: ${e.message}")
                        null
                    }

                    // Longitud focal estimada para calcular distancia Z a cada rostro (altura media rostro humano ~0.22m)
                    val focalLengthPx = (frameW / 2f) / tan(Math.toRadians(cameraHFOV.toDouble() / 2.0)).toFloat()
                    val sign = if (isFrontCamera) -1f else 1f

                    // 1. Extraer o recuperar el embedding (192-d) de cada rostro presente en la escena
                    data class RawFaceCandidate(
                        val index: Int,
                        val trackingId: Int,
                        val bbox: Rect,
                        val angleOffset: Float,
                        val distanceMeters: Float,
                        val embedding: FloatArray?,
                        val cropBitmap: Bitmap?
                    )

                    val rawCandidates = detectedFaces.mapIndexed { idx, face ->
                        val tId = face.trackingId ?: (idx + 1000)
                        val bbox = face.boundingBox
                        val faceCenterX = bbox.centerX().toFloat()
                        val normalizedX = (faceCenterX - (frameW / 2f)) / (frameW / 2f)
                        val angleOffset = normalizedX * (cameraHFOV / 2f) * sign

                        val faceHeightPx = max(bbox.height().toFloat(), 12f)
                        val distanceMeters = ((0.22f * focalLengthPx) / faceHeightPx).coerceIn(0.30f, 6.0f)

                        val cached = trackingCache[tId]
                        val (embedding, crop) = if (cached != null && (now - cached.timestamp) < embeddingRefreshIntervalMs) {
                            Pair(cached.embedding, cached.cropBitmap)
                        } else {
                            val newCrop = uprightFrameBitmap?.let { cropFaceFromUpright(it, bbox) }
                            val newEmb = newCrop?.let { mobileFaceNetHelper.extractEmbedding(it) }
                            if (newEmb != null && newCrop != null) {
                                trackingCache[tId] = CachedFaceData(now, newEmb, newCrop)
                            }
                            Pair(newEmb ?: cached?.embedding, newCrop ?: cached?.cropBitmap)
                        }

                        RawFaceCandidate(
                            index = idx,
                            trackingId = tId,
                            bbox = bbox,
                            angleOffset = angleOffset,
                            distanceMeters = distanceMeters,
                            embedding = embedding,
                            cropBitmap = crop
                        )
                    }

                    // Limpiar trackingIds antiguos que ya salieron del cuadro
                    val activeIds = rawCandidates.map { it.trackingId }.toSet()
                    trackingCache.keys.retainAll(activeIds)

                    // 2. Emparejamiento Exclusivo 1 a 1 (Diferenciación entre múltiples rostros en escena)
                    val currentProfiles = registeredProfiles
                    data class MatchScore(
                        val faceIndex: Int,
                        val profile: RegisteredPersonProfile,
                        val similarity: Float
                    )

                    val allPossibleMatches = mutableListOf<MatchScore>()
                    var bestSimilarityPerFace = FloatArray(rawCandidates.size) { 0f }

                    for (candidate in rawCandidates) {
                        val emb = candidate.embedding ?: continue
                        for (profile in currentProfiles) {
                            val sim = mobileFaceNetHelper.cosineSimilarity(emb, profile.embedding)
                            if (sim > bestSimilarityPerFace[candidate.index]) {
                                bestSimilarityPerFace[candidate.index] = sim
                            }
                            if (sim >= MobileFaceNetHelper.SIMILARITY_THRESHOLD) {
                                allPossibleMatches.add(MatchScore(candidate.index, profile, sim))
                            }
                        }
                    }

                    // Ordenar de mayor a menor similitud para asignar cada identidad registrada a lo sumo a 1 rostro físico
                    allPossibleMatches.sortByDescending { it.similarity }
                    val assignedFaceIndices = mutableSetOf<Int>()
                    val assignedPatientIds = mutableSetOf<Long>()
                    val matchedProfileByFaceIndex = mutableMapOf<Int, MatchScore>()

                    for (match in allPossibleMatches) {
                        val pacId = match.profile.paciente.Id_paciente
                        if (match.faceIndex !in assignedFaceIndices && pacId !in assignedPatientIds) {
                            assignedFaceIndices.add(match.faceIndex)
                            assignedPatientIds.add(pacId)
                            matchedProfileByFaceIndex[match.faceIndex] = match
                        }
                    }

                    // Contar cuántos rostros sin registro hay para numerarlos si hay más de uno (#1, #2...)
                    val unregisteredCount = rawCandidates.count { it.index !in matchedProfileByFaceIndex }
                    var unregCounter = 1

                    val finalFaces = rawCandidates.map { candidate ->
                        val match = matchedProfileByFaceIndex[candidate.index]
                        if (match != null) {
                            val pac = match.profile.paciente
                            val pct = (match.similarity * 100).toInt()
                            DetectedFaceInfo(
                                trackingId = candidate.trackingId,
                                boundingBox = candidate.bbox,
                                angleOffsetDegrees = candidate.angleOffset,
                                distanceMeters = candidate.distanceMeters,
                                isRegistered = true,
                                pacienteId = pac.Id_paciente,
                                nombre = pac.Nombre,
                                displayLabel = "${pac.Nombre} ($pct%)",
                                similarity = match.similarity,
                                faceCropBitmap = candidate.cropBitmap
                            )
                        } else {
                            val unregLabel = if (unregisteredCount > 1) {
                                "Sin registro #${unregCounter++}"
                            } else {
                                "Sin registro"
                            }
                            DetectedFaceInfo(
                                trackingId = candidate.trackingId,
                                boundingBox = candidate.bbox,
                                angleOffsetDegrees = candidate.angleOffset,
                                distanceMeters = candidate.distanceMeters,
                                isRegistered = false,
                                pacienteId = null,
                                nombre = "Sin registro",
                                displayLabel = unregLabel,
                                similarity = bestSimilarityPerFace[candidate.index],
                                faceCropBitmap = candidate.cropBitmap
                            )
                        }
                    }

                    val elapsedMs = System.currentTimeMillis() - startTime
                    onFrameProcessed(
                        UnifiedVisionResult(
                            faces = finalFaces,
                            frameWidth = frameW.toInt(),
                            frameHeight = frameH.toInt(),
                            inferenceTimeMs = elapsedMs
                        )
                    )
                } catch (e: Exception) {
                    AppLogger.error("UnifiedVision", "Error procesando rostros múltiples: ${e.message}")
                } finally {
                    imageProxy.close()
                    isProcessing.set(false)
                }
            }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat(), bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropFaceFromUpright(upright: Bitmap, box: Rect): Bitmap? {
        // Añadir un margen de 8% para capturar bien el contorno facial completo para MobileFaceNet
        val padW = (box.width() * 0.08f).toInt()
        val padH = (box.height() * 0.08f).toInt()

        val x = (box.left - padW).coerceIn(0, upright.width - 1)
        val y = (box.top - padH).coerceIn(0, upright.height - 1)
        val w = (box.width() + padW * 2).coerceAtMost(upright.width - x)
        val h = (box.height() + padH * 2).coerceAtMost(upright.height - y)

        return if (w > 24 && h > 24) {
            Bitmap.createBitmap(upright, x, y, w, h)
        } else null
    }
}
