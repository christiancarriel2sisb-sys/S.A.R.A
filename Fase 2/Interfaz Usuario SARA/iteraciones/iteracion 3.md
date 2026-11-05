# Arquitectura de Detección de Objetos y Navegación On-Device (Gama Baja)

**Proyecto:** Robot Sara  
**Módulo:** Percepción y Navegación Reactiva en Interiores (Entorno Hospitalario)  
**Entorno de Ejecución:** Android Nativo (Kotlin) sobre Smartphone de Gama Baja  
**Principio de Diseño:** 100% On-Device, cero latencia de red, presupuesto estricto de CPU/RAM y estimación espacial monocular.

---

## 1. Visión General del Sistema

Para operar en un teléfono de recursos limitados sin sobrecalentamiento ni pérdida de cuadros (*frame drops*), el sistema desacopla la captura, la inferencia visual, la toma de decisiones probabilísticas y la emisión motriz mediante un esquema de colas asíncronas no bloqueantes.

```
┌────────────────────────────────────────────────────────┐
│                   CÁMARA DEL SMARTPHONE                │
└───────────────────────────┬────────────────────────────┘
                            │ (Frames a 320x320 @ 8-10 FPS)
                            ▼
┌────────────────────────────────────────────────────────┐
│ 1. CAPA DE CAPTURA & PREPROCESAMIENTO (CameraX)       │
│    - Backpressure: DROP_LATEST (evita cola de frames)  │
│    - Conversión directa YUV -> TensorBuffer            │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 2. MOTOR DE DETECCIÓN VISUAL (YOLOv8n / TFLite INT8)   │
│    - Delegado NNAPI / GPU (si disponible) o CPU x4     │
│    - Clases hospitalarias: persona, cama, silla, pared │
│    - Bounding Boxes 2D + Confianza (%)                 │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 3. PROYECCIÓN ESPACIAL 3D MONOCULAR                    │
│    - Estimación de distancia Z mediante óptica pinhole │
│    - Mapeo de coordenadas relativas (X_rel, Z_dist)    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 4. MÓDULO DE DECISIÓN PROBABILÍSTICA (Modelo Jev)      │
│    - Entrada: Vector estructurado de estados + dist    │
│    - Salida: Distribución de probabilidades de acción  │
│      [AVANZAR: 0.15, DESVIAR_IZQ: 0.75, FRENAR: 0.10]  │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 5. CONTROLADOR MOTRIZ & SERIAL DISPATCHER             │
│    - Comandos compactos vía USB-OTG a microcontrolador │
│    - Frecuencia de envío: 10 Hz (ej: "CMD:NAV,-25,12\n")│
└────────────────────────────────────────────────────────┘
```

---

## 2. Especificación por Capas

### Capa 1: Captura y Preprocesamiento Ligero
* **Librería:** `androidx.camera:camera-camera2` + `ImageAnalysis`.
* **Estrategia de Optimización:**
  * **Resolución reducida:** Configurar `TargetResolution` en $320 \times 320$ o $416 \times 416$ píxeles. Evitar resoluciones HD o 4K que saturan el ancho de banda del bus de memoria.
  * **Control de framerate:** Limitar la inferencia a $8 - 10\text{ FPS}$. En un robot móvil doméstico/hospitalario que avanza a velocidades bajas ($0.2 - 0.5\text{ m/s}$), 8 evaluaciones por segundo son más que suficientes para esquivar obstáculos.
  * **Política de descarte:** `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`. Si el modelo está ocupado analizando un frame, los fotogramas intermedios se descartan de inmediato sin acumular latencia.

### Capa 2: Motor de Inferencia Visual (YOLO TFLite Cuantizado)
* **Modelo base:** YOLOv8 Nano (`yolov8n`) exportado a TensorFlow Lite.
* **Técnica de compresión:** Cuantización post-entrenamiento a **INT8** (Full Integer Quantization).
  * Reduce el peso del binario de ~12 MB a **~3.2 MB**.
  * Reduce el consumo de memoria RAM de pico a menos de 45 MB.
  * Permite ejecutar operaciones aritméticas enteras vectorizadas en los núcleos Cortex-A pequeños del CPU sin requerir punto flotante intensivo.
* **Delegados de aceleración:**
  1. Intentar instanciar `NnApiDelegate` (Aceleración por chip de inferencia de Android).
  2. Si falla o genera inestabilidad térmica, caer a `CpuDelegate` fijado en 2 hilos (`numThreads = 2`).

### Capa 3: Estimación Espacial Monocular (Proyección 3D)
Dado que un teléfono estándar no cuenta con sensor LIDAR ni cámara de profundidad (RGB-D), la posición 3D relativa se infiere mediante el modelo de cámara estenopeica (*Pinhole Camera Model*), usando la altura física conocida de los objetos en un hospital:

$$\text{Distancia } Z \approx \frac{H_{\text{real}} \times f_y}{h_{\text{bbox}}}$$

