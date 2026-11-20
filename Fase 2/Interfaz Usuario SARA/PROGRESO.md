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
| **Iteración 7: Base de Datos Local Room + SQLite** | COMPLETADO | KSP 1.9.23 + Room 2.6.1 + 5 Entidades + DAOs Corrutinas + sara_local_db |
| **Iteración 8: Módulo Recordatorios (AlarmManager + RAM)** | COMPLETADO | Buffer Temporal RAM + AlarmManager Nativo + WakeLock + Despertar a Sara |
| **Iteración 9: Módulo Multimedia (YouTube + ExoPlayer)** | COMPLETADO | Media3 ExoPlayer (Búfer 500ms) + YouTube Data API v3 + Gemini Tools + Ducking |
| **Iteración 10: Reestructuración 3 Roles + Chat Privado Familiar** | COMPLETADO | 3 Vistas (Paciente/Familiar/Admin) + Room SQLite Chat (MER) + Parser Local 0 Tokens + TTS |
| **Iteración 11: Reconocimiento Facial Multirrostro MobileFaceNet** | COMPLETADO | MobileFaceNet 192-d + Consentimiento + Diferenciación 1:1 + 640x360 @ 15 FPS |
| **Iteración 12: 4 Roles (Desarrollador) + Panel Admin + Red Neuronal Jev** | COMPLETADO | Perfil Desarrollador + CRUD Lógico (estado) + Monitor LED/MD + Red Neuronal Jev TF-IDF/Softmax |

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

### Iteración 7: Creación de la Infraestructura de Base de Datos Local (Room / SQLite)
- **Estado:** COMPLETADO Y VERIFICADO
- **Petición del Usuario:** Leer la especificación de `b_d.md`, implementar la infraestructura de base de datos local completa en el proyecto Android y presentar la estructura de tablas y tipos de datos de cada atributo para verificación.
- **Componentes implementados:**
  - **KSP y Dependencias:** Integración de Google KSP `1.9.23-1.0.20` y Room `2.6.1` (`room-runtime`, `room-ktx`, `room-compiler`).
  - **5 Entidades Room:** Modelado relacional de `Paciente`, `recordatorios`, `Contacto_emergencia`, `juegos` e `historial_estimulacion` con índices y borrado en cascada.
  - **DAOs con Corrutinas y Flow:** Métodos reactivos y asíncronos para operaciones CRUD.
  - **SaraAppDatabase Singleton:** Instancia central de base de datos (`sara_local_db`).
  - **Esquema Físico SQLite Verificado:** Generación del archivo SQLite y extracción de tipos de datos (`PRAGMA table_info`), llaves foráneas e índices.
  - **Documento de especificación:** `iteraciones/iteracion 7.md`.

### Iteración 8: Módulo de Recordatorios Clínicos con AlarmManager y Buffer Temporal en RAM
- **Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)
- **Petición del Usuario:** Crear nuevo módulo de recordatorios similar a los demás módulos; configurar la alarma especificando fecha (día y mes) y hora con `LocalDateTime`; calcular los milisegundos requeridos por AlarmManager; trazar logs en cada proceso; usar AlarmManager nativo de Android para revisar cuándo ejecutar la alarma, levantar a Sara y que Sara hable al usuario; almacenar en buffer temporal en memoria RAM (sin persistir en base de datos física en esta fase).
- **Componentes implementados:**
  - **Buffer en RAM (`ReminderMemoryBuffer.kt`):** Colección concurrente volátil con estados (`PROGRAMADA`, `DISPARADA`, `CANCELADA`), conversión a `LocalDateTime` y emisión reactiva con `StateFlow`.
  - **Configuración con `LocalDateTime` (`ReminderScreen.kt`):** Selectores interactivos de Día y Mes (`DatePickerDialog`) y Hora (`TimePickerDialog`), accesos directos de prueba (+30s, +1m, +5m, +1 día) y cálculo en tiempo real de milisegundos (`targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()`).
  - **AlarmManager Nativo (`AlarmScheduler.kt`):** Agendamiento exacto con `RTC_WAKEUP` y soporte Doze (`setExactAndAllowWhileIdle`).
  - **Receptor y WakeLock (`ReminderBroadcastReceiver.kt`):** Despertar de pantalla, notificación de alta prioridad y lanzamiento de Sara.
  - **Síntesis Vocal en Sara (`SaraViewModel.kt`):** Método `hablarRecordatorio(mensaje)` con voz TTS y animación facial `SPEAKING`.
  - **Monitor en RAM y Logs:** Visualización en tiempo real de alarmas activas con cuenta regresiva, botón de prueba auditiva inmediata y consola flotante de logs.
  - **Documento de especificación:** `iteraciones/iteracion 8.md`.

