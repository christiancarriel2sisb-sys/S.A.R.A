package com.sara.robot.navigation.reasoning

import com.sara.robot.core.AppLogger
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Clases de salida (Módulos del Sistema) que la Red Neuronal de Jev puede orquestar.
 */
enum class JevIntentModule(
    val index: Int,
    val displayName: String,
    val badgeColorHex: Long,
    val isLocalZeroToken: Boolean
) {
    RECORDATORIO(0, "Módulo de Recordatorios", 0xFFFFD32A, true),
    MUSICA(1, "Módulo de Música (Reproducir)", 0xFFFF4757, true),
    MUSICA_PAUSA_QUITAR(2, "Módulo de Música (Pausar / Quitar)", 0xFFFF6B81, true),
    CHAT_FAMILIAR(3, "Módulo de Chat Familiar (Jev + Gemini)", 0xFF8A7DFF, true),
    MOVIMIENTO_ROSTRO(4, "Módulo de Movimiento y Búsqueda Facial", 0xFF00F0FF, true),
    CONVERSACION_LIBRE(5, "Conversación Abierta (Fallback Gemini)", 0xFF2ED573, false);

    companion object {
        fun fromIndex(idx: Int): JevIntentModule = entries.first { it.index == idx }
    }
}

/**
 * Ejemplo de entrenamiento etiquetado para la red neuronal de Jev.
 */
data class JevTrainingSample(
    val phrase: String,
    val label: JevIntentModule
)

/**
 * Detalle de cada token/raíz del diccionario activado durante la inferencia de una frase.
 */
data class ActivatedDictionaryToken(
    val token: String,
    val tfIdfValue: Float,
    val synapticWeightForWinner: Float
)

/**
 * Resultado completo de una predicción de la Red Neuronal Jev.
 */
data class JevNeuralPrediction(
    val inputPhrase: String,
    val normalizedTokens: List<String>,
    val activatedDictionaryTokens: List<ActivatedDictionaryToken>,
    val winningModule: JevIntentModule,
    val confidence: Float,
    val probabilities: Map<JevIntentModule, Float>,
    val shouldExecuteLocallyZeroTokens: Boolean,
    val inferenceTimeMicros: Long
)

/**
 * Métricas del estado de entrenamiento de la Red Neuronal Jev.
 */
data class JevTrainingMetrics(
    val vocabularySize: Int,
    val totalSamples: Int,
    val totalEpochsTrained: Int,
    val currentLoss: Float,
    val trainingAccuracy: Float,
    val lastTrainingTimeMs: Long
)

/**
 * ============================================================================
 * RED NEURONAL DE DETECCIÓN DE INTENCIONES Y ORQUESTACIÓN DEL SISTEMA (MODELO JEV)
 * Ruta del archivo:
 * android/app/src/main/java/com/sara/robot/navigation/reasoning/JevNeuralIntentClassifier.kt
 * ============================================================================
 *
 * Arquitectura Matemática On-Device (0 MB externos, entrenamiento e inferencia en CPU local):
 * 1. Preprocesamiento Morfológico y Lematización (Stemming en Español):
 *    - Normaliza acentos y puntuación.
 *    - Reduce palabras conjugadas o derivados a su raíz semántica (ej: "medicamento", "medicamentos",
 *      "medicina" -> "medicam"; "pastillas", "pastillita" -> "pastill"; "recuerdame", "recordar" -> "record").
 *    - Extrae Unigramas (palabras), Bigramas (pares contextuales como "a_las", "tengo_que", "dile_a")
 *      y Anclas Sintácticas Temporales ("<HORA_NUMERICA>" cuando detecta números de hora como "10", "5:30").
 *
 * 2. Diccionario Dinámico de Datos con Pesos TF-IDF (Term Frequency - Inverse Document Frequency):
 *    - Construye automáticamente el vocabulario V a partir del dataset de frases.
 *    - Calcula el vector IDF: idf(t) = ln((1 + N) / (1 + df(t))) + 1.0, penalizando palabras genéricas
 *      y premiando términos exclusivos de cada módulo.
 *
 * 3. Capa Neuronal Densa (Pesos Sinápticos W y Sesgos b) + Activación Softmax:
 *    - Logits: z_k = sum_i (W[k][i] * x[i]) + b[k]
 *    - Probabilidad Softmax: P(y = k | X) = exp(z_k) / sum_j exp(z_j)
 *
 * 4. Entrenamiento Real en Dispositivo mediante Backpropagation (Gradiente Descendente + Cross-Entropy):
 *    - Función de pérdida: L = -ln(P(y = y_real)) + (lambda / 2) * ||W||^2
 *    - Gradiente de salida: delta_k = P(y = k | X) - 1(y_real == k)
 *    - Actualización sináptica: W[k][i] = W[k][i] - lr * (delta_k * x[i] + lambda * W[k][i])
 */
object JevNeuralIntentClassifier {

    private val trainingSamples = mutableListOf<JevTrainingSample>()

    // Diccionario de datos: Token -> Índice en el vector de entrada X
    private var vocabulary = LinkedHashMap<String, Int>()
    private var idfWeights = FloatArray(0)

    // Pesos sinápticos de la Red Neuronal: W[numClasses][vocabSize] y sesgos b[numClasses]
    private val numClasses = JevIntentModule.entries.size
    private var weightsW: Array<FloatArray> = Array(numClasses) { FloatArray(0) }
    private var biasesB: FloatArray = FloatArray(numClasses)

    private var totalEpochs = 0
    private var latestMetrics = JevTrainingMetrics(0, 0, 0, 0f, 0f, 0L)

    // Umbral de confianza para que Jev ejecute el módulo localmente a 0 tokens
    const val CONFIDENCE_THRESHOLD = 0.65f

    // Palabras vacías (stopwords) que no aportan intención cuando van solas
    private val stopWords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del",
        "por", "favor", "sara", "oye", "hola", "mi", "mis", "tu", "tus", "su",
        "lo", "le", "se", "al", "es", "son", "con", "para", "en", "y", "o"
    )

    // Bigramas contextuales clave inicializados ANTES del bloque init
    private val importantBigrams = setOf(
        "a_las", "a_la", "tengo_que", "me_toca", "no_olvides", "se_olvide",
        "dile_a", "mandale_a", "escribele_a", "avisale_a", "recuerdale_a",
        "mensaje_a", "mensaje_para", "enviar_mensaje", "mandar_mensaje", "quiero_mandar", "quiero_enviar",
        "pon_musica", "quiero_escuchar", "pon_canciones",
        "pon_pausa", "pausa_la", "quita_la", "quitar_la", "saca_la", "apaga_la",
        "para_la", "parar_la", "deten_la", "corta_la", "silencia_la", "deja_de", "basta_de",
        "reanuda_la", "continua_la",
        "busca_a", "ve_a", "ven_para", "para_aca", "acercate_a", "gira_a",
        "me_siento", "cuentame_un", "cuentame_una", "como_estas"
    )

    init {
        cargarDatasetBaseYEntrenar()
    }

    /**
     * Inicializa el dataset base con frases naturales en español (incluyendo giros chilenos
     * y expresiones indirectas como "a las 10, tengo que tomar mi medicamento") y entrena la red.
     */
    @Synchronized
    fun cargarDatasetBaseYEntrenar() {
        trainingSamples.clear()

        // 1. MÓDULO DE RECORDATORIOS (Directos e indirectos con horas, remedios, citas)
        val frasesRecordatorio = listOf(
            "hazme un recordatorio para tomar agua a las 5",
            "recuerdame tomar mi pastilla a las 8 de la manana",
            "a las 10 tengo que tomar mi medicamento",
            "a las 12 me toca el remedio de la presion",
            "no se me puede olvidar la pastilla a las 9",
            "pon una alarma para las 7 de la tarde",
            "avisame en 20 minutos que debo apagar la cocina",
            "despiertame manana a las 8",
            "agendar recordatorio de control medico",
            "tengo que inyectarme insulina a las 6",
            "acuerdate que mas rato me toca tomar las gotas",
            "programar alerta para mi tratamiento a las 3",
            "en 10 minutos tengo que revisar el horno",
            "me toca tomar vitamina a la 1 de la tarde",
            "anota un recordatorio para llamar al doctor a las 4"
        )

        // 2. MÓDULO DE MÚSICA (REPRODUCIR / BUSCAR CANCIONES)
        val frasesMusica = listOf(
            "pon musica de jose jose",
            "reproduce una cancion alegre",
            "quiero escuchar los prisioneros",
            "ponme un tema de rock clasico",
            "pon canciones de violeta parra",
            "toca algo de boleros antiguos",
            "reproducir lista de musica relajante",
            "quiero oir musica romantica",
            "pon musica chilena",
            "reproduce musica instrumental",
            "pon una cancion de camilo sesto",
            "quiero escuchar musica de los anos 70"
        )

        // 3. MÓDULO DE MÚSICA (PAUSAR / QUITAR / DETENER / REANUDAR)
        val frasesMusicaPausaQuitar = listOf(
            "pausa",
            "pon pausa",
            "pausa la musica por favor",
            "pausa la cancion",
            "pausar la musica",
            "quita la musica",
            "quitar la musica",
            "quitar musica",
            "quita la cancion",
            "quitalo por favor",
            "quitala",
            "saca la musica",
            "sacar la musica",
            "apaga la musica",
            "apagar el reproductor",
            "deten la musica",
            "deten la cancion",
            "para la musica",
            "parar la musica",
            "corta la musica",
            "silencia la musica",
            "silencia el reproductor de musica",
            "deja de tocar musica",
            "basta de musica",
            "reanuda la musica",
            "continua la musica"
        )

        // 4. MÓDULO DE CHAT FAMILIAR (ORQUESTADO POR JEV + EXTRACCIÓN CON GEMINI)
        val frasesChat = listOf(
            "dile a claudia que ya almorce",
            "mandale un mensaje a pedro que estoy bien",
            "escribele a mi hija que venga a verme",
            "avisale a claudia que necesito mis lentes",
            "enviar mensaje a mi familiar",
            "quiero mandar un mensaje a claudia",
            "quiero enviarle un mensaje a rodrigo",
            "mandar un mensaje para patricia diciendo que me siento bien",
            "dile a mi hijo que lo quiero mucho",
            "mandale un texto a pedro diciendo que llegue",
            "leeme los mensajes de mi familia",
            "que me escribio claudia en el chat",
            "respondele a pedro que muchas gracias",
            "contactar a mi familiar por chat",
            "dile a mi mama que tome mi desayuno",
            "recuerdale a claudia que traiga mis remedios"
        )

        // 4. MÓDULO DE MOVIMIENTO Y BÚSQUEDA FACIAL
        val frasesMovimiento = listOf(
            "busca a christian en la habitacion",
            "ve a buscar a pedro",
            "acercate hacia mi sara",
            "ven para aca por favor",
            "gira a la derecha para mirarme",
            "gira a la izquierda",
            "detente ahi mismo",
            "frena los motores",
            "encuentra donde esta christian",
            "camina hacia el paciente",
            "escanea mi rostro con la camara",
            "reconoce quien esta frente a ti",
            "muevete hacia adelante despacio",
            "rote en 360 grados para buscarme"
        )

        // 5. CONVERSACIÓN ABIERTA (FALLBACK A GEMINI)
        val frasesConversacion = listOf(
            "como estas hoy sara",
            "me siento un poco solo y triste",
            "cuentame un chiste divertido",
            "que significa tener hipertension arterial",
            "cuentame una historia bonita antes de dormir",
            "cual es la capital de francia",
            "hoy hizo mucho frio en la manana",
            "gracias por hacerme compania",
            "que opinas del clima de hoy",
            "explicame por que es bueno caminar todos los dias",
            "hablemos un rato de la vida",
            "me duele un poquito la espalda cuando me levanto"
        )

        frasesRecordatorio.forEach { trainingSamples.add(JevTrainingSample(it, JevIntentModule.RECORDATORIO)) }
        frasesMusica.forEach { trainingSamples.add(JevTrainingSample(it, JevIntentModule.MUSICA)) }
        frasesMusicaPausaQuitar.forEach { trainingSamples.add(JevTrainingSample(it, JevIntentModule.MUSICA_PAUSA_QUITAR)) }
        frasesChat.forEach { trainingSamples.add(JevTrainingSample(it, JevIntentModule.CHAT_FAMILIAR)) }
        frasesMovimiento.forEach { trainingSamples.add(JevTrainingSample(it, JevIntentModule.MOVIMIENTO_ROSTRO)) }
        frasesConversacion.forEach { trainingSamples.add(JevTrainingSample(it, JevIntentModule.CONVERSACION_LIBRE)) }

        totalEpochs = 0
        reconstruirDiccionarioYEntrenar(epochs = 140, learningRate = 0.85f)
    }

    /**
     * Paso 1: Tokenizador Morfológico, Lematizador (Stemming) y Extractor de Bigramas.
     * Convierte cualquier frase cruda en una lista de tokens canónicos del diccionario.
     */
    fun tokenizeAndExtractFeatures(rawText: String): List<String> {
        val normalized = Normalizer.normalize(rawText.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        val tokens = mutableListOf<String>()

        // 1. Detectar anclas numéricas de tiempo (ej: "a las 10", "en 15 minutos", "18:30")
        if (Regex("\\b(a la|a las)\\s+\\d{1,2}\\b").containsMatchIn(normalized) ||
            Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(normalized) ||
            Regex("\\ben\\s+\\d+\\s+(minuto|minutos|hora|horas)\\b").containsMatchIn(normalized)
        ) {
            tokens.add("<PATRON_HORA>")
        }

        // Extraer palabras limpias
        val rawWords = normalized
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        // 2. Generar Bigramas contextuales clave ANTES de filtrar stopwords
        // (ej: "a_las", "tengo_que", "me_toca", "dile_a", "busca_a", "ve_a", "para_aca")
        for (i in 0 until rawWords.size - 1) {
            val w1 = rawWords[i]
            val w2 = rawWords[i + 1]
            val bigram = "${w1}_${w2}"
            if (bigram in importantBigrams) {
                tokens.add(bigram)
            }
        }

        // 3. Aplicar Stemming (reducción a raíz semántica) a cada palabra significativa
        for (word in rawWords) {
            if (word.all { it.isDigit() }) {
                tokens.add("<NUMERO>")
                continue
            }
            if (word in stopWords || word.length < 2) continue
            val stem = spanishStem(word)
            tokens.add(stem)
        }

        return tokens
    }

    /**
     * Lematizador / Stemmer rápido en Español para agrupar familias de palabras
     * bajo un mismo nodo del diccionario neuronal.
     */
    private fun spanishStem(word: String): String {
        return when {
            word.startsWith("medicam") || word.startsWith("medicin") || word.startsWith("medicac") -> "medicam"
            word.startsWith("pastill") -> "pastill"
            word.startsWith("remedi") -> "remedi"
            word.startsWith("recordat") || word.startsWith("recuerdam") || word.startsWith("recordar") -> "record"
            word.startsWith("recuerdal") -> "avis_chat"
            word.startsWith("acuerd") || word.startsWith("acord") -> "acord"
            word.startsWith("alarm") || word.startsWith("alert") -> "alarm"
            word.startsWith("avisam") -> "avis_rec"
            word.startsWith("avisal") -> "avis_chat"
            word.startsWith("avis") -> "avis"
            word.startsWith("despiert") || word.startsWith("despert") -> "despert"
            word.startsWith("inyec") || word.startsWith("insul") -> "inyec"
            word.startsWith("tratamient") -> "tratamient"
            word.startsWith("vitamin") -> "vitamin"
            word.startsWith("tomar") || word.startsWith("tome") || word.startsWith("tomo") -> "tomar"
            word.startsWith("music") -> "music"
            word.startsWith("cancion") || word.startsWith("cantant") -> "cancion"
            word.startsWith("reproduc") -> "reproduc"
            word.startsWith("escuch") || word.startsWith("oir") -> "escuch"
            word.startsWith("paus") -> "paus"
            word.startsWith("quit") -> "quit"
            word.startsWith("sac") || word.startsWith("saqu") -> "sac"
            word.startsWith("apag") -> "apag"
            word.startsWith("cort") -> "cort"
            word.startsWith("parar") -> "parar_mus"
            word.startsWith("silenc") || word.startsWith("calla") -> "silenc"
            word.startsWith("reanud") || word.startsWith("continu") -> "reanud"
            word == "basta" || word == "stop" -> "basta_mus"
            word.startsWith("volum") -> "volum"
            word.startsWith("mensaj") || word.startsWith("text") -> "mensaj"
            word.startsWith("escrib") || word.startsWith("respond") -> "escrib"
            word.startsWith("mand") || word.startsWith("envi") -> "envi"
            word.startsWith("dile") || word.startsWith("decir") -> "dile"
            word.startsWith("famili") || word.startsWith("hij") || word.startsWith("mam") -> "famili"
            word.startsWith("busc") || word.startsWith("encuentr") -> "busc"
            word.startsWith("acerc") || word.startsWith("camina") || word.startsWith("muev") -> "acerc"
            word.startsWith("gir") || word.startsWith("rot") -> "gir"
            word.startsWith("deten") || word.startsWith("fren") -> "deten"
            word.startsWith("rostr") || word.startsWith("car") || word.startsWith("escan") -> "rostr"
            word.startsWith("trist") || word.startsWith("sol") || word.startsWith("dolor") || word.startsWith("duel") -> "emocion"
            word.startsWith("chist") || word.startsWith("histori") || word.startsWith("cuent") -> "relat"
            else -> word
        }
    }

    /**
     * Paso 2 y 4: Reconstruye el Diccionario de Datos (TF-IDF) y entrena la Red Neuronal
     * mediante Backpropagation (Gradiente Descendente Estocástico con Regularización L2).
     */
    @Synchronized
    fun reconstruirDiccionarioYEntrenar(
        epochs: Int = 100,
        learningRate: Float = 0.8f
    ): JevTrainingMetrics {
        val startMs = System.currentTimeMillis()
        val nSamples = trainingSamples.size
        if (nSamples == 0) return latestMetrics

        // 1. Construir vocabulario único y frecuencia de documento (DF)
        val tokenizedSamples = trainingSamples.map { tokenizeAndExtractFeatures(it.phrase) }
        val docFreq = LinkedHashMap<String, Int>()

        for (tokens in tokenizedSamples) {
            for (uniqueToken in tokens.toSet()) {
                docFreq[uniqueToken] = (docFreq[uniqueToken] ?: 0) + 1
            }
        }

        val newVocab = LinkedHashMap<String, Int>()
        var idx = 0
        for (token in docFreq.keys) {
            newVocab[token] = idx++
        }
        val vocabSize = newVocab.size
        val newIdf = FloatArray(vocabSize)
        for ((token, tokenIdx) in newVocab) {
            val df = docFreq[token] ?: 1
            // Fórmula IDF suavizada: ln((1 + N) / (1 + df)) + 1.0
            newIdf[tokenIdx] = (ln((1.0 + nSamples) / (1.0 + df)) + 1.0).toFloat()
        }

        // Si el tamaño del vocabulario cambió, redimensionar matriz W conservando pesos aprendidos previos
        val newW = Array(numClasses) { FloatArray(vocabSize) }
        for (c in 0 until numClasses) {
            for ((token, newIdx) in newVocab) {
                val oldIdx = vocabulary[token]
                if (oldIdx != null && oldIdx < weightsW[c].size) {
                    newW[c][newIdx] = weightsW[c][oldIdx]
                }
            }
        }

        vocabulary = newVocab
        idfWeights = newIdf
        weightsW = newW

        // Pre-vectorizar todos los ejemplos del dataset con TF-IDF normalizado L2
        val featureVectors = tokenizedSamples.map { tokens ->
            tokensToTfIdfVector(tokens)
        }

        // 2. Bucle de Entrenamiento Neuronal (Backpropagation)
        val l2Lambda = 0.0005f
        var epochLoss = 0f

        for (epoch in 0 until epochs) {
            var totalLoss = 0f
            for (sIdx in trainingSamples.indices) {
                val x = featureVectors[sIdx]
                val targetClass = trainingSamples[sIdx].label.index

                // Forward Pass: z_k = W_k · x + b_k
                val probs = forwardSoftmax(x)

                // Cross-Entropy Loss: -ln(P(target))
                val targetProb = probs[targetClass].coerceIn(1e-7f, 1.0f)
                totalLoss += -ln(targetProb.toDouble()).toFloat()

                // Backward Pass (Gradiente del error): delta_k = P(k) - 1(k == target)
                for (c in 0 until numClasses) {
                    val targetIndicator = if (c == targetClass) 1.0f else 0.0f
                    val delta = probs[c] - targetIndicator

                    // Actualizar sesgo b_c
                    biasesB[c] -= learningRate * delta * 0.25f

                    // Actualizar pesos sinápticos W[c][i] solo en características activas
                    val wRow = weightsW[c]
                    for (i in x.indices) {
                        val xi = x[i]
                        if (xi != 0f) {
                            val grad = (delta * xi) + (l2Lambda * wRow[i])
                            wRow[i] -= learningRate * grad
                        }
                    }
                }
            }
            epochLoss = totalLoss / nSamples
            totalEpochs++
        }

        // 3. Evaluar precisión (Accuracy) sobre el dataset
        var correctCount = 0
        for (sIdx in trainingSamples.indices) {
            val probs = forwardSoftmax(featureVectors[sIdx])
            val predictedIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
            if (predictedIdx == trainingSamples[sIdx].label.index) {
                correctCount++
            }
        }

        val accuracy = correctCount.toFloat() / nSamples.toFloat()
        val elapsedMs = System.currentTimeMillis() - startMs

        latestMetrics = JevTrainingMetrics(
            vocabularySize = vocabSize,
            totalSamples = nSamples,
            totalEpochsTrained = totalEpochs,
            currentLoss = epochLoss,
            trainingAccuracy = accuracy,
            lastTrainingTimeMs = elapsedMs
        )

        AppLogger.success(
            "JevNeural",
            "Red Neuronal Jev entrenada: Vocabulario=${vocabSize} tokens | Muestras=$nSamples | Épocas=$totalEpochs | Precisión=${"%.1f".format(accuracy * 100)}% | Loss=${"%.4f".format(epochLoss)} (${elapsedMs}ms)"
        )

        return latestMetrics
    }

    /**
     * Permite agregar una nueva frase de entrenamiento desde la interfaz de Desarrollador
     * y re-entrenar la red neuronal en vivo para que aprenda el nuevo patrón inmediatamente.
     */
    @Synchronized
    fun aprenderNuevaFrase(phrase: String, targetModule: JevIntentModule): JevTrainingMetrics {
        val clean = phrase.trim()
        if (clean.isBlank()) return latestMetrics

        trainingSamples.add(JevTrainingSample(clean, targetModule))
        AppLogger.info(
            "JevNeural",
            "Nueva muestra agregada al dataset: \"$clean\" -> Módulo [${targetModule.name}]. Ejecutando Backpropagation..."
        )
        return reconstruirDiccionarioYEntrenar(epochs = 80, learningRate = 0.85f)
    }

    /**
     * Convierte una lista de tokens en el vector numérico TF-IDF con normalización L2.
     */
    private fun tokensToTfIdfVector(tokens: List<String>): FloatArray {
        val x = FloatArray(vocabulary.size)
        if (tokens.isEmpty() || vocabulary.isEmpty()) return x

        for (t in tokens) {
            val idx = vocabulary[t]
            if (idx != null) {
                x[idx] += 1.0f
            }
        }

        var sumSq = 0f
        for (i in x.indices) {
            if (x[i] > 0f) {
                x[i] *= idfWeights[i]
                sumSq += x[i] * x[i]
            }
        }

        if (sumSq > 0f) {
            val norm = sqrt(sumSq)
            for (i in x.indices) {
                x[i] /= norm
            }
        }
        return x
    }

    /**
     * Forward Pass: Calcula z = W·x + b y aplica la función de activación Softmax.
     */
    private fun forwardSoftmax(x: FloatArray): FloatArray {
        val logits = FloatArray(numClasses)
        var maxLogit = Float.NEGATIVE_INFINITY

        for (c in 0 until numClasses) {
            var sum = biasesB[c]
            val wRow = weightsW[c]
            for (i in x.indices) {
                val xi = x[i]
                if (xi != 0f) {
                    sum += wRow[i] * xi
                }
            }
            logits[c] = sum
            if (sum > maxLogit) maxLogit = sum
        }

        val probs = FloatArray(numClasses)
        var sumExp = 0f
        for (c in 0 until numClasses) {
            val e = exp((logits[c] - maxLogit).toDouble()).toFloat()
            probs[c] = e
            sumExp += e
        }

        val safeSum = max(sumExp, 1e-9f)
        for (c in 0 until numClasses) {
            probs[c] /= safeSum
        }
        return probs
    }

    /**
     * Inferencia en Tiempo Real (< 0.5 ms):
     * Evalúa cualquier texto ingresado por el usuario o desarrollador y devuelve el módulo
     * ganador, la distribución de probabilidades Softmax y los tokens activados del diccionario.
     */
    @Synchronized
    fun predict(inputPhrase: String): JevNeuralPrediction {
        val startNano = System.nanoTime()
        val tokens = tokenizeAndExtractFeatures(inputPhrase)
        val x = tokensToTfIdfVector(tokens)
        val probsArray = forwardSoftmax(x)

        var bestClassIdx = 0
        var bestProb = probsArray[0]
        for (c in 1 until numClasses) {
            if (probsArray[c] > bestProb) {
                bestProb = probsArray[c]
                bestClassIdx = c
            }
        }

        // Si ningún token del diccionario se activó, redirigir por seguridad a CONVERSACION_LIBRE (Gemini)
        val hasActiveFeatures = x.any { it > 0f }
        val winningModule = if (!hasActiveFeatures) {
            JevIntentModule.CONVERSACION_LIBRE
        } else {
            JevIntentModule.fromIndex(bestClassIdx)
        }

        val finalConfidence = if (!hasActiveFeatures) 1.0f else bestProb
        val probsMap = JevIntentModule.entries.associateWith { mod ->
            probsArray[mod.index]
        }

        val activatedTokens = mutableListOf<ActivatedDictionaryToken>()
        for ((token, idx) in vocabulary) {
            if (idx < x.size && x[idx] > 0f) {
                activatedTokens.add(
                    ActivatedDictionaryToken(
                        token = token,
                        tfIdfValue = x[idx],
                        synapticWeightForWinner = weightsW[winningModule.index][idx]
                    )
                )
            }
        }
        activatedTokens.sortByDescending { it.synapticWeightForWinner * it.tfIdfValue }

        val shouldBypassGemini = winningModule.isLocalZeroToken &&
                hasActiveFeatures &&
                finalConfidence >= CONFIDENCE_THRESHOLD

        val elapsedMicros = (System.nanoTime() - startNano) / 1000L

        AppLogger.info(
            "JevNeural",
            "Inferencia Jev: \"$inputPhrase\" -> [${winningModule.name}] (${"%.1f".format(finalConfidence * 100)}%) | Local 0-Tokens: $shouldBypassGemini (${elapsedMicros}µs)"
        )

        return JevNeuralPrediction(
            inputPhrase = inputPhrase,
            normalizedTokens = tokens,
            activatedDictionaryTokens = activatedTokens,
            winningModule = winningModule,
            confidence = finalConfidence,
            probabilities = probsMap,
            shouldExecuteLocallyZeroTokens = shouldBypassGemini,
            inferenceTimeMicros = elapsedMicros
        )
    }

    @Synchronized
    fun getMetrics(): JevTrainingMetrics = latestMetrics

    @Synchronized
    fun getDatasetSamples(): List<JevTrainingSample> = trainingSamples.toList()

    @Synchronized
    fun getTopDictionaryWordsPerModule(limit: Int = 8): Map<JevIntentModule, List<Pair<String, Float>>> {
        val result = mutableMapOf<JevIntentModule, List<Pair<String, Float>>>()
        val inverseVocab = vocabulary.entries.associate { (k, v) -> v to k }
        for (mod in JevIntentModule.entries) {
            val wRow = weightsW[mod.index]
            val pairs = wRow.indices.mapNotNull { idx ->
                val word = inverseVocab[idx] ?: return@mapNotNull null
                Pair(word, wRow[idx])
            }.sortedByDescending { it.second }.take(limit)
            result[mod] = pairs
        }
        return result
    }
}
