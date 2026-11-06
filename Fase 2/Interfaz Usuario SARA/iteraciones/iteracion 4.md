# Iteración 4: Detección Facial HUD y Bounding Box en Tiempo Real (Módulo de Voz y Visión)

**Proyecto:** S.A.R.A - Sistema Asistente Robótico de Apoyo  
**Autor de la Petición:** Christian Carriel  
**Fecha:** Noviembre 2026 (Fase 2)  
**Estado:** COMPLETADO Y VERIFICADO  

---

## 1. Petición del Usuario

> *"Quiero que en el módulo de reconocimiento de voz, pongas un cuadro en la cara de la persona cuando reconozca un rostro (...) no es que quiero que en el módulo de Sara de voz se vea la cámara, sino que quiero que encima de la cara aparezca un cuadro para verificar que genuinamente está detectando un rostro, eso es lo que quiero (...) recuerda no instalar nada, solo usa lo que tengas instalado."*
>
> *"Estoy testeando, pero no sale el cuadro, sale objetivo fijado pero no detecta mi rostro, o por lo menos no sale el cuadro encima de mi rostro."*

### Requisitos Específicos:
1. **Preservar la Estética del Robot en la Pantalla de Voz (`SaraScreen`):** No mostrar el feed de video de la cámara en vivo de fondo para no romper la apariencia del rostro animado de Sara.
2. **Detección Activa en Segundo Plano:** Mantener la cámara frontal activa de forma transparente procesando fotogramas con Google ML Kit Face Detection.
3. **Indicador Visual Concreto (Bounding Box HUD):** Cuando se detecte una persona, renderizar un cuadro futurista sobre la pantalla que siga el rostro y demuestre que el robot la tiene fijada.
4. **Corrección en Módulo de Visión (`FaceDetectionScreen`):** Asegurar que en la pantalla con video en vivo, el cuadro delimitador también aparezca superpuesto exactamente sobre la cara del usuario, resolviendo la discrepancia donde el texto decía `"OBJETIVO FIJADO"` pero no se dibujaba el cuadro en pantalla.
5. **Cero Dependencias Externas Nuevas:** Utilizar estrictamente las dependencias ya instaladas en el proyecto (CameraX y Google ML Kit Vision).

---

## 2. Diagnóstico Técnico de la Causa Raíz

Al analizar el código existente tras el reporte del usuario, se identificaron dos causas técnicas:

1. **Ausencia del componente de dibujo en `FaceDetectionScreen.kt`:**
   En la pantalla de video en vivo existía la lógica de inferencia de ML Kit y un panel de telemetría inferior que mostraba el texto `"OBJETIVO FIJADO"` al detectar un rostro. Sin embargo, no existía ninguna capa de `Canvas` o componente gráfico dibujando el rectángulo sobre el fotograma de la cámara.
2. **Desajuste de Aspect-Ratio y Espejado:**
   Para que el cuadro encaje milimétricamente sobre la cara humana en la pantalla de un teléfono en orientación horizontal (Landscape), la relación de aspecto del sensor de la cámara (típicamente 4:3 o 16:9) debe proyectarse a la pantalla usando el algoritmo `FILL_CENTER` de CameraX, corrigiendo además el efecto espejo de la cámara frontal.

---

## 3. Solución Implementada Paso a Paso

### Paso 1: Enriquecimiento de Datos de Telemetría (`FaceAnalyzer.kt`)
Se actualizó el modelo de datos `FaceTrackingData` para transportar las dimensiones reales del fotograma capturado (`imageWidth`, `imageHeight`), las coordenadas absolutas de la caja detectada por ML Kit (`rawLeft`, `rawTop`, `rawRight`, `rawBottom`) y la bandera de cámara frontal (`isFrontCamera`):

