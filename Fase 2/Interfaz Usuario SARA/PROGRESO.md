# Estado y Progreso del Proyecto SARA

> **Bitácora de Desarrollo e Hitos Técnicos:** Actualizado al desarrollo de la Fase 2 y consolidación de la arquitectura Android Nativa.

---

## 📌 Resumen de Estado Global

| Fase / Hito | Estado | Arquitectura |
| :--- | :--- | :--- |
| **Fase 1: Módulo de Dictado Inicial** | COMPLETADO | Angular 18 + Whisper Local (Offline) |
| **Fase 2: Conexión Gemini + Rostro Web** | COMPLETADO | Angular 18 + Node Express + Rostro SVG |
| **Sprint 1: Portabilidad Android Nativa** | COMPLETADO | Kotlin + Jetpack Compose + EncryptedSharedPreferences |
| **Iteración 1: Streaming SSE + Barge-In + Voz 100%** | COMPLETADO | Gemini 2.5 Flash + Chunking + Interrupción en tiempo real |
| **Iteración 2: Visión y Seguimiento Facial** | COMPLETADO | CameraX + Google ML Kit Face Detection + Seguimiento ocular |
| **Iteración 3: Navegación Reactiva Jev + USB-OTG** | COMPLETADO | Pinhole 3D + SafetySupervisor (<0.40m) + Despachador Serial 10 Hz |
| **Iteración 4: Detección Facial HUD (Voz y Visión)** | COMPLETADO | Bounding Box HUD neón + FILL_CENTER + Cámara invisible en voz |
| **Iteración 5: Estabilización de Obstáculos + Arduino V2** | COMPLETADO | AtomicBoolean anti-starvation + Dispatchers.Main + Protocolo 1-6 Fran |
| **Iteración 6: Optimización de Hardware + Manual APK Celular** | COMPLETADO | Desbloqueo AVD config.ini (6GB RAM) + Build APK + Manual Despliegue Físico |

---

## 🔍 Detalle de Módulos e Iteraciones

### Fase 1: Módulo de Dictado
- **Estado:** COMPLETADO CON WHISPER LOCAL (100% OFFLINE)
- Prototipo inicial en Angular con servidor Express para captura y transcripción de voz local.

### Fase 2: Conexión con Gemini y Rostro Animado Web
- **Estado:** COMPLETADO Y VERIFICADO
- Implementación del rostro animado procedural en SVG con 60 FPS, parpadeo autónomo y sincronía labial.
- Conexión con Gemini mediante backend intermedio.

