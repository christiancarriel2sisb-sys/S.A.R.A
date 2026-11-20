# Sara Robot — Aplicación Nativa Android (Kotlin)

> **Cerebro Cognitivo, Reconocimiento Biométrico Multirrostro, Panel Clínico Administrativo y Red Neuronal Jev On-Device**  
> Desarrollado en Kotlin con Jetpack Compose, Room SQLite v4, CameraX, TensorFlow Lite (MobileFaceNet) y Red Neuronal Jev.
>
> 📘 **Nota:** Para la guía paso a paso completa de instalación, configuración de 8 GB de RAM, variables `.env`, emulador y despliegue en teléfono físico, consulta también el [INSTRUCTIVO_INSTALACION_KOTLIN.md](../INSTRUCTIVO_INSTALACION_KOTLIN.md).

---

## 📋 Resumen del Sistema Móvil

Esta aplicación constituye el **núcleo completo de interacción, visión, persistencia y monitoreo** del robot S.A.R.A, estructurado en **4 Perfiles de Operación**:

1. **Perfil Usuario (Paciente):** Interacción 100% por voz con el rostro animado de Sara a 60 FPS, streaming con Gemini 2.5 Flash, síntesis nativa Android TTS (Google TTS `es-CL`) e interrupción instantánea (*Barge-In*).
2. **Perfil Administrador (Monitoreo de Pacientes y Robot):** Panel interactivo con barra lateral izquierda para gestión de **Pacientes** (búsqueda reutilizable por RUT/Nombre/Apellido, ficha clínica y borrado lógico `estado = false`), **Familiares** (contactos de emergencia por paciente), **Recordatorios**, **Música** y **Control de Sara** (monitoreo por módulo con luces LED Verde/Plateado/Rojo, subproceso detector de errores $O(1)$ y generación de informes `.md`).
3. **Perfil Familiar:** Chat privado bidireccional de texto en SQLite entre familiares y el paciente.
4. **Perfil Desarrollador (Laboratorio y Testing):** Acceso completo a todos los módulos operativos de Sara, incluyendo el **Módulo de Reconocimiento y Diferenciación Facial Multirrostro** (MobileFaceNet 192-d, flujo `"Sin registro"` $\rightarrow$ Consentimiento $\rightarrow$ Nombre y selector `"🎯 Ir a: [Persona]"`) y el **Laboratorio de la Red Neuronal de Intenciones Jev** (Diccionario TF-IDF + Stemming + Softmax + Backpropagation en vivo).

---

## 🏛️ Arquitectura de Paquetes (`com.sara.robot`)

```text
com.sara.robot/
├── MainActivity.kt                        # Punto de entrada, permisos y enrutador de los 4 Perfiles
├── core/
│   ├── AppLogger.kt                       # Logs independientes por módulo + Subproceso detector de errores + Informe .md
│   ├── SaraPrompt.kt                      # System Prompt clínico y declaración de Tools
│   └── TextUtils.kt                       # Sanitización y fragmentador de oraciones (Sentence Chunking)
├── data/
│   ├── BiometriaRepository.kt             # Vectores faciales 192-d, consentimiento y caché en RAM
│   ├── ChatRepository.kt                  # Mensajería local Paciente <-> Familiar
│   ├── GeminiRepository.kt                # Cliente HTTP SSE contra Gemini 2.5 Flash
│   ├── RecordatorioRepository.kt          # SQLite + Subproceso diario en Buffer RAM
│   ├── SecurePreferences.kt               # Cifrado AES-256 de API Keys
│   └── local/                             # Room SQLite Database (sara_local_db v4)
│       ├── database/SaraAppDatabase.kt
│       ├── entity/                        # 7 Entidades (Paciente, Biometria, Contacto_emergencia, Recordatorio, Chat, etc.)
│       └── dao/                           # DAOs con soporte de eliminación lógica (estado = Boolean)
├── music/
│   ├── ReproductorManager.kt              # Control multimedia y audio ducking
│   └── YouTubeMusicRepository.kt          # YouTube Data API v3
├── navigation/
│   ├── actuator/                          # SerialDispatcher (USB-OTG 115200 bps) y MotorCommandFormatter
│   ├── perception/                        # SpatialProjector (Pinhole 3D a 640x360)
│   └── reasoning/
│       ├── JevDecisionEngine.kt           # Clasificador espacial de navegación
│       ├── JevNeuralIntentClassifier.kt   # Red Neuronal de Intenciones On-Device (TF-IDF + Softmax + Backpropagation)
│       └── SafetySupervisor.kt            # Parada de emergencia (< 0.40 m)
├── reminders/                             # AlarmScheduler, AlarmReceiver y ReminderMemoryBuffer
├── ui/
│   ├── RoleSelectionScreen.kt             # Selección de los 4 perfiles
│   ├── AdminDashboardScreen.kt            # Panel de monitoreo del Administrador + Control de Sara (.md)
│   ├── JevNeuralStudioScreen.kt           # Laboratorio de testeo y entrenamiento de la Red Neuronal Jev
│   ├── UnifiedVisionScreen.kt             # Reconocimiento facial multirrostro + Consentimiento + "Ir a: [Persona]"
│   ├── SaraScreen.kt & SaraViewModel.kt   # Rostro animado y orquestador principal
│   ├── FamilyChatScreen.kt                # Chat privado familiar
│   ├── ReminderScreen.kt & MusicScreen.kt # Módulos de recordatorios y música
│   └── LogPanelDialog.kt                  # Visor de logs filtrable por módulo independiente
├── vision/
│   ├── MobileFaceNetHelper.kt             # Intérprete TFLite (mobilefacenet.tflite -> 192 floats L2)
│   └── UnifiedVisionAnalyzer.kt           # Detección multirrostro 640x360 @ 15 FPS + Emparejamiento exclusivo 1 a 1
└── voice/
    ├── TextToSpeechManager.kt             # Android Native TTS (Google TTS es-CL)
    └── VoiceInputManager.kt               # SpeechRecognizer continuo con Barge-In
```

---

## 🚀 Instalación Rápida

### 1. Configurar `.env` en `android/.env`
```env
GEMINI_API_KEY=AIzaSy...TuClaveGemini...
GOOGLE_CLOUD_API_KEY=AIzaSy...TuClaveYouTube...
```

### 2. Compilar y Desplegar (PowerShell con JDK 17)
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.1"
Set-Location "S.A.R.A\Fase 2\Interfaz Usuario SARA\android"

# Verificar compilación Kotlin y KSP (Room SQLite)
.\gradlew.bat compileDebugKotlin

# Instalar en emulador o teléfono conectado por USB
.\gradlew.bat installDebug
```

Para el detalle completo de configuración de 8 GB de RAM en el emulador, permisos de Android y guía de uso de cada perfil, abre [INSTRUCTIVO_INSTALACION_KOTLIN.md](../INSTRUCTIVO_INSTALACION_KOTLIN.md).

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
