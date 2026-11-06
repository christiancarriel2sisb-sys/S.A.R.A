# S.A.R.A - Sistema Asistente Robótico de Apoyo

> **Robot Asistente Móvil e Inteligente para Adultos Mayores y Entornos Asistenciales**  
> Proyecto de Título (Capstone) — Duoc UC Sede Plaza Oeste (2026)

---

## 👥 Integrantes del Equipo

* **Christian Carriel** — Arquitectura de Software, Inteligencia Conversacional (Android/Gemini) e Integración Serial
* **Francisca Otárola** — Interacción Humano-Robot, Usabilidad y Validación de Casos de Uso
* **Kevin Castro** — Hardware, Control de Motores (Arduino) y Estructura Robótica

### 🎓 Información Académica
* **Institución:** Duoc UC — Sede Plaza Oeste
* **Carrera:** Ingeniería en Informática
* **Asignatura:** Capstone (APT122)
* **Docente Guía:** Arturo Vargas

---

## 🎯 Descripción General del Proyecto

**S.A.R.A** (*Sistema Asistente Robótico de Apoyo*) es una plataforma robótica autónoma diseñada específicamente para brindar asistencia cotidiana, compañía y acompañamiento activo a **adultos mayores** y personas en entornos de cuidado o reposo.

A diferencia de los asistentes virtuales tradicionales que requieren interacción táctil o interfaces web complejas, SARA integra:
1. **Un smartphone montado como rostro y cerebro superior**, encargado del procesamiento cognitivo, visión por computadora en tiempo real y comunicación natural 100% por voz.
2. **Una base motriz robótica gobernada por Arduino**, encargada de la tracción diferencial, evitación de colisiones por ultrasonido y navegación segura en interiores.
3. **Comunicación física por USB-OTG directa**, logrando un desacoplamiento limpio entre la inteligencia de alto nivel (Android/Kotlin) y el control de bajo nivel en tiempo real (Arduino C++).

---

## 🏗️ Arquitectura General del Sistema

```
                      ┌────────────────────────────────────────────────────────┐
                      │              SMARTPHONE (ANDROID NATIVO)               │
                      │               Cerebro Superior & Rostro                │
                      └──────────────────────────┬─────────────────────────────┘
                                                 │
                        ┌────────────────────────┴────────────────────────┐
                        ▼                                                 ▼
        ┌───────────────────────────────┐                 ┌───────────────────────────────┐
        │  MÓDULO DE VOZ Y CONVERSACIÓN │                 │  MÓDULO DE VISIÓN Y CONTROL   │
        ├───────────────────────────────┤                 ├───────────────────────────────┤
        │ • SpeechRecognizer (es-CL)    │                 │ • CameraX (320x320 @ 10 FPS)  │
        │ • Streaming SSE (Gemini 2.5)  │                 │ • ML Kit Face Detection       │
        │ • Barge-In (Interrupción)     │                 │ • Pinhole 3D Projection       │
        │ • Sentence Chunking + TTS     │                 │ • Motor Jev (Campos Fuerza)   │
        │ • Rostro Emocional SVG (60FPS)│                 │ • Supervisor de Seguridad     │
        └───────────────────────────────┘                 └───────────────┬───────────────┘
                                                                          │
                                                 ┌────────────────────────┴────────────┐
                                                 │ Protocolo Serial USB (115200 baud)  │
                                                 │ "NAV:<angle>,<pwm>\n"               │
                                                 │ "EMERGENCY_STOP\n"                  │
                                                 └────────────────────────┬────────────┘
                                                                          │ (Cable USB-OTG)
                                                 ┌────────────────────────▼────────────┐
                                                 │        CHASIS / ARDUINO UNO         │
                                                 │       Control de Bajo Nivel         │
                                                 ├─────────────────────────────────────┤
                                                 │ • Driver L298N (Tracción Diferencial)
                                                 │ • Sensor Ultrasónico HC-SR04        │
                                                 │ • Servomotor de barrido (Pin 3)     │
                                                 │ • Baterías 18650 / Step-Down LM2596 │
                                                 └─────────────────────────────────────┘
```

---

## 🚀 Módulos e Iteraciones Desarrolladas (Fase 2)

### 1. Núcleo Android Nativo (`Sprint 1`)
- Migración integral de la arquitectura web inicial hacia una aplicación **100% nativa en Kotlin** y **Jetpack Compose**.
- Inyección segura de API Key mediante `EncryptedSharedPreferences` y Android Keystore (cifrado AES-256).
- Eliminación de intermediarios o servidores proxy locales; conexión directa y ultraligera a la API de Gemini.