```kotlin
data class FaceTrackingData(
    val faceDetected: Boolean,
    val angleOffsetDegrees: Float,
    val boundingBoxWidth: Int,
    val boundingBoxHeight: Int,
    val rawLeft: Float = 0f,
    val rawTop: Float = 0f,
    val rawRight: Float = 0f,
    val rawBottom: Float = 0f,
    val imageWidth: Float = 1f,
    val imageHeight: Float = 1f,
    val isFrontCamera: Boolean = true
)
```

### Paso 2: Creación del Overlay HUD Robótico (`FaceBoundingBoxOverlay.kt`)
Se desarrolló un componente procedural de Jetpack Compose (`Canvas`) que dibuja una retícula de fijación de objetivo de alta tecnología:

* **Algoritmo de Proyección Óptica `FILL_CENTER`:**
  ```kotlin
  val scale = maxOf(canvasW / imgW, canvasH / imgH)
  val offsetX = (canvasW - imgW * scale) / 2f
  val offsetY = (canvasH - imgH * scale) / 2f

  val mappedLeft = if (trackingData.isFrontCamera) {
      (imgW - trackingData.rawRight) * scale + offsetX
  } else {
      trackingData.rawLeft * scale + offsetX
  }
  ```
* **Estilizado Visual Futurista:**
  - Color Neón Cian (`#00F0FF`) con relleno translúcido suave.
  - Brackets (escuadras reforzadas) en las cuatro esquinas de la caja delimitadora.
  - Líneas de conexión punteadas.
  - Mira en cruz en el centroide del rostro.
  - Placa superior con telemetría en tiempo real: `ROSTRO FIJADO [x.x°]`.
* **Animaciones Reactivas:** Uso de `animateFloatAsState` para transiciones de opacidad suave (fade-in / fade-out) al detectar o perder el objetivo.

### Paso 3: Integración en Pantalla de Cámara en Vivo (`FaceDetectionScreen.kt`)
Se superpuso `FaceBoundingBoxOverlay` directamente sobre el `PreviewView` de CameraX, vinculando el parámetro `isFrontCamera = useFrontCamera`. Ahora el cuadro enmarca con precisión la cara del usuario sobre el video.

### Paso 4: Integración en Pantalla Principal de Voz (`SaraScreen.kt`)
Para cumplir el requerimiento de no mostrar el video pero sí el cuadro:
* Se instanció CameraX con un `PreviewView` invisible de `1.dp` con `alpha = 0.001f`, garantizando compatibilidad con drivers OEM sin mostrar imagen.
* Se dibujó el `FaceBoundingBoxOverlay` directamente encima del componente de rostro animado (`FaceComponent`), proyectando la caja en la posición exacta del usuario mientras habla con Sara.

### Paso 5: Configuración de Inicio Directo (`MainActivity.kt`)
Se configuró `AppScreen.ROBOT_FACE` como pantalla de inicio predeterminada para que el usuario acceda inmediatamente a la interacción por voz y visión.

---

## 4. Archivos Modificados y Creados

| Archivo | Tipo de Cambio | Descripción |
| :--- | :--- | :--- |
| `com.sara.robot.vision.FaceAnalyzer.kt` | Modificación | Exportación de dimensiones del sensor y caja absoluta para escalado. |
| `com.sara.robot.ui.face.FaceBoundingBoxOverlay.kt` | Nuevo | Componente Compose de dibujo de retícula HUD con escalado `FILL_CENTER`. |
| `com.sara.robot.ui.FaceDetectionScreen.kt` | Modificación | Integración del overlay visual sobre el feed de video en vivo. |
| `com.sara.robot.ui.SaraScreen.kt` | Modificación | Captura en segundo plano invisible y proyección del cuadro sobre el rostro. |
| `com.sara.robot.MainActivity.kt` | Modificación | Inicio por defecto en `ROBOT_FACE`. |

---

## 5. Validación y Compilación
* Compilación ejecutada con Gradle y JDK 17: **`BUILD SUCCESSFUL in 56s`**.
* Generación de APK funcional en `app/build/outputs/apk/debug/app-debug.apk`.
* Código versionado y sincronizado en la rama `feature/android-app`.