### Fase Android Nativa: Sprint 1 (Portabilidad a Kotlin)
- **Estado:** COMPLETADO Y ESTRUCTURADO
- **Componentes implementados:**
  - `SaraPrompt.kt`: Prompt de sistema, saludos y reglas éticas de no prescripción clínica.
  - `TextUtils.kt`: Limpieza de formato Markdown (`stripMarkdown`) y segmentación de oraciones (`splitSentences`).
  - `VoiceInputManager.kt`: Reconocedor continuo con `android.speech.SpeechRecognizer` y recuperación automática ante errores de red/idioma.
  - `TextToSpeechManager.kt`: Síntesis de voz nativa en español (`es-CL` / `es-ES`).
  - `GeminiRepository.kt`: Cliente HTTP directo y asíncrono con OkHttp, eliminando la necesidad de servidores proxy.
  - `SecurePreferences.kt`: Cifrado de credenciales en Android Keystore con AES-256 (`EncryptedSharedPreferences`).
  - `SaraViewModel.kt`: Orquestador reactivo de flujo (Voz -> Gemini -> TTS) y máquina de estados (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`).

### Iteración 1: Conversación Fluida con Streaming SSE y Barge-In
- **Estado:** COMPLETADO Y VERIFICADO
- **Componentes implementados:**
  - **Streaming SSE:** Consumo en vivo de eventos `:streamGenerateContent?alt=sse` mediante `BufferedReader` sobre `Dispatchers.IO`.
  - **Sentence Chunking en Tiempo Real:** Acumulador de fragmentos de texto que corta inmediatamente por signos de puntuación (`.`, `?`, `!`, `\n`) y despacha las oraciones al TTS con `QUEUE_ADD`.
  - **Mecanismo de Interrupción (*Barge-In*):** Cancelación cooperativa de corrutinas (`streamJob?.cancel()`), detención instantánea del audio (`TTS.stop()`) y retorno a escucha activa si el usuario comienza a hablar mientras el robot responde.
  - **Interacción 100% por Voz:** Se eliminó la barra de texto o teclados en pantalla, optimizando la experiencia para adultos mayores.
  - **LogPanel Flotante (`LogPanelDialog.kt`):** Consola de registro en memoria con filtro por nivel (`DEBUG`, `INFO`, `WARN`, `ERROR`), exportación de logs y telemetría de latencias.

### Iteración 2: Visión Artificial y Seguimiento Facial
- **Estado:** COMPLETADO Y VERIFICADO
- **Componentes implementados:**
  - **Pipeline CameraX (`CameraManager.kt`):** Captura en resolución optimizada para bajo consumo (320x320) con descarte de fotogramas atrasados (`ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`).
  - **Detección con Google ML Kit (`FaceAnalyzer.kt`):** Procesamiento de imágenes en memoria con `InputImage.fromMediaImage` para detección de rostros humanos y estimación de caja delimitadora (*bounding box*).
  - **Cálculo Angular de Desviación:** Cálculo del ángulo horizontal ($\theta$) y vertical ($\phi$) relativo al centro del sensor óptico.
  - **Sincronización Ocular:** Los ojos del rostro animado (`FaceComponent.kt` / `FaceAnimator.kt`) desplazan sus pupilas en tiempo real siguiendo las coordenadas del usuario detectado.
  - **Pantalla de Telemetría Visual (`FaceDetectionScreen.kt`):** Overlay visual con caja delimitadora, punto central y métricas angulares en pantalla.

### Iteración 3: Navegación Reactiva Jev, Proyección 3D y Enlace USB-OTG
- **Estado:** COMPLETADO Y VERIFICADO
- **Componentes implementados:**
  - **Proyección Espacial Monocular (`SpatialProjector.kt`):** Estimación métrica de distancia de profundidad ($Z$) y desplazamiento horizontal ($X$) usando el modelo de cámara Pinhole ($Z = f_y \times H_{real} / h_{bbox}$).
  - **Motor de Decisión Jev (`JevDecisionEngine.kt`):** Algoritmo de campos vectoriales de potencial: genera vectores de atracción hacia personas u objetivos y vectores de repulsión inversamente proporcionales ante obstáculos.
  - **Supervisor de Seguridad (`SafetySupervisor.kt`):** Verificación de proximidad crítica. Si cualquier objeto entra en el radio de peligro ($Z < 0.40\text{ m}$), fuerza una parada de emergencia inmediata (`EMERGENCY_STOP`) con retroceso de seguridad.
  - **Despachador Serial USB-OTG (`SerialDispatcher.kt`):** Módulo de comunicación por cable USB-OTG con la placa Arduino Uno a 115200 baudios. Envía periódicamente a 10 Hz tramas cinemáticas estandarizadas (`NAV:<angle>,<pwm>\n`).
  - **HUD de Navegación (`NavigationScreen.kt`):** Interfaz táctica con radar polar polarizado, indicador de vector angular/PWM, estado del supervisor de seguridad y consola de telemetría de navegación.

### Iteración 4: Detección Facial HUD y Bounding Box en Tiempo Real (Módulo de Voz y Visión)
- **Estado:** COMPLETADO Y VERIFICADO
- **Petición del Usuario:** Renderizar un cuadro delimitador en tiempo real al reconocer un rostro en el módulo de voz sin mostrar el video de la cámara (manteniendo el rostro del robot limpio) y asegurar encuadre preciso sobre el rostro en la pantalla de visión.
- **Componentes implementados:**
  - **Componente HUD Robótico (`FaceBoundingBoxOverlay.kt`):** Retícula con brackets neón cian (`#00F0FF`), cruz central, líneas punteadas, relleno translúcido y badge `ROSTRO FIJADO [x.x°]`.
  - **Cálculo de Proyección Óptica `FILL_CENTER`:** Mapeo de coordenadas del sensor de la cámara a la resolución de pantalla del dispositivo, corrigiendo el aspect ratio y el efecto espejo de la cámara frontal.
  - **Integración Dual:**
    - `FaceDetectionScreen.kt`: Bounding box superpuesto con precisión milimétrica sobre el video en vivo.
    - `SaraScreen.kt`: Captura CameraX invisible en segundo plano con overlay sobre la cara interactiva de Sara.
  - **Documento de especificación:** `iteraciones/iteracion 4.md`.

### Iteración 5: Estabilización Continua de Obstáculos y Fusión con Arduino V2 (Fran)
- **Estado:** COMPLETADO Y VERIFICADO
- **Petición del Usuario:** Solucionar el congelamiento de cuadros delimitadores y colapso de Jev con el tiempo; conectar el motor Jev al nuevo controlador Arduino V2 (`prueba_sara_v2.ino`) de Francisca para control autónomo real sin alterar el sistema operativo ni instalar librerías externas.
- **Componentes implementados:**
  - **Fusión de Firmware:** Integración local de `Fase 2/Movimiento Sara/prueba_sara_v2.ino` desde `origin/main`.
  - **Control de Saturación Atómico (`CameraManager.kt`):** Cerrojo `isProcessing` (`AtomicBoolean`) que descarta inmediatamente fotogramas entrantes si la inferencia anterior sigue ejecutándose, eliminando el agotamiento de buffers en CameraX.
  - **Concurrencia en Hilo Principal:** Despacho de resultados hacia `Dispatchers.Main` antes de mutar `navState`, evitando colisiones de snapshot en Compose.
  - **Adaptación Serial Arduino V2 (`MotorCommandFormatter.kt`):** Emisión de tramas numéricas simples (`1` Avanzar, `2` Giro Izq, `3` Giro Der, `6` Stop) compatibles con `Serial.parseInt()` de Arduino a 115200 baudios.
  - **HUD Táctico Enriquecido (`NavigationScreen.kt`):** Bounding boxes con etiquetas textuales y distancia métrica (`Persona [1.4m]`) y telemetría de transmisión `TX: 1 (Avanzar)`.
  - **Documento de especificación:** `iteraciones/iteracion 5.md`.

### Iteración 6: Optimización de Hardware en Emulador, Generación de APK y Despliegue Físico
- **Estado:** COMPLETADO Y VERIFICADO
- **Petición del Usuario:** Explicar arquitectura de hardware de Jev y ML Kit (CPU/GPU/Cloud y límites de cómputo); solucionar bloqueo de recursos en emulador de Android Studio y escalar la RAM a 6 GB; generar el APK para teléfono físico y documentar un manual paso a paso de instalación y ejecución en un celular.
- **Componentes implementados:**
  - **Auditoría de Hardware y Cómputo:** Mapeo de ejecución: Jev (CPU local, <1 ms), ML Kit (CPU local con ARM NEON, 320x320 @ 10 FPS), Gemini (Google Cloud TPUs, dependiente de red) y Arduino (microcontrolador ATmega328P para motores).
  - **Desbloqueo de Recursos de AVD (`config.ini`):** Modificación física de `Medium_Phone_API_35.avd/config.ini` sobrepasando el bloqueo de Google Play Store para asignar `hw.ramSize=6144` (6 GB RAM), `vm.heapSize=512` y 6 núcleos de CPU.
  - **Compilación de Artefacto Android:** Generación limpia de `app-debug.apk` con Gradle y JDK 17 (`BUILD SUCCESSFUL in 15s`).
  - **Manual de Instalación en Celular Físico:** Guía detallada en `iteraciones/iteracion 6.md` con métodos de transferencia directa (Drive/WhatsApp/USB) y consola ADB, permisos de tiempo de ejecución y configuración de orígenes desconocidos.
  - **Documento de especificación:** `iteraciones/iteracion 6.md`.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