### 2. Iteración 1: Conversación Fluida con *Barge-In* e Interacción Solo Voz
- **Streaming SSE con Gemini 2.5 Flash:** Consumo reactivo token por token sin retardos de buffering completo.
- **Fragmentador de Oraciones (*Sentence Chunking*):** Agrupa tokens hasta detectar signos de puntuación (`.`, `?`, `!`) despachando audio al sintetizador de voz (TTS) con latencia mínima (< 1.2 segundos).
- **Interrupción en Tiempo Real (*Barge-In*):** Si el usuario habla mientras SARA está respondiendo, el sistema cancela inmediatamente la corrutina de red, detiene el reproductor de voz (`TTS.stop()`) y vuelve a escuchar al instante.
- **Diseño Adaptativo Senior:** Se eliminaron las barras de texto o teclados virtuales; toda la interacción es **100% natural y por voz**.

### 3. Iteración 2: Visión y Seguimiento Facial (Presencia Humana)
- Pipeline con **CameraX** y **Google ML Kit Face Detection** monocular.
- Estimación de desviación angular horizontal ($\theta$) y vertical ($\phi$) relativa al centro óptico de la cámara.
- Sincronización con el rostro animado a 60 FPS: las pupilas y la mirada de SARA siguen activamente la posición de la persona detectada.

### 4. Iteración 3: Navegación Reactiva y Seguridad Embebida
- **Percepción Visual Optimizada:** Muestreo a 320x320 píxeles a 10 FPS con estrategia `DROP_LATEST` para prevenir sobrecalentamiento y saturación de RAM en teléfonos de gama baja.
- **Proyección 3D Monocular (Modelo Pinhole):** Estimación métrica continua de distancia ($Z$) y desviación lateral ($X$) sin necesidad de sensores LIDAR costosos.
- **Motor de Decisión Jev:** Algoritmo reactivo de campos de potencial vectorial que genera atracciones hacia objetivos y repulsiones frente a obstáculos.
- **Supervisor de Seguridad Crítico:** Parada de emergencia instantánea e irrevocable ante proximidad crítica ($< 0.40\text{ m}$).
- **Despachador Serial USB-OTG:** Envío determinista a 10 Hz del vector cinemático (`NAV:<angle>,<pwm>\n`) a través de chipsets CH34x, FTDI, CP210x o CDC/ACM.
- **HUD de Telemetría:** Pantalla de ingeniería con radar polar, vector de dirección y monitoreo de baudios en tiempo real.

### 5. Iteración 4: Detección Facial HUD y Bounding Box en Tiempo Real (Módulo de Voz y Visión)
- **Overlay HUD Futurista:** Retícula visual con brackets neón cian (`#00F0FF`), líneas punteadas y cruz central sobre la persona detectada.
- **Doble Modo de Operación:**
  - *En pantalla de cámara (`FaceDetectionScreen`):* Proyección directa y precisa sobre el video en vivo con escalado de aspecto `FILL_CENTER` y corrección de efecto espejo frontal.
  - *En pantalla de voz (`SaraScreen`):* Captura invisible en segundo plano con CameraX (sin mostrar video) que superpone el cuadro sobre la cara animada de Sara para confirmar la detección del usuario sin romper la estética del robot.
- **Telemetría de Objetivo:** Indicador dinámico `ROSTRO FIJADO [x.x°]` con desviación angular en tiempo real.

### 6. Iteración 5: Estabilización Continua de Obstáculos y Fusión con Arduino V2 (Fran)
- **Control Atómico contra Agotamiento de Buffers:** Implementación de `isProcessing` (`AtomicBoolean`) para descartar fotogramas si la inferencia previa aún no concluye, eliminando el congelamiento y la desaparición de cuadros en CameraX.
- **Despacho Seguro al Hilo de UI:** Los resultados de ML Kit y el motor Jev se emiten sobre `Dispatchers.Main`, garantizando renderizado fluido de Compose a 60 FPS.
- **Integración con Arduino V2 (`prueba_sara_v2.ino`):** El motor Jev ahora despacha comandos numéricos directos (`1` Avanzar, `2` Giro Izquierda, `3` Giro Derecha, `6` Detener/Emergencia) a 115200 baudios por USB-OTG, sincronizándose con el nuevo firmware del chasis de Francisca.
- **HUD Táctico con Etiquetas Métricas:** Bounding boxes con indicador visual del objeto y distancia estimada en metros (`Persona [1.4m]`, `Silla [0.8m]`).

