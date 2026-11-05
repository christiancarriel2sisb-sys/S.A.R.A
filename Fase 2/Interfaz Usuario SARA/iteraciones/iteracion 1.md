# Flujo de Trabajo: Implementación de Interrupción en Tiempo Real (*Barge-In*) Directo en Android

**Autor:** Christian  
**Arquitectura:** 100% On-Device (Android/Kotlin) sin Servidor Proxy  
**Objetivo:** Permitir que el robot Sara hable mediante streaming continuo de Gemini y pueda ser interrumpido de forma instantánea al detectar la voz del usuario (*barge-in*), liberando recursos de red, audio y hardware.

---

## 1. Arquitectura del Flujo de Interrupción

El ciclo funciona bajo un modelo reactivo no bloqueante gobernado por **Kotlin Coroutines (`Job`)** y control de estados de audio:

```
[Usuario habla] ──────► SpeechRecognizer / AudioRecord (Escucha activa)
                               │
                      ¿Voz detectada mientras Sara habla?
                               ├── SÍ ──► 1. Cancelar `geminiStreamingJob?.cancel()`
                               │          2. Silenciar `textToSpeech.stop()`
                               │          3. Vaciar buffers en cola
                               │          4. Tomar nueva transcripción
                               └── NO ──► Flujo normal
                                              │
Gemini Stream API ──(Chunks / SSE)──► Acumulador de Frases ──► TextToSpeech.QUEUE_ADD
```

---

## 2. Fases de Implementación Paso a Paso

### Fase 1: Control de Concurrencia con Corrutinas (`Job` Cancelable)
Para poder detener en seco el consumo de la API de Gemini en cuanto el usuario empiece a hablar, la llamada HTTP/SSE debe estar anclada a un `Job` cancelable en el `ViewModel`.

1. **Definir la referencia cancelable:**
   * Crear una variable `private var streamJob: Job? = null` en el `SaraViewModel`.
2. **Consumo por Streaming:**
   * En lugar de llamar al endpoint bloqueante tradicional (`:generateContent`), usar el endpoint de streaming (`:streamGenerateContent?alt=sse`).
   * Recibir el flujo de eventos línea por línea con `BufferedReader` sobre `Dispatchers.IO`.
   * Envolver la lectura en un bloque cooperativo de cancelación (`ensureActive()` o `yield()`), de modo que cuando el `Job` sea cancelado, el socket HTTP se cierre de inmediato sin descargar tokens innecesarios.

### Fase 2: Acumulador y Fragmentador de Oraciones (*Sentence Chunking*)
El modelo envía tokens o fragmentos de palabras. El motor `TextToSpeech` (TTS) suena robótico o entrecortado si verbaliza palabra por palabra; la mejor práctica es verbalizar oración por oración:

1. **Buffer de caracteres en streaming:**
   * A medida que llegan los fragmentos de texto del stream de Gemini, agregarlos a un `StringBuilder`.
2. **Corte por signos de puntuación:**
   * Verificar continuamente si el buffer contiene terminadores de oración: `.` (punto), `?` (cierre de interrogación), `!` (cierre de exclamación) o `\n`.
3. **Despacho a cola de audio:**
   * Extraer la oración completa, limpiarla con `TextUtils.stripMarkdown()` y enviarla al motor TTS con la bandera:
     `TextToSpeech.QUEUE_ADD` (para encadenar oraciones fluidamente sin pausas artificiales).

### Fase 3: Detección de Voz Concurrente mientras Sara Habla (*Barge-In Detector*)
El desafío técnico de interrumpir al robot reside en capturar la voz del usuario mientras los parlantes del teléfono están reproduciendo sonido:

1. **Configuración de Audio:**
   * Utilizar `AcousticEchoCanceler` de Android si el hardware del teléfono lo soporta (`AcousticEchoCanceler.isAvailable()`), enlazándolo al identificador de sesión de audio (`audioSessionId`). Esto atenúa el sonido emitido por el propio parlante en el micrófono.
2. **Estrategia de Detección:**
   * **Opción A (Reconocedor continuo):** Mantener el `SpeechRecognizer` escuchando con `RecognitionListener`. En cuanto se active el callback `onBeginningOfSpeech()` o el nivel RMS suba sostenidamente en `onRmsChanged(rmsdB)`, activar la señal de interrupción.
   * **Opción B (Detector de volumen por umbral en buffer crudo):** Abrir un stream ligero de `AudioRecord` en paralelo mientras el TTS está activo. Si el nivel de decibelios supera un umbral durante más de 200 ms (indicando voz humana cercana), disparar el evento de interrupción.

### Fase 4: La Rutina de Cancelación Instantánea (*The Kill Switch*)
En cuanto se confirma que el usuario empezó a hablar para interrumpir a Sara, ejecutar la siguiente rutina atómica:

```kotlin
fun interruptSara() {
    // 1. Cancelar la conexión de red con Gemini inmediatamente
    streamJob?.cancel()
    streamJob = null

    // 2. Detener en seco la salida física de audio
    textToSpeechManager.stop()

    // 3. Limpiar buffers de oraciones pendientes
    sentenceBuffer.clear()

    // 4. Cambiar estado visual del robot (UI) a "Escuchando interrupción"
    _uiState.update { it.copy(isSpeaking = false, isListening = true) }
}
```

### Fase 5: Manejo del Contexto Posterior a la Interrupción
Cuando una conversación se interrumpe, el historial de contexto del LLM debe reflejar la realidad de lo que ocurrió para que la IA no actúe confundida:

1. **Guardar solo lo que el usuario realmente escuchó:**
   * Mantener un contador o lista de las oraciones que el `TextToSpeech` realmente alcanzó a verbalizar antes del corte (usando el `UtteranceProgressListener.onRangeStart` o `onDone` por oración).
2. **Actualizar el historial:**
   * Descartar el resto de la respuesta que Gemini estaba generando pero que nunca llegó a los parlantes.
   * Guardar en la lista del historial:
     * `user`: Pregunta original.
     * `model`: Solo las oraciones que alcanzaron a sonar + `[interrumpido por el usuario]`.
     * `user`: La nueva instrucción que causó la interrupción.

---

## 3. Plan de Verificación y Pruebas Unitarias/Locales

Para validar la fluidez de la conversación sin hardware del robot ni servidores externos:

| Escenario de Prueba | Acción Realizada | Resultado Esperado |
| :--- | :--- | :--- |
| **Prueba 1: Streaming continuo** | Hacer una pregunta larga (ej: *"Sara, cuéntame sobre el sistema solar"*). | El texto empieza a sonar en menos de 1 segundo mediante oraciones fluidas sin esperar el fin de la respuesta completa. |
| **Prueba 2: Interrupción verbal directa** | Hablar con fuerza (*"Sara, basta"* o *"Espera"*) a mitad de la respuesta. | El audio de los parlantes se apaga al instante (<100 ms) y el teléfono muestra el estado de escucha. |
| **Prueba 3: Consumo de datos** | Interrumpir al robot en la primera oración. | Verificar en el profiler de red de Android Studio que la descarga de bytes de la API se detuvo en seco tras el `cancel()`. |
| **Prueba 4: Consistencia de contexto** | Preguntar de inmediato *"¿De qué estábamos hablando?"*. | Sara responde haciendo referencia al fragmento previo que alcanzó a decir, sin desincronización de memoria. |