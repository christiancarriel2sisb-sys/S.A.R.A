# 📘 Instructivo Oficial de Instalación, Configuración y Despliegue — App Móvil S.A.R.A (Android Nativo en Kotlin)

**Proyecto:** S.A.R.A (Sistema Asistente Robótico de Apoyo)  
**Plataforma:** Android Nativo (Kotlin 1.9.23 + Jetpack Compose + Room SQLite v4 + CameraX + TFLite + Red Neuronal Jev)  
**Autor / Responsable:** Christian Carriel — Duoc UC  

---

## 📑 Índice del Instructivo

1. [Requisitos Previos de Software y Hardware](#1-requisitos-previos-de-software-y-hardware)
2. [Arquitectura General y Estructura del Proyecto en Kotlin](#2-arquitectura-general-y-estructura-del-proyecto-en-kotlin)
3. [Paso 1: Configuración de Variables de Entorno y API Keys (`.env`)](#3-paso-1-configuración-de-variables-de-entorno-y-api-keys-env)
4. [Paso 2: Verificación de Modelos IA On-Device y Base de Datos SQLite](#4-paso-2-verificación-de-modelos-ia-on-device-y-base-de-datos-sqlite)
5. [Paso 3: Apertura y Configuración en Android Studio (JDK 17 y 8 GB RAM)](#5-paso-3-apertura-y-configuración-en-android-studio-jdk-17-y-8-gb-ram)
6. [Paso 4: Configuración del Emulador Android (8 GB RAM + Webcam)](#6-paso-4-configuración-del-emulador-android-8-gb-ram--webcam)
7. [Paso 5: Compilación e Instalación en Dispositivo Físico (Teléfono / Tablet del Robot)](#7-paso-5-compilación-e-instalación-en-dispositivo-físico-teléfono--tablet-del-robot)
8. [Paso 6: Configuración de Permisos y Conexión Hardware Arduino (USB-OTG)](#8-paso-6-configuración-de-permisos-y-conexión-hardware-arduino-usb-otg)
9. [Paso 7: Guía de Verificación Funcional de los 4 Perfiles y Módulos](#9-paso-7-guía-de-verificación-funcional-de-los-4-perfiles-y-módulos)
10. [Solución de Problemas Frecuentes (Troubleshooting)](#10-solución-de-problemas-frecuentes-troubleshooting)

---

## 1. Requisitos Previos de Software y Hardware

### 💻 En el Computador de Desarrollo (Windows / Linux / macOS)
* **Sistema Operativo:** Windows 10/11 (64 bits), Linux o macOS.
* **Java Development Kit (JDK):** **JDK 17** (obligatorio para Android Gradle Plugin 8.3.0 y KSP 1.9.23).
  * Ruta estándar en Windows: `C:\Program Files\Java\jdk-17.0.1` (o el JBR incluido en Android Studio).
* **IDE Oficial:** **Android Studio Ladybug (2024.2.1)** o superior.
* **Android SDK:**
  * `compileSdk = 34` (Android 14)
  * `targetSdk = 34` (Android 14)
  * `minSdk = 26` (Compatible desde Android 8.0 Oreo en adelante)
* **Memoria RAM en PC:** Mínimo 16 GB recomendados (8 GB asignados al entorno JVM/AVD).

### 📱 En el Teléfono o Tablet Android (Cerebro del Robot)
* **Versión de Android:** Android 8.0 (API 26) o superior (recomendado Android 11 a 14).
* **Soporte de Hardware:**
  * Cámara frontal operativa.
  * Micrófono y altavoces activos (con motor *Google Speech Services / Android TTS* en español).
  * Soporte **USB Host (USB-OTG)** para enviar comandos seriales a la placa Arduino Uno (`115200 baudios`).

---

## 2. Arquitectura General y Estructura del Proyecto en Kotlin

La raíz del proyecto móvil se ubica en:
```text
S.A.R.A/Fase 2/Interfaz Usuario SARA/android/
```

El código fuente en Kotlin (`app/src/main/java/com/sara/robot/`) está organizado bajo arquitectura **MVVM + Clean Repositories + Reactive StateFlow**:

```text
com.sara.robot/
├── MainActivity.kt                        # Punto de entrada, permisos, enrutador de los 4 Perfiles y Reproductor Global YouTube
│
├── core/                                  # Núcleo transversal y monitoreo de salud
│   ├── AppLogger.kt                       # Logs independientes por módulo + Subproceso O(1) detector de errores + Generador .md
│   ├── SaraPrompt.kt                      # System Prompt clínico, reglas éticas y declaración de Tools (Function Calling)
│   └── TextUtils.kt                       # Sanitización de texto y Sentence Chunking para baja latencia en voz
│
├── data/                                  # Repositorios y Persistencia Local Room SQLite (sara_local_db v4)
│   ├── BiometriaRepository.kt             # Gestión de vectores faciales 192-d, consentimiento y perfiles en RAM
│   ├── ChatRepository.kt                  # Mensajería bidireccional local Paciente <-> Familiar
│   ├── GeminiRepository.kt                # Cliente streaming SSE contra Gemini 2.5 Flash con soporte Barge-In
│   ├── RecordatorioRepository.kt          # Inserción en SQLite + Subproceso de carga diaria en Buffer RAM
│   ├── SecurePreferences.kt               # Cifrado AES-256 (EncryptedSharedPreferences) para API Keys
│   └── local/
│       ├── database/SaraAppDatabase.kt    # Configuración Room Database v4 (7 tablas relacionales)
│       ├── entity/                        # Tablas SQLite (@Entity)
│       │   ├── PacienteEntity.kt          # Tabla Paciente (PK: Id_paciente/RUT, Nombre, ape_p, ape_m, estado: Boolean)
│       │   ├── BiometriaEntity.kt         # Tabla biometria_paciente (Relación 1:1 con Paciente, vector 192 floats CSV)
│       │   ├── ContactoEmergenciaEntity.kt# Tabla Contacto_emergencia (FK a Paciente, teléfono, estado: Boolean)
│       │   ├── RecordatorioEntity.kt      # Tabla recordatorios (FK a Paciente, fecha_recor, descripcion)
│       │   ├── ChatEntity.kt              # Tabla chat (Mensajes entre Paciente y Familiar)
│       │   ├── JuegoEntity.kt             # Tabla Juego
│       │   └── HistorialEstimulacionEntity.kt
│       └── dao/                           # Consultas SQL reactivas (@Dao) con borrado lógico (estado = 0)
│           ├── PacienteDao.kt
│           ├── BiometriaDao.kt
│           ├── ContactoEmergenciaDao.kt
│           ├── RecordatorioDao.kt
│           └── ChatDao.kt
│
├── music/                                 # Subsistema Multimedia
│   ├── ReproductorManager.kt              # Control de estado multimedia, volumen y audio ducking
│   └── YouTubeMusicRepository.kt          # Integración con YouTube Data API v3
│
├── navigation/                            # Navegación, Actuación Física y Red Neuronal Jev
│   ├── actuator/
│   │   ├── MotorCommandFormatter.kt       # Formateador de tramas numéricas Arduino V2 (1, 2, 3, 5, 6)
│   │   └── SerialDispatcher.kt            # Driver USB-OTG a 115200 bps (10 Hz)
│   ├── perception/
│   │   └── SpatialProjector.kt            # Estimación métrica 3D monocular Pinhole (640x360)
│   └── reasoning/
│       ├── JevDecisionEngine.kt           # Motor probabilístico espacial de navegación
│       ├── JevNeuralIntentClassifier.kt   # Red Neuronal de Intenciones On-Device (Diccionario TF-IDF + Softmax + Backprop)
│       ├── NavigationState.kt
│       └── SafetySupervisor.kt            # Freno de emergencia determinista (< 0.40 m)
│
├── reminders/                             # Subsistema de Alarmas Exactas y Memoria RAM
│   ├── AlarmReceiver.kt                   # BroadcastReceiver que despierta a Sara con WakeLock
│   ├── AlarmScheduler.kt                  # Programador nativo con Android AlarmManager
│   └── ReminderMemoryBuffer.kt            # Buffer temporal en RAM con las alertas activas del día
│
├── ui/                                    # Interfaces en Jetpack Compose
│   ├── RoleSelectionScreen.kt             # Pantalla inicial con los 4 perfiles (Usuario, Admin, Familiar, Desarrollador)
│   ├── AdminDashboardScreen.kt            # Panel del Administrador con barra lateral, CRUD lógico y Control de Sara (.md)
│   ├── JevNeuralStudioScreen.kt           # Laboratorio del Desarrollador para testear y entrenar la Red Neuronal Jev
│   ├── UnifiedVisionScreen.kt             # HUD de Reconocimiento y Diferenciación Facial Multirrostro + Consentimiento
│   ├── SaraScreen.kt                      # Rostro animado procedural a 60 FPS + interacción por voz
│   ├── SaraViewModel.kt                   # Orquestador principal de estados, voz, música, chat y recordatorios
│   ├── FamilyChatScreen.kt                # Interfaz de chat privado para el perfil Familiar
│   ├── ReminderScreen.kt                  # Interfaz de gestión de recordatorios y alarmas
│   ├── MusicScreen.kt                     # Interfaz del reproductor de música
│   ├── LogPanelDialog.kt                  # Terminal de logs filtrable por cada módulo independiente
│   └── face/                              # Animación vectorial del rostro de Sara
│
├── vision/                                # Visión Computacional e Inferencia Biométrica
│   ├── MobileFaceNetHelper.kt             # Wrapper TensorFlow Lite para extracción de embeddings de 192 dimensiones
│   ├── UnifiedVisionAnalyzer.kt           # Pipeline multirrostro a 640x360 @ 15 FPS con emparejamiento exclusivo 1 a 1
│   └── FaceAnalyzer.kt                    # Seguimiento ocular ligero para el rostro de Sara
│
└── voice/                                 # Subsistema de Voz Nativa (0 Tokens)
    ├── TextToSpeechManager.kt             # Síntesis de voz Android TTS (Google TTS es-CL) con cola de oraciones
    └── VoiceInputManager.kt               # Captura de voz SpeechRecognizer con detector de interrupción (Barge-In)
```

---

## 3. Paso 1: Configuración de Variables de Entorno y API Keys (`.env`)

La aplicación utiliza un archivo `.env` en la carpeta `android/` para inyectar las claves de forma segura en tiempo de compilación sin exponerlas en el código fuente:

1. Dirígete a la carpeta:
   `S.A.R.A/Fase 2/Interfaz Usuario SARA/android/`
2. Crea o edita el archivo llamado **`.env`** con la siguiente estructura:
   ```env
   GEMINI_API_KEY=AIzaSy_TU_CLAVE_DE_GEMINI_AQUI
   GOOGLE_CLOUD_API_KEY=AIzaSy_TU_CLAVE_DE_YOUTUBE_API_V3_AQUI
   ```
3. **¿Cómo funciona por dentro?**
   - El archivo `android/app/build.gradle.kts` lee automáticamente `.env` al compilar y genera `BuildConfig.GEMINI_API_KEY` y `BuildConfig.GOOGLE_CLOUD_API_KEY`.
   - Al arrancar la app, `SecurePreferences.kt` guarda las claves cifradas con **AES-256** en el Keystore de Android.

---

## 4. Paso 2: Verificación de Modelos IA On-Device y Base de Datos SQLite

Antes de compilar, verifica que el modelo biométrico TensorFlow Lite esté en su lugar:

* **Ruta del modelo MobileFaceNet:**
  `S.A.R.A/Fase 2/Interfaz Usuario SARA/android/app/src/main/assets/mobilefacenet.tflite`
  *(Tamaño aproximado: ~5.2 MB. Genera vectores normalizados $L_2$ de 192 dimensiones en ~12 ms en CPU).*
* **Base de Datos SQLite (`sara_local_db`):**
  No requiere instalación externa. El compilador **KSP + Room (`2.6.1`)** construye automáticamente la base de datos relacional versión 4 dentro del teléfono al iniciar la aplicación por primera vez.

---

## 5. Paso 3: Apertura y Configuración en Android Studio (JDK 17 y 8 GB RAM)

1. Abre **Android Studio**.
2. Selecciona **File ➔ Open** y elige exactamente la carpeta:
   `S.A.R.A/Fase 2/Interfaz Usuario SARA/android`
3. **Verificar JDK 17:**
   - Ve a **File ➔ Settings ➔ Build, Execution, Deployment ➔ Build Tools ➔ Gradle**.
   - En **Gradle JDK**, asegúrate de que esté seleccionado **JDK 17** (ej. `jdk-17.0.1` o `JetBrains Runtime 17`).
4. **Configuración de 8 GB de RAM para Gradle (Ya aplicada en el proyecto):**
   - En `android/gradle.properties` ya está configurado:
     ```properties
     org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=2048m -Dfile.encoding=UTF-8
     ```
   - Y en `AndroidManifest.xml` está activo `android:largeHeap="true"`.
5. Espera a que termine el proceso **Gradle Sync**.

---

## 6. Paso 4: Configuración del Emulador Android (8 GB RAM + Webcam)

Si vas a ejecutar y testear el proyecto en el emulador de Android Studio:

1. Abre el **Device Manager** en Android Studio y crea o edita tu dispositivo virtual (ej. `Medium_Phone_API_35`).
2. Para asignar **8 GB de RAM** y activar tu **cámara web física** sin bloqueos de la interfaz gráfica, abre el archivo de configuración del emulador en tu PC:
   `C:\Users\<Tu_Usuario>\.android\avd\Medium_Phone_API_35.avd\config.ini`
3. Verifica que contenga los siguientes parámetros:
   ```ini
   hw.ramSize = 8192
   vm.heapSize = 1024
   hw.camera.front = webcam0
   hw.camera.back = virtualscene
   ```
   *(Nota: Si tu PC asigna la webcam al canal trasero del emulador, el módulo de visión de Sara lo detecta automáticamente mediante `isActuallyFrontCamera` y ajusta el encuadre sin espejado erróneo).*
4. Inicia el emulador haciendo clic en **Cold Boot Now** para que tome los 8 GB de RAM.

---

## 7. Paso 5: Compilación e Instalación en Dispositivo Físico (Teléfono / Tablet del Robot)

### Opción A: Instalación Directa por Cable USB desde Terminal (PowerShell)
1. En el teléfono Android:
   - Ve a **Ajustes ➔ Acerca del teléfono** y toca 7 veces sobre **Número de compilación** para activar las *Opciones de desarrollador*.
   - Entra a **Sistema ➔ Opciones de desarrollador** y activa **Depuración por USB**.
   - Conecta el teléfono al PC por USB y acepta el cartel *"¿Permitir depuración por USB?"*.
2. Abre PowerShell en la raíz del proyecto y ejecuta:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.1"
   Set-Location "S.A.R.A\Fase 2\Interfaz Usuario SARA\android"

   # Verificar que el código Kotlin compile sin errores
   .\gradlew.bat compileDebugKotlin

   # Compilar e instalar directamente en el teléfono o emulador conectado
   .\gradlew.bat installDebug
   ```

### Opción B: Generar el Archivo Instalable `.apk`
Si deseas generar el archivo `.apk` para copiarlo a varios teléfonos:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.1"
Set-Location "S.A.R.A\Fase 2\Interfaz Usuario SARA\android"
.\gradlew.bat assembleDebug
```
* **Ubicación del APK generado:**
  `S.A.R.A/Fase 2/Interfaz Usuario SARA/android/app/build/outputs/apk/debug/app-debug.apk`

---

## 8. Paso 6: Configuración de Permisos y Conexión Hardware Arduino (USB-OTG)

Al abrir **SARA Robot** por primera vez en el teléfono:

1. **Permisos en Pantalla:** Acepta los permisos de **Micrófono (`RECORD_AUDIO`)** y **Cámara (`CAMERA`)** cuando aparezca el diálogo del sistema.
2. **Alarmas Exactas (Android 12 / 13 / 14):**
   - El módulo de recordatorios utiliza `AlarmManager.setExactAndAllowWhileIdle` para despertar a Sara en el segundo exacto. Si usas Android 13+, verifica en *Ajustes ➔ Aplicaciones ➔ SARA Robot* que estén permitidas las **Notificaciones** y **Alarmas y recordatorios**.
3. **Conexión con el Chasis Arduino Uno (`prueba_sara_v2.ino`):**
   - Conecta el adaptador **USB-OTG** al puerto USB-C del teléfono y el cable USB a la placa **Arduino Uno**.
   - Android mostrará una ventana emergente: *"¿Permitir que SARA Robot acceda al dispositivo USB?"* $\rightarrow$ Marca **"Usar siempre"** y presiona **Aceptar**.
   - El módulo `SerialDispatcher.kt` establecerá enlace a **115200 baudios** para enviar las tramas de movimiento (`1` Avanzar, `2` Izquierda, `3` Derecha, `6` Detener).

---

## 9. Paso 7: Guía de Verificación Funcional de los 4 Perfiles y Módulos

Al iniciar la aplicación en orientación horizontal (*Landscape*), verás la pantalla **SELECCIÓN DE PERFIL** con las **4 tarjetas de rol**:

### 🟢 1. Perfil: Usuario (Paciente)
* Muestra únicamente el rostro animado de Sara a 60 FPS.
* Toca la pantalla o háblale para conversar, pedir música (*"Sara, pon música de..."*), crear recordatorios (*"Sara, recuérdame a las 5 tomar agua"*) o enviar mensajes a familiares (*"Sara, dile a Claudia que ya almorcé"*).

### 🔵 2. Perfil: Administrador (Monitoreo de Pacientes y Robot)
Abre el panel interactivo con **barra lateral izquierda**:
* **Pacientes:**
  * Usa la barra superior para buscar pacientes activos por **RUT (`Id_paciente`)**, **Nombre** o **Apellido**.
  * Selecciona un paciente para ver su **Ficha Clínica Completa de Base de Datos** a la derecha.
  * Usa los 3 botones superiores: **Agregar Paciente**, **Modificar Paciente** y **Eliminar (Inactivar)** (que cambia `estado = false` en SQLite sin destruir los datos).
* **Familiares:**
  * Arriba utiliza el mismo módulo buscador de pacientes por RUT/Nombre/Apellido.
  * Abajo muestra los **Contactos de Emergencia** asociados a ese paciente y los botones **Agregar Contacto**, **Modificar Contacto** y **Eliminar (Inactivar)**.
* **Recordatorios y Música:** Acceso directo a ambos módulos desde la barra lateral.
* **Control de Sara:**
  * Muestra un cuadro por cada uno de los 8 módulos con su log independiente y su **Luz LED de estado**:
    * 🟢 **Verde (`OPERANDO`):** Módulo activo y sin errores.
    * ⚪ **Plateado (`DORMIDO`):** Módulo en reposo sin errores.
    * 🔴 **Rojo (`DETENIDO`):** El subproceso detectó un error en el log del módulo.
  * Cuando un módulo está en **Rojo**, se activa el botón **`[NOTIFICAR ERROR (.MD)]`**, el cual genera y guarda en disco el informe Markdown con todos los errores detectados en el log. *(Puedes pulsar `"Simular Fallo"` en cualquier tarjeta para probar esta función en vivo).*

### 🟣 3. Perfil: Familiar
* Abre el chat privado de texto conectado a la tabla `chat` de SQLite. Permite elegir el familiar y enviar mensajes al paciente (que Sara le leerá en voz alta automáticamente).

### 🟠 4. Perfil: Desarrollador (Laboratorio de Christian)
* Tiene acceso a **todos los módulos operativos de Sara**:
  * **Rostro y Voz de Sara**
  * **Reconocimiento y Diferenciación Facial Multirrostro (Ícono Cámara):** Detecta múltiples rostros a $640 \times 360\text{ px}$, marca como `⚠️ Sin registro` a personas desconocidas, pide **Consentimiento Informado** y luego solicita el **Nombre** para guardarlo en SQLite (`Paciente` + `biometria_paciente` 1:1). Permite elegir **`🎯 Ir a: [Persona]`** para que Sara distinga a esa persona entre varias y calcule el giro ($\theta$) y distancia ($Z$).
  * **Laboratorio de Red Neuronal Jev (Ícono Cerebro `🧠`):**
    * Prueba cualquier frase en la barra de texto (ej. *"a las 10, tengo que tomar mi medicamento"*).
    * Visualiza en microsegundos el módulo ganador, las barras de probabilidad **Softmax**, las raíces y bigramas activados en el diccionario **TF-IDF**, y **entrena la red en vivo con nuevas frases mediante Backpropagation**.

---

## 10. Solución de Problemas Frecuentes (Troubleshooting)

| Síntoma / Mensaje | Causa Probable | Solución Rápida |
| :--- | :--- | :--- |
| `JAVA_HOME is not set` o error de JVM al compilar por consola | La terminal no apunta a JDK 17 | Ejecuta `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.1"` antes de correr `.\gradlew.bat`. |
| Módulo de Conversación aparece en **Rojo (`DETENIDO`)** en Control de Sara | Falta `GEMINI_API_KEY` en `.env` o no hay internet en el emulador | Verifica el archivo `android/.env` y presiona `[NOTIFICAR ERROR (.MD)]` en Control de Sara para ver el detalle exacto. |
| La cámara en el emulador muestra `SIN CÁMARA` | El AVD tiene las cámaras en `none` | En `config.ini` del AVD pon `hw.camera.front = webcam0` y reinicia el emulador con *Cold Boot*. |
| Quiero ver las tablas de SQLite en vivo mientras pruebo | Inspección de base de datos Room | En Android Studio abre la pestaña inferior **App Inspection ➔ Database Inspector** y selecciona `sara_local_db`. |

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