### Iteración 9: Módulo de Reproducción de Música por Voz (YouTube API + Reproductor Oficial Embebido)
- **Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)
- **Petición del Usuario:** Leer y ejecutar la guía técnica `modulo de musica.md`; configurar el reproductor multimedia por comandos de voz; usar `GOOGLE_CLOUD_API_KEY` desde `.env`; corregir la sustitución indebida de canciones por pistas de prueba de jazz y piano; eliminar llamadas a instancias caídas como `pipedapi.nosebs.ru` y asegurar que se reproduzca exclusivamente la canción buscada por el usuario.
- **Componentes implementados:**
  - **Configuración Segura desde `.env` (`build.gradle.kts` & `SecurePreferences.kt`):** Carga de `GOOGLE_CLOUD_API_KEY` desde `.env` hacia `BuildConfig`.
  - **Cliente YouTube Data API v3 (`YouTubeApiService.kt`):** Consulta por Retrofit con extracción de `videoId`, título real, artista y miniatura en tiempo récord.
  - **Supresión de Fallbacks y Servidores Externos (`AudioStreamResolver.kt`):** Eliminación total de servidores de scraping caídos (`pipedapi.nosebs.ru`) y supresión de sustitución por jazz o piano.
  - **Reproductor Global Persistente (`MainActivity.kt` & `GlobalYouTubePlayer`):** Ejecución de audio en segundo plano a nivel de aplicación que reproduce la canción de YouTube inmediatamente en cualquier módulo (incluyendo el módulo de conversación `SaraScreen`) sin forzar cambios de pantalla.
  - **Transición a Silencio e IDLE (`SaraViewModel.kt`):** Apenas la canción está lista, Sara apaga el micrófono (`stopListening()`), cancela procesos de pensamiento (`isThinking = false`) y pasa su rostro a reposo (`IDLE`), quedándose completamente callada mientras la música suena a menos que el usuario toque la pantalla para hablar.
  - **Mini-Reproductor Flotante en Conversación (`SaraScreen.kt`):** Barra superior con título de la canción, botón de pausa/reanudación y detención directa.
  - **Motor ExoPlayer Media3 (`ReproductorManager.kt`):** Manejo de estado y atenuación de volumen automática (*Audio Ducking*) al hablar Sara.
  - **Gemini Function Calling (`GeminiRepository.kt` & `SaraPrompt.kt`):** Declaración de herramienta `controlar_multimedia` y captura de intenciones de voz en streaming.
  - **Pantalla Táctica Limpia (`MusicScreen.kt`):** Búsqueda táctil y accesos directos por género (*Boleros, Tangos, Instrumental, Éxitos del Ayer, Baladas de Oro*), sin temas de prueba intrusivos.
  - **Documento de especificación:** `iteraciones/iteracion 9.md`.

### Iteración 10: Reestructuración por Roles (3 Vistas) y Módulo de Chat Privado Familiar (MER + Room SQLite + 0 Tokens)
- **Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)
- **Petición del Usuario:** Reestructurar el proyecto en capas con 3 vistas bien diferenciadas (Paciente: 100% voz con Sara, Familiar: chat privado con el paciente y recordatorios, Administrador: acceso completo a todos los módulos); implementar el chat privado según el MER (`mer.png`) en Room SQLite para máxima seguridad y privacidad; diseñar la lógica de voz para consumo de 0 tokens en la interacción diaria.
- **Componentes implementados:**
  - **Reestructuración de Roles (`MainActivity.kt`, `RoleSelectionScreen.kt` & `UserRole`):** Implementación de la pantalla inicial `RoleSelectionScreen` con 3 botones interactivos (Usuario, Admin, Familiar). Enrutamiento estricto: el paciente solo ve el rostro de Sara (100% voz sin botones); el familiar accede directamente al chat privado de texto (sin IA ni conexión a Sara); el administrador accede a todos los módulos técnicos (visión, navegación, recordatorios, música, logs) sin acceso al chat ("el admin no chatea"). Retorno intuitivo mediante BackHandler y botones de salida.
  - **Entidad y DAO de Chat según MER (`ChatEntity.kt` & `ChatDao.kt`):** Creación de la tabla `chat` con llaves foráneas a `Paciente` y `Contacto_emergencia` con borrado en cascada, `id_chat` autoincremental, `emisor`, `mensaje`, `fecha_hora` y `leido: Boolean`. Consultas reactivas con `Flow`.
  - **Actualización de Base de Datos (`SaraAppDatabase.kt`):** Incremento a `version = 2`, registro de `ChatEntity` y exposición de `chatDao()`.
  - **Repositorio de Mensajería y Sembrado Automático (`ChatRepository.kt`):** Gestión de envío/lectura y precarga automática de paciente demo ("Juan Pérez") y familiares ("Claudia", "Pedro") en caso de base de datos vacía.
  - **Lógica de Voz con 0 Tokens (`SaraViewModel.kt`):** Detección inmediata por expresiones regulares locales (`isChatCommand`, `parseChatIntent`) que evita llamadas a la API de Gemini para comandos estándar ("Sara, dile a Claudia que..."). Android TTS nativo para confirmaciones auditivas y lectura de mensajes sin costo de tokens.
  - **Observador Reactivo de Mensajes Entrantes (`SaraViewModel.kt`):** Detección automática en segundo plano de mensajes no leídos del familiar para despertar a Sara y leérselos al paciente en tiempo real.
  - **Herramienta Gemini Function Calling (`SaraPrompt.kt` & `GeminiRepository.kt`):** Declaración de `enviar_mensaje_familiar` como fallback inteligente ante comandos de voz complejos o ambiguos.
  - **Pantalla Táctica de Chat Familiar (`FamilyChatScreen.kt`):** Selector de contactos, burbujas diferenciadas (paciente en verde con icono de micrófono, familiar en morado con check de lectura), caja de entrada responsiva de texto plano y botón para cambiar de perfil (100% desacoplado de IA y de Sara).
  - **Activación por Voz y Toque Robusto (`VoiceInputManager.kt` & `SaraScreen.kt`):** Restauración del botón interactivo táctil de micrófono con animación de pulso y aviso central *"Toca la pantalla para hablar con Sara"*, garantizando estabilidad absoluta contra limitaciones del reconocedor nativo en emuladores (evitando errores 11 y 12) manteniendo soporte completo de Barge-In e interrupción táctil.
  - **Recordatorios en BD SQLite + Subproceso de Carga Diaria en RAM (`RecordatorioRepository.kt`):** Inserción directa en la tabla `recordatorios` para todos los registros (vía Sara o pantalla táctica); subproceso en segundo plano que carga las alertas de cada día en el buffer de RAM (`ReminderMemoryBuffer`) al arrancar y a medianoche; creación de recordatorios por voz con parser local (0 tokens) y Gemini Function Calling (`crear_recordatorio`); purga completa de recordatorios previos de la BD para inicio de pruebas del usuario.
  - **Trazabilidad de Recordatorios y Logging Estructurado (`AppLogger.kt`, `SaraViewModel.kt` & `RecordatorioRepository.kt`):** Implementación de la estructura de logs solicitada bajo el tag `"Recordatorio"`: paso 1 *"Recordatorio recibido: ... (programado para: ...)"* al capturar la orden por voz o pantalla, paso 2 *"Recordatorio ingresado en la base de datos: ID=... | ... | Fecha: ..."* tras inserción exitosa en SQLite, y paso 3 *"Alarma programada en buffer de RAM: ID=... | ... | Disparo en X seg"*. Ampliación integral del reconocedor de intenciones por voz (tiempos relativos en palabras/números, formatos 12h/24h automáticos, frases naturales "para las", "a las", "avísame", "despiértame") e inyección del reloj del sistema en tiempo real en los payloads de Gemini para evitar fechas desfasadas.
  - **Documento de especificación:** `iteraciones/iteracion 10.md`.

### Iteración 11: Módulo de Visión Unificada (Cámara Frontal Exclusiva) y Reconocimiento Facial Biométrico con MobileFaceNet (TFLite) y Consentimiento Informado
- **Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)
- **Petición del Usuario:** Unir el módulo de detección de objetos con el de reconocimiento facial en la cámara frontal (descartando la cámara trasera para rotar en 360° con el chasis); agregar MobileFaceNet para extraer el vector facial (192 floats); crear una nueva tabla biométrica en la base de datos vinculada 1:1 con la clave primaria de Paciente; construir el módulo integrado en el perfil Administrador que ejecute el flujo de consentimiento informado ("Hola. Para poder reconocerte cuando me llames... ¿Me autorizas a escanear tu rostro?"), almacene el vector en SQLite y provea trazabilidad completa por logs en cada paso.
- **Componentes implementados:**
  - **Cámara Frontal Exclusiva y Visión Unificada (`UnifiedVisionAnalyzer.kt`):** Canal único de inferencia en tiempo real (~10 FPS) que procesa fotogramas de la cámara frontal ejecutando en paralelo detección de objetos ML Kit (con proyección 3D monocular de distancias $Z_{izq}, Z_{cen}, Z_{der}$) y detección facial ML Kit (con ángulo de desviación $\theta$ corregido para vista de espejo).
  - **Inferencia On-Device MobileFaceNet (`MobileFaceNetHelper.kt` & `assets/mobilefacenet.tflite`):** Modelo TFLite de 5.2 MB que genera un vector biométrico de 192 dimensiones a partir del recorte del rostro ($112 \times 112$ px), con normalización $L_2$ automática y función de similitud coseno instantánea (< 0.05 ms en CPU) sin uso de tokens ni conexión a la red.
  - **Entidad y DAO Biométrico en SQLite (`BiometriaEntity.kt`, `BiometriaDao.kt` & `SaraAppDatabase.kt`):** Creación de la tabla `biometria_paciente` en versión 3 de la base de datos, con clave foránea en cascada a `Paciente(Id_paciente)` y clave única garantizando relación estricta 1 a 1, persistiendo el embedding en formato CSV, consentimiento otorgado y fecha de registro.
  - **Repositorio de Biometría (`BiometriaRepository.kt`):** Gestión de operaciones de persistencia, eliminación y comparación biométrica en vivo con umbral de similitud (70%).
  - **Pantalla Táctica de Visión Unificada y Biometría en Admin (`UnifiedVisionScreen.kt` & `MainActivity.kt`):** Interfaz HUD para el Administrador con video en vivo de cámara frontal, cajas de delimitación para obstáculos y rostros, barra superior de telemetría y tarjeta de control de paciente con selector, estado biométrico en SQLite y botones para:
    1. *"PEDIR PERMISO Y ESCANEAR"*: Verifica la presencia del rostro, ejecuta por TTS el prompt exacto de consentimiento informado, abre el micrófono para escuchar la respuesta ("Sí" / "No") con botones táctiles de respaldo, y tras la aprobación genera el embedding y lo persiste en la BD.
    2. *"TESTEAR"*: Inferencia biométrica en vivo comparando la persona frente a la cámara con el registro guardado, calculando el porcentaje de similitud y saludando por TTS.
    3. *"BORRAR"*: Purga el registro biométrico de la base de datos.
  - **Refinamiento No Invasivo y Conmutación de Cámara:** Barra inferior reducida a píldora flotante de 46 dp para dejar >90% de pantalla visible; modo `PreviewView.ImplementationMode.COMPATIBLE` (TextureView) para emuladores; botón de alternancia rápida entre cámara Frontal y Trasera; solicitud simultánea de permisos (`CAMERA` + `RECORD_AUDIO`) en `MainActivity.kt`.
  - **Configuración de 8 GB de Memoria RAM:** Asignación de 8 GB (`hw.ramSize = 8192`) y `vm.heapSize = 1024` en el emulador AVD (`Medium_Phone_API_35`), asignación de 8 GB en JVM (`org.gradle.jvmargs=-Xmx8192m`) y activación de `android:largeHeap="true"` en `AndroidManifest.xml`.
  - **Corrección de Bounding Box (`error 7.png`) y Placa Identificatoria Christian Carriel:** Detección de la causa raíz del desfase en la imagen `error 7.png` (la cámara activa era `TRASERA (Fallback)`, pero el canvas aplicaba espejado horizontal `1 - x` porque la preferencia era `useFrontCamera = true`, proyectando el recuadro sobre la pared derecha); se implementó `isActuallyFrontCamera` para que el canvas y el analizador solo espejen si la cámara física activa es realmente frontal; renderizado de la placa con nombre (`👤 Christian`) sobre el recuadro celeste en tiempo real con fondo de alto contraste; automatización en un clic del botón `[TEST]` y migración completa de cualquier referencia a "Juan" hacia "Christian Carriel".
  - **Optimización de Resolución para Inferencia Máxima (640x360 px):** Implementación de `ResolutionSelector` en `ImageAnalysis` (reducción de 89% en volumen de píxeles respecto a 1080p sin degradar la nitidez de la pantalla `Preview`), bajando la latencia de cómputo de 120 ms a ~18 ms, sincronizando la proyección espacial 3D y elevando la cadencia a ~14 FPS sostenidos sin estrangulamiento térmico ni pausas de GC.
  - **Especialización Exclusiva en Reconocimiento Facial Multirrostro y Flujo "Sin Registro":** Eliminación completa del detector de objetos para dedicar el 100% de la visión al reconocimiento facial a ~15 FPS; eliminación de nombres pre-asumidos al inicio (cualquier persona no registrada se marca como `⚠️ Sin registro` y activa automáticamente el flujo en 2 pasos: Consentimiento Informado + Ingreso de Nombre por texto o voz para crear su registro en `Paciente` y `biometria_paciente`); diferenciación multirrostro simultánea mediante algoritmo de asignación exclusiva 1 a 1 (*Greedy Bipartite Matching*) con cálculo individual de distancia $Z$ (m) y ángulo $\theta$ (°); selector táctico `🎯 Ir a: [Persona]` para que Sara busque y se dirija específicamente hacia la persona seleccionada entre múltiples rostros en escena.
  - **Trazabilidad Exhaustiva en Logs (`AppLogger`):** Registro detallado de cada fase bajo el tag `"Biometria"` y `"UnifiedVision"`.
  - **Documento de especificación:** `iteraciones/iteracion 11.md`.

