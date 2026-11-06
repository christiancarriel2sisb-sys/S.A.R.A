# Sara Robot — Aplicación Nativa Android (Kotlin)

> **Cerebro Superior, Percepción Visual y Control del Robot S.A.R.A**  
> Desarrollado en Kotlin con Jetpack Compose para smartphones Android (100% On-Device).

---

## 📋 Resumen del Módulo

Esta aplicación constituye el **núcleo cognitivo y de control** del robot Sara. Corre directamente en el smartphone montado sobre el chasis y es responsable de:
1. **Interacción Conversacional por Voz:** Integración directa con Google Gemini 2.5 Flash mediante SSE (Server-Sent Events), sintetizador de voz (TTS) y cancelación inmediata por voz del usuario (*Barge-In*).
2. **Percepción Visual en Tiempo Real:** Detección de presencia y rostros mediante CameraX y Google ML Kit, calculando la desviación angular para orientar el robot hacia el usuario.
3. **Navegación Reactiva y Estimación Espacial:** Proyección métrica monocular (Pinhole 3D), motor de toma de decisiones Jev (campos de fuerza) y supervisor de seguridad ante colisiones (< 0.40 m).
4. **Enlace Serial USB-OTG:** Envío determinista a 10 Hz de comandos de navegación (`NAV:<angle>,<pwm>\n`) a la placa Arduino Uno que comanda los motores.

---

## 🏛️ Arquitectura de Paquetes (`com.sara.robot`)

El proyecto sigue el patrón de arquitectura recomendada por Android (MVVM + StateFlow + Unidirectional Data Flow):

```text
com.sara.robot/
├── MainActivity.kt                  # Punto de entrada, gestión de permisos en tiempo de ejecución y pestañas
│
├── core/                            # Reglas centrales del robot y utilidades de procesamiento
│   ├── AppLogger.kt                 # Sistema de registro en memoria observable para depuración en vivo
│   ├── SaraPrompt.kt                # Prompt del sistema, saludos y reglas éticas de no prescripción clínica
│   └── TextUtils.kt                 # Sanitización de Markdown y fragmentador de oraciones (Sentence Chunking)
│
├── data/                            # Capa de datos, APIs de red y almacenamiento seguro
│   ├── GeminiRepository.kt          # Cliente HTTP SSE contra Gemini 2.5 Flash con corrutinas cancelables
│   └── SecurePreferences.kt         # Almacenamiento cifrado de API Key con Android Keystore (AES-256)
│
├── navigation/                      # Subsistema de navegación autónoma y actuación física
│   ├── actuator/
│   │   ├── MotorCommandFormatter.kt # Formateador del protocolo de texto ("NAV:<angle>,<pwm>\n")
│   │   └── SerialDispatcher.kt      # Driver USB-OTG (CH34x/FTDI/CP210x/CDC) con loop a 10 Hz
│   │
│   ├── perception/
│   │   ├── CameraManager.kt         # Controlador de CameraX configurado a 320x320 @ 10 FPS (DROP_LATEST)
│   │   ├── SpatialProjector.kt      # Estimación métrica Z y desviación X mediante modelo Pinhole
│   │   └── YoloDetector.kt          # Inferencia visual monocular de objetos
│   │
│   └── reasoning/
│       ├── JevDecisionEngine.kt     # Motor reactivo de atracción/repulsión de campos de potencial
│       ├── NavigationState.kt       # Modelos inmutables de telemetría y vectores cinemáticos
│       └── SafetySupervisor.kt      # Guardián de seguridad con parada de emergencia ante Z < 0.40m
│
├── ui/                              # Capa de presentación visual en Jetpack Compose
│   ├── face/
│   │   ├── FaceAnimator.kt          # Animación del rostro a 60 FPS (parpadeo, micro-sacadas y sincronía labial)
│   │   └── FaceComponent.kt         # Dibujado SVG procedural reactivo a emociones y audio
│   │
│   ├── DictadoScreen.kt             # Pantalla de verificación del motor de reconocimiento de voz (CU-1)
│   ├── FaceDetectionScreen.kt       # Pantalla de visión artificial con overlay de detección y ángulos
│   ├── LogPanelDialog.kt            # Modal flotante de telemetría y depuración en vivo
│   ├── MainScreen.kt                # Contenedor de pestañas y barra superior
│   ├── NavigationScreen.kt          # HUD de navegación con radar polar y métricas del motor Jev
│   ├── SaraGeminiScreen.kt          # Pantalla de conversación con métricas de red y streaming
│   ├── SaraScreen.kt                # Interfaz principal de Sara (Rostro completo + Interacción 100% voz)
│   └── SaraViewModel.kt             # ViewModel unificado que orquesta voz, visión, IA y actuadores
│
├── vision/                          # Procesamiento de visión con ML Kit
│   └── FaceAnalyzer.kt              # Analizador de fotogramas ImageAnalysis de CameraX para rostros
│
└── voice/                           # Subsistema de audio y voz
    ├── TextToSpeechManager.kt       # Motor TTS nativo en español con control de cola
    └── VoiceInputManager.kt         # Reconocedor de voz continuo (SpeechRecognizer) con autoreinicio
```

