# Módulo: Interfaz de Usuario y Cerebro del Robot SARA

> **Evolución del Módulo:** De prototipo web distribuido (Fase 1/2 inicial) a **Aplicación Autónoma On-Device en Android Nativo (Kotlin)**.

---

## 🧭 Visión del Módulo

El objetivo de este módulo es dotar al robot SARA de una interfaz empática, intuitiva y robusta para adultos mayores, integrando capacidades de visión artificial, conversación por inteligencia artificial y control cinemático.

El proyecto ha pasado por dos grandes etapas arquitectónicas:
1. **Etapa 1 (Prototipo Web Distribuido):** Implementado con Angular 18, Node.js proxy, Whisper local y Google GenAI SDK.
2. **Etapa 2 (Arquitectura Nativa Android):** Migración 100% on-device a Kotlin y Jetpack Compose, integrando reconocimiento nativo, streaming con Gemini 2.5 Flash, CameraX + ML Kit y comunicación serial USB-OTG con Arduino.

---

## 📂 Organización de Carpetas

```text
Interfaz Usuario SARA/
├── android/            # ⭐ CÓDIGO PRINCIPAL Y ACTIVO: Aplicación nativa en Kotlin
│   ├── app/src/main/   # Código fuente (MVVM, Jetpack Compose, CameraX, ML Kit, Serial)
│   ├── README.md       # Manual de compilación y arquitectura Android
│   └── build.gradle.kts# Configuración de dependencias y compilación
│
├── iteraciones/        # Especificaciones técnicas detalladas de desarrollo
│   ├── iteracion 1.md  # Streaming SSE, Sentence Chunking y Barge-In (Interrupción)
│   ├── iteracion 2.md  # Visión artificial con CameraX y Google ML Kit Face Detection
│   ├── iteracion 3.md  # Navegación reactiva Jev, modelo Pinhole 3D y enlace USB-OTG
│   ├── iteracion 4.md  # Detección Facial HUD y Bounding Box en Tiempo Real (Voz y Visión)
│   └── iteracion 5.md  # Estabilización de Obstáculos (Anti-Starvation) y Arduino V2
│
├── PROGRESO.md         # Registro cronológico del estado de cada iteración y módulo
├── Sprint 1.md         # Guía de portabilidad inicial de Angular/Node a Android Kotlin
│
├── web/                # (Respaldo) Prototipo previo en Angular 18
├── server/             # (Respaldo) Servidor proxy previo en Node.js Express
└── docs/               # Decisiones de diseño y actas técnicas históricas
```

---

## ⚡ Stack Tecnológico Actual (Android Nativo)

| Área | Tecnología / Librería | Función Principal |
| :--- | :--- | :--- |
| **Lenguaje** | Kotlin 2.0 | Lenguaje principal con corrutinas y Flow reactivo |
| **Interfaz (UI)** | Jetpack Compose + Material 3 | Interfaz declarativa, adaptable y accesible para adultos mayores |
| **Rostro Animado** | Compose Canvas SVG procedural | Animación a 60 FPS con parpadeo, emociones y sincronización labial |
| **Visión** | CameraX + Google ML Kit Face Detection | Detección facial monocular a 320x320 @ 10 FPS |
| **Estimación 3D** | Modelo Pinhole Monocular | Cálculo de profundidad métrica $Z$ y ángulo horizontal $\theta$ |
| **Navegación** | Modelo Jev (Campos Potenciales) | Evitación de obstáculos y seguimiento de usuarios en tiempo real |
| **Seguridad** | `SafetySupervisor` | Parada de emergencia instantánea (< 0.40 m) |
| **IA Conversacional** | Google Gemini 2.5 Flash | Inferencia rápida en lenguaje natural vía streaming SSE |
| **Voz (STT & TTS)** | `android.speech.SpeechRecognizer` + TTS | Reconocimiento continuo en español y verbalización fluida |
| **Barge-In** | Concurrencia de Corrutinas | Cancelación de respuesta de IA cuando el usuario interrumpe |
| **Comunicación Serial** | `usb-serial-for-android` | Protocolo serie 115200 bps a 10 Hz por USB-OTG hacia Arduino |

---

## 🚀 Cómo Empezar con la Versión Actual

Para trabajar con la versión oficial y activa del robot Sara:
1. Dirígete a la carpeta `android/`.
2. Sigue las instrucciones del manual en [android/README.md](android/README.md).
3. Asegúrate de configurar tu archivo `.env` con la variable `GEMINI_API_KEY`.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