### Iteración 12: 4 Perfiles (Modo Desarrollador), Panel de Monitoreo Administrativo, Eliminación Lógica en SQLite, Subproceso Detector de Errores (.md) y Red Neuronal de Intenciones Jev
- **Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)
- **Componentes implementados:**
  - **4° Perfil "Desarrollador" (`MainActivity.kt` & `RoleSelectionScreen.kt`):** Incorporación del rol `UserRole.DESARROLLADOR` exclusivo para pruebas integrales de Christian con acceso a todos los módulos operativos de Sara (`ROBOT_FACE`, `UNIFIED_VISION`, `REMINDERS`, `MUSIC`) y al nuevo laboratorio `JEV_NEURAL_STUDIO`.
  - **Separación de Logs por Módulo y Subproceso Detector de Errores O(1) (`AppLogger.kt` & `LogPanelDialog.kt`):** Aislamiento de buffers de logs independientes por cada `SaraModule`; subproceso asíncrono en segundo plano (`Channel<LogEntry>` en `Dispatchers.Default`) que evalúa incrementalmente cada evento en $O(1)$ sin polling pesado, conmutando el estado de cada módulo entre `OPERANDO` (Verde), `DORMIDO` (Plateado) y `DETENIDO` (Rojo); generador de informes Markdown (`generarInformeErrorMarkdown`) con tabla de errores detectados y traza completa.
  - **Eliminación Lógica en SQLite (`PacienteEntity.kt`, `ContactoEmergenciaEntity.kt`, `PacienteDao.kt`, `ContactoEmergenciaDao.kt` & `SaraAppDatabase.kt` v4):** Adición del atributo booleano `estado: Boolean = true` en `Paciente` y `Contacto_emergencia`; las operaciones de eliminación realizan borrado lógico (`UPDATE ... SET estado = 0`) preservando la integridad histórica y excluyendo registros inactivos de las consultas activas, telemetría e informes.
  - **Nueva Interfaz de Monitoreo del Administrador (`AdminDashboardScreen.kt`):** Panel interactivo con barra lateral izquierda y 5 secciones:
    1. *Pacientes*: Módulo reutilizable de búsqueda (`ReusablePatientSearchModule`) por RUT (`Id_paciente`), Nombre o Apellido; ficha clínica completa de base de datos; botones para Agregar, Modificar y Eliminar (Inactivar).
    2. *Familiares*: Integración del módulo de búsqueda de paciente en la cabecera, listado de contactos de emergencia activos asociados y botones para Agregar, Modificar y Eliminar (Inactivar).
    3. *Recordatorios* y *Música*: Integración directa de los módulos operativos.
    4. *Control de Sara*: Tablero de tarjetas de estado por módulo con indicador LED (Verde/Plateado/Rojo), visor de log individual y botón *"Notificar Error (.md)"* activado automáticamente cuando un módulo se detiene por error.
  - **Red Neuronal On-Device de Detección de Intenciones Jev (`JevNeuralIntentClassifier.kt` & `JevNeuralStudioScreen.kt`):** Implementación desde cero de una red neuronal entrenable en CPU local con lematización en español (stemming), extracción de bigramas y anclas temporales (`<PATRON_HORA>`), diccionario dinámico TF-IDF con normalización $L_2$, capa densa de pesos sinápticos $W \cdot X + b$ con activación Softmax y entrenamiento mediante Backpropagation (Gradiente Descendente + Cross-Entropy Loss), junto con una interfaz interactiva en el perfil Desarrollador para testear frases y entrenar la red en vivo.
  - **Documento de especificación:** `iteraciones/iteracion 12.md`.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