---

## 🔧 Requisitos de Hardware y Software

* **Dispositivo Móvil:** Android 8.0 (API 26) o superior (recomendado Android 11+). Soporte para USB Host (OTG).
* **Entorno de Desarrollo:** Android Studio Ladybug (2024.2.1) o superior.
* **JDK:** Java 17 o superior.
* **Hardware Externo:**
  * Cable adaptador USB-OTG (Tipo C o Micro-USB a USB-A hembra).
  * Placa Arduino Uno (con convertidor USB CH340, ATmega16U2 o FTDI).

---

## 🔑 Configuración del Entorno (`.env`)

1. En la carpeta `Fase 2/Interfaz Usuario SARA/android/` crea un archivo `.env` (si no existe):
   ```env
   GEMINI_API_KEY=AIzaSy...TuClaveAqui...
   ```
2. El script `app/build.gradle.kts` lee automáticamente este valor en tiempo de compilación y lo inyecta en `BuildConfig.GEMINI_API_KEY`, almacenándose de forma segura en `SecurePreferences` al iniciar la app.
3. El archivo `.env` se encuentra ignorado por Git para proteger la clave.

---

## 🚀 Compilación y Despliegue

### Desde la Terminal (CLI)
```bash
cd "Fase 2/Interfaz Usuario SARA/android"

# Compilar el APK de depuración
./gradlew assembleDebug

# Instalar y ejecutar en el dispositivo conectado
./gradlew installDebug
```

### Desde Android Studio
1. File -> Open -> Seleccionar la carpeta `android`.
2. Esperar la sincronización de Gradle (*Sync Project with Gradle Files*).
3. Seleccionar tu dispositivo físico o emulador.
4. Presionar **Run 'app'** (`Shift + F10`).

---

## 📡 Protocolo de Comunicación Serial USB

El módulo `SerialDispatcher.kt` gestiona la conexión con el Arduino mediante la librería `usb-serial-for-android`:

* **Baudrate:** 115200 bps
* **Data bits:** 8
* **Stop bits:** 1
* **Paridad:** Ninguna
* **Frecuencia de emisión:** 10 Hz (cada 100 ms)

### Tramas Enviadas:
1. **Navegación normal:**
   ```text
   NAV:<angulo>,<pwm>\n
   ```
   * `<angulo>`: Entero entre -90 (giro máximo a la izquierda) y +90 (giro a la derecha). 0 indica avance recto.
   * `<pwm>`: Potencia del motor entre 0 (detenido) y 255 (velocidad máxima).
   * *Ejemplo:* `NAV:15,180\n`

2. **Parada de emergencia:**
   ```text
   EMERGENCY_STOP\n
   ```
   * Enviado de inmediato cuando un obstáculo se encuentra a menos de 40 cm ($Z < 0.40\text{ m}$).

---

## 🛡️ Permisos Declarados en `AndroidManifest.xml`

* `android.permission.RECORD_AUDIO`: Captura continua de la voz del usuario.
* `android.permission.CAMERA`: Captura de video para ML Kit y navegación.
* `android.permission.INTERNET`: Comunicación con los servidores de Google Gemini.
* `android.hardware.usb.host`: Permiso de host USB para comunicación con Arduino por USB-OTG.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
