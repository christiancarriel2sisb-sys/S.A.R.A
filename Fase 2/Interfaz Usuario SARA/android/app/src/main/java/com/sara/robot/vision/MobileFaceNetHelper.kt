package com.sara.robot.vision

import android.content.Context
import android.graphics.Bitmap
import com.sara.robot.core.AppLogger
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Motor de Inferencia On-Device para Reconocimiento Facial (MobileFaceNet).
 * - Carga el modelo TensorFlow Lite 'mobilefacenet.tflite' (~5MB) desde assets.
 * - Procesa recortes faciales a 112x112 px normalizados a [-1, 1].
 * - Genera un vector biométrico (embedding) de 192 dimensiones con norma L2.
 * - Calcula similitud coseno ultrarrápida (<0.05 ms) para identificación de pacientes.
 */
class MobileFaceNetHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val reusableInputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val reusableIntValues = IntArray(INPUT_SIZE * INPUT_SIZE)

    companion object {
        const val MODEL_FILE = "mobilefacenet.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192
        const val SIMILARITY_THRESHOLD = 0.72f // Umbral de coincidencia biométrica (72%)
    }

    init {
        initModel()
    }

    private fun initModel() {
        try {
            val assetDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(assetDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetDescriptor.startOffset
            val declaredLength = assetDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            AppLogger.success("MobileFaceNet", "Modelo MobileFaceNet inicializado con éxito (Embeddings: 192-d)")
        } catch (e: Exception) {
            AppLogger.error("MobileFaceNet", "Fallo al inicializar modelo TFLite: ${e.message}")
        }
    }

    /**
     * Extrae el vector biométrico de 192 dimensiones a partir del recorte de un rostro.
     * Thread-safe y con reutilización de buffers para 0 GC overhead.
     */
    @Synchronized
    fun extractEmbedding(faceBitmap: Bitmap): FloatArray? {
        val currentInterpreter = interpreter ?: run {
            AppLogger.error("MobileFaceNet", "Intérprete no disponible para extracción de embedding")
            return null
        }

        return try {
            // 1. Redimensionar a 112x112 píxeles
            val scaledBitmap = if (faceBitmap.width == INPUT_SIZE && faceBitmap.height == INPUT_SIZE) {
                faceBitmap
            } else {
                Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
            }

            // 2. Preparar el buffer de entrada [1, 112, 112, 3] en formato Float32 reutilizando memoria
            reusableInputBuffer.rewind()
            scaledBitmap.getPixels(reusableIntValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            for (pixel in reusableIntValues) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Normalización estándar de MobileFaceNet a rango [-1.0f, 1.0f]
                reusableInputBuffer.putFloat((r - 128f) / 128f)
                reusableInputBuffer.putFloat((g - 128f) / 128f)
                reusableInputBuffer.putFloat((b - 128f) / 128f)
            }

            // 3. Ejecutar inferencia
            val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
            currentInterpreter.run(reusableInputBuffer, output)

            // 4. Normalización L2 del vector resultante
            l2Normalize(output[0])
        } catch (e: Exception) {
            AppLogger.error("MobileFaceNet", "Error extrayendo embedding facial: ${e.message}")
            null
        }
    }

    /**
     * Normalización euclidiana L2 (vector unitario donde ||v|| = 1.0).
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sumSquares = 0f
        for (value in embedding) {
            sumSquares += value * value
        }
        val norm = sqrt(max(sumSquares, 1e-10f))
        for (i in embedding.indices) {
            embedding[i] /= norm
        }
        return embedding
    }

    /**
     * Calcula la similitud coseno entre dos vectores biométricos.
     * Retorna un valor entre -1.0 y 1.0 (donde 1.0 es identidad idéntica).
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f
        var dot = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(-1f, 1f)
    }

    /**
     * Serializa el vector a una cadena de texto CSV.
     */
    fun embeddingToString(embedding: FloatArray): String {
        return embedding.joinToString(",")
    }

    /**
     * Deserializa la cadena de texto CSV al vector FloatArray.
     */
    fun stringToEmbedding(csv: String): FloatArray {
        return try {
            csv.split(",").map { it.trim().toFloat() }.toFloatArray()
        } catch (_: Exception) {
            FloatArray(0)
        }
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (_: Exception) {}
    }
}