### 7. Iteración 6: Optimización de Hardware en Emulador, Generación de APK y Despliegue Físico
- **Auditoría de Hardware y Cómputo:** Mapeo de ejecución: Jev (CPU local, <1 ms), ML Kit (CPU local con ARM NEON, 320x320 @ 10 FPS), Gemini (Google Cloud TPUs, dependiente de red) y Arduino (microcontrolador ATmega328P para motores).
- **Desbloqueo de Recursos de AVD (`config.ini`):** Modificación física de `Medium_Phone_API_35.avd/config.ini` sobrepasando el bloqueo de Google Play Store para asignar `hw.ramSize=6144` (6 GB RAM), `vm.heapSize=512` y 6 núcleos de CPU.
- **Compilación de Artefacto Android:** Generación limpia de `app-debug.apk` con Gradle y JDK 17 (`BUILD SUCCESSFUL in 15s`).
- **Manual de Instalación en Celular Físico:** Guía detallada en `iteraciones/iteracion 6.md` con métodos de transferencia directa (Drive/WhatsApp/USB) y consola ADB, permisos de tiempo de ejecución y configuración de orígenes desconocidos.

---

## 📂 Estructura del Repositorio

```text
S.A.R.A/
├── .gitignore                      # Configuración global de exclusiones (ignora .env, build/, etc.)
├── README.md                       # Documentación general del proyecto (este archivo)
│
├── Fase 1/                         # Hitos fundacionales, levantamiento de requisitos y CU iniciales
│
└── Fase 2/                         # Etapa actual de desarrollo y prototipado funcional
    ├── Documentos Fase 2/          # Entregables académicos, diarios de reflexión y guías Duoc UC
    │
    ├── Interfaz Usuario SARA/      # Módulo de Inteligencia Superior y UI
    │   ├── PROGRESO.md             # Bitácora detallada de avances por iteración
    │   ├── iteraciones/            # Documentos de especificación técnica (Iteraciones 1 a 6)
    │   │   ├── iteracion 1.md      # Especificación: Streaming SSE + Barge-In
    │   │   ├── iteracion 2.md      # Especificación: CameraX + ML Kit Face Detection
    │   │   ├── iteracion 3.md      # Especificación: Navegación Jev + Pinhole 3D + USB-OTG
    │   │   ├── iteracion 4.md      # Especificación: Bounding Box HUD en Voz y Visión
    │   │   ├── iteracion 5.md      # Especificación: Estabilización de Obstáculos + Arduino V2
    │   │   └── iteracion 6.md      # Especificación: Optimización Hardware AVD, Build APK y Manual Celular
    │   │
    │   ├── android/                # CÓDIGO FUENTE DE LA APLICACIÓN NATIVA ANDROID (Kotlin)
    │   │   ├── README.md           # Manual técnico de compilación, arquitectura y configuración
    │   │   └── app/src/main/       # Código Kotlin, Jetpack Compose y AndroidManifest
    │   │
    │   ├── web/                    # (Prototipo anterior) Frontend en Angular 18
    │   └── server/                 # (Prototipo anterior) Backend proxy en Node.js
    │
    └── Movimiento Sara/            # Módulo de Actuación y Chasis Robótico
        ├── README.md               # Instrucciones de conexión de hardware y pines
        ├── prueba_sara.ino         # Sketch de Arduino V1 (L298N + Ultrasonido + Servomotor)
        └── prueba_sara_v2.ino      # Sketch de Arduino V2 (Fran - Comandos 1-6 a 115200 bps)
```

---

## ⚙️ Guía Rápida de Puesta en Marcha

### 1. Cerebro Superior (App Android en el Smartphone)
1. Abrir Android Studio (Ladybug o superior) y cargar la carpeta `Fase 2/Interfaz Usuario SARA/android`.
2. Crear un archivo `.env` en la raíz de `android/` con tu clave de Gemini:
   ```env
   GEMINI_API_KEY=tu_clave_de_google_ai_studio
   ```
3. Conectar el teléfono físico mediante depuración USB y compilar el proyecto:
   ```bash
   ./gradlew installDebug
   ```
4. Otorgar los permisos solicitados en pantalla (Cámara, Micrófono y acceso a Dispositivos USB).

### 2. Base Motriz (Arduino en el Chasis)
1. Abrir `Fase 2/Movimiento Sara/prueba_sara.ino` en el **IDE de Arduino**.
2. Conectar la placa **Arduino Uno** vía USB y seleccionar el puerto COM correspondiente.
3. Cargar el programa a la placa.
4. Conectar la placa Arduino al puerto USB del smartphone utilizando un **adaptador USB-OTG**.

---

## 🛡️ Seguridad y Buenas Prácticas
* **Protección de Credenciales:** El archivo `.env` y los artefactos generados en `build/` se encuentran estrictamente protegidos en `.gitignore` para evitar filtraciones accidentales de credenciales.
* **Seguridad Mecánica:** El sistema incluye salvaguardas por software (`SafetySupervisor`) y por hardware (ultrasonido frontal directo en Arduino) que detienen inmediatamente los motores ante cualquier anomalía o pérdida de señal serial.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