Donde:
* $H_{\text{real}}$: Altura promedio estimada del objeto (ej: Persona = $1.70\text{ m}$, Silla de ruedas = $0.90\text{ m}$).
* $f_y$: Longitud focal vertical en píxeles de la cámara del teléfono.
* $h_{\text{bbox}}$: Altura de la caja delimitadora detectada en píxeles.
* $X_{\text{rel}}$: Desplazamiento horizontal relativo respecto al centro del lente:
  $$X_{\text{rel}} \approx \frac{(X_{\text{centro}} - X_{\text{medio}}) \times Z}{f_x}$$

### Capa 4: Módulo de Razonamiento y Decisión (Modelo Jev)
* **Propósito:** No genera texto ni utiliza memoria secuencial pesada; toma una tupla de valores numéricos normalizados y escupe una matriz de probabilidades de navegación.
* **Vector de Entrada ($D_{\text{in}}$):**
  * Sector Izquierdo ($Z_{\text{min\_izq}}$): Distancia mínima a obstáculo en la izquierda $[0.0 - 1.0]$.
  * Sector Central ($Z_{\text{min\_cen}}$): Distancia mínima frontal $[0.0 - 1.0]$.
  * Sector Derecho ($Z_{\text{min\_der}}$): Distancia mínima a la derecha $[0.0 - 1.0]$.
  * Estado de trayectoria previa: Dirección de giro en el paso anterior $[-1.0, 0.0, 1.0]$.
* **Salida del Modelo ($P_{\text{out}}$):**
  * $P(\text{AVANZAR})$
  * $P(\text{GIRAR\_IZQ})$
  * $P(\text{GIRAR\_DER})$
  * $P(\text{DETENERSE})$
* **Filtro de Seguridad Determinista:** Si $Z_{\text{min\_cen}} < 0.40\text{ m}$, el sistema aplica un corte de emergencia directo (`OVERRIDE_STOP`), ignorando la probabilidad del modelo para salvaguardar la integridad física del robot.

### Capa 5: Despacho a Hardware (Capa de Actuación)
* **Canal:** `usb-serial-for-android` vía USB-OTG al microcontrolador a 115200 bps.
* **Formato de trama de navegación:**
  ```text
  NAV:<angulo_giro_grados>,<velocidad_pwm>\n
  ```
  * Ejemplo avance despejado: `NAV:0,120\n`
  * Ejemplo corrección evasiva: `NAV:-35,80\n`
  * Ejemplo frenado: `NAV:0,0\n`

---

## 3. Presupuesto de Recursos para Teléfono de Gama Baja

| Métrica / Recurso | Límite Máximo Asignado | Estrategia de Control |
| :--- | :--- | :--- |
| **Tiempo de Inferencia Visual** | $\le 75\text{ ms}$ | YOLOv8n INT8 a 320x320 con backend NNAPI/CPU. |
| **Tiempo de Decisión (Jev)** | $\le 5\text{ ms}$ | Multiplicación de matrices compacta en memoria local. |
| **Latencia Total Pipeline** | $\le 100\text{ ms}$ | Comunicación reactiva directa sin pasos por disco. |
| **Uso de Memoria RAM** | $\le 180\text{ MB}$ | Reciclaje de buffers `ByteBuffer` sin invocar al Garbage Collector en cada frame. |
| **Consumo de CPU** | $\le 40\%$ en promedio | Limitación a 8 FPS y 2 hilos dedicados para evitar throttling térmico. |

---

## 4. Estructura de Paquetes en Kotlin

```text
com.sara.robot.navigation/
│
├── perception/
│   ├── CameraManager.kt          // Manejo del ciclo de vida y streaming CameraX
│   ├── YoloDetector.kt           // Wrapper TFLite INT8 con delegados de aceleración
│   └── SpatialProjector.kt       // Estimación matemática de distancias 3D (X, Z)
│
├── reasoning/
│   ├── JevDecisionEngine.kt      // Inferencia rápida probabilística del clasificador Jev
│   ├── SafetySupervisor.kt       // Regla estricta de frenado por umbral crítico (<40cm)
│   └── NavigationState.kt        // Data class con vectores de estados y probabilidades
│
└── actuator/
    ├── SerialDispatcher.kt       // Salida no bloqueante hacia el microcontrolador
    └── MotorCommandFormatter.kt  // Generación de tramas de texto seguras
```

---

## 5. Estrategia de Pruebas sin Hardware Físico en PC

Para validar el pipeline completo en Android Studio antes de subirlo al robot:

1. **Inyección de Video Hospitalario en el Emulador:**
   * Utilizar secuencias MP4 pregrabadas de pasillos de hospital (con camillas, personas caminando y puertas).
   * Inyectar el video como fuente de la cámara virtual de Android mediante `v4l2loopback` en Linux o configurando la entrada de video del emulador.
2. **Validación de la Proyección Espacial:**
   * Colocar objetos a distancias medidas ($1\text{ m}, 2\text{ m}, 3\text{ m}$) frente a la cámara web de la PC y contrastar contra el cálculo de $Z$ arrojado por el `SpatialProjector`.
3. **Telemetría en Logcat:**
   * Imprimir la matriz de decisiones cada 100 ms para constatar que el modelo Jev aumente la probabilidad de evasión a medida que el valor de $Z$ disminuye.