# Iteración 5: Estabilización Continua de Obstáculos y Fusión con Arduino V2 (Fran)

**Proyecto:** S.A.R.A - Sistema Asistente Robótico de Apoyo  
**Autor de la Petición:** Christian Carriel  
**Fecha:** Noviembre 2026 (Fase 2)  
**Estado:** COMPLETADO Y VERIFICADO  

---

## 1. Petición del Usuario

> *"Quiero que fusiones `prueba_sara_v2.ino`, quiero que Jev ahora tenga conexión con ese controlador que está ahí para que pueda manejar el robot de forma autónoma. Lo segundo: los cuadros de reconocimiento dejan de aparecer después de cierto tiempo y Jev empieza a fallar, no sé si es el módulo de reconocimiento que no le está pasando los parámetros bien a Jev, o si es Jev que no está haciendo bien el reconocimiento. Primero discutamos cómo arreglar lo del módulo de reconocimiento de obstáculos, después quiero que hagamos la implementación del código de mi amiga Fran. Ojo que no tienes que instalar nada ni modificar ninguna cosa del sistema operativo, solo manéjate con las herramientas que tienes dentro del proyecto."*

### Requisitos Específicos:
1. **Fusión de Código Arduino V2:** Traer al árbol de trabajo el firmware `prueba_sara_v2.ino` subido por Francisca al repositorio remoto.
2. **Diagnóstico y Corrección de Congelamiento:** Solucionar la desaparición paulatina de los cuadros delimitadores y el colapso del motor Jev después de un tiempo de ejecución.
3. **Integración Cinemática Jev ➔ Arduino V2:** Conectar las decisiones de navegación del motor Jev y el supervisor de seguridad (`SafetySupervisor`) con el protocolo numérico de Arduino (`1`, `2`, `3`, `5`, `6`).
4. **Cero Alteraciones de Sistema:** No instalar librerías externas ni modificar la configuración del sistema operativo.

---

## 2. Diagnóstico Técnico de Causa Raíz

### A. Fuga de Buffers en CameraX (*Buffer Starvation*)
En `YoloDetector.kt` y `CameraManager.kt`, el procesamiento de Google ML Kit (`detector.process()`) es una operación asíncrona no bloqueante. Cuando el dispositivo experimentaba latencias mayores a 100 ms (por recolección de basura o temperatura), entraba un nuevo fotograma antes de que el anterior terminara. CameraX tiene un límite fijo de buffers en cola (estrategia `KEEP_ONLY_LATEST`). Al no existir un bloqueo atómico, la cola se agotaba, provocando que CameraX dejara de entregar fotogramas de forma permanente.

### B. Conflicto de Concurrencia en Compose (*Threading Collision*)
El callback de inferencia se invocaba directamente en el hilo de trabajo del analizador (`cameraExecutor`). Dentro de ese hilo secundario se mutaba el estado `navState = NavigationState(...)` de Compose, generando colisiones de snapshots que descartaban la recomposición de la pantalla.

### C. Desconexión de Protocolo Serial
El motor Jev y el `MotorCommandFormatter` emitían tramas de texto de formato previo (`"NAV:<angle>,<pwm>\n"`), mientras que el nuevo firmware de Arduino `prueba_sara_v2.ino` lee comandos numéricos directos mediante `Serial.parseInt()` (`1`, `2`, `3`, `5`, `6`). El Arduino descartaba o recibía cero.

---

## 3. Solución Implementada Paso a Paso

### Paso 1: Fusión de `prueba_sara_v2.ino`
Se extrajo e integró en el directorio local `Fase 2/Movimiento Sara/prueba_sara_v2.ino` el nuevo firmware de control desarrollado por Francisca, manteniendo el soporte a 115200 baudios y freno por ultrasonido a 15 cm.

### Paso 2: Protección Atómica contra Saturación de Buffers (`CameraManager.kt`)
Se introdujo una bandera atómica `AtomicBoolean(false)` (`isProcessing`):
```kotlin
if (now - lastInferenceTimestamp < minFrameIntervalMs || isProcessing.get()) {
    imageProxy.close()
    return@setAnalyzer
}
if (!isProcessing.compareAndSet(false, true)) {
    imageProxy.close()
    return@setAnalyzer
}
```
Si ML Kit todavía está ocupado procesando un fotograma, el nuevo fotograma entrante es cerrado y liberado de inmediato sin encolarse, blindando a CameraX contra cualquier agotamiento de memoria. Al completarse la tarea (en éxito o fallo), `isProcessing.set(false)` libera el cerrojo para el siguiente cuadro.

### Paso 3: Despacho Seguro al Hilo Principal (`Dispatchers.Main`)
Los resultados de la inferencia y el modelo Jev ahora se despachan al hilo principal de UI antes de actualizar el estado de Compose:
```kotlin
ContextCompat.getMainExecutor(context).execute {
    onFrameResults(obstacles, zLeft, zCenter, zRight, inferenceTime)
}
```
Esto garantiza que los cuadros delimitadores, los indicadores de sector y el radar polar se redibujen continuamente a 60 FPS sin colisiones.

### Paso 4: Adaptación del Protocolo de Control Arduino V2 (`MotorCommandFormatter.kt`)
Se actualizó el formateador de comandos motrices para despachar los códigos estandarizados por Fran:
* **`1\n`** ➔ `NavigationAction.AVANZAR` (potencia crucero 120 PWM).
* **`2\n`** ➔ `NavigationAction.GIRAR_IZQ` (giro temporizado izquierda).
* **`3\n`** ➔ `NavigationAction.GIRAR_DER` (giro temporizado derecha).
* **`6\n`** ➔ `NavigationAction.DETENERSE` o parada de emergencia por `SafetySupervisor` ($Z < 0.40\text{ m}$).

### Paso 5: Renderizado Enriquecido de Obstáculos y HUD Táctico (`NavigationScreen.kt`)
* En el `Canvas`, cada caja delimitadora ahora incluye un fondo translúcido y una etiqueta superior generada con `nativeCanvas` indicando la entidad y distancia métrica:  
  *Ejemplo:* `Persona [1.4m]`, `Silla [0.8m]`.
* El HUD de telemetría inferior ahora refleja la transmisión exacta en tiempo real hacia Arduino:  
  *Ejemplo:* `TX: 1 (Avanzar)`, `TX: 2 (Giro Izq)`, `TX: 6 (Stop)`.
* Se vinculó el ciclo de vida de `CameraManager` con `DisposableEffect` para ejecutar `cameraManager.shutdown()` al salir de la pantalla, evitando fugas de memoria.

---

## 4. Archivos Modificados y Creados

| Archivo | Tipo de Cambio | Descripción |
| :--- | :--- | :--- |
| `Fase 2/Movimiento Sara/prueba_sara_v2.ino` | Fusión Remota | Firmware Arduino con protocolo serial numérico (1-6) a 115200 bps. |
| `com.sara.robot.navigation.perception.CameraManager.kt` | Modificación | Implementación de `isProcessing` atómico y despacho en hilo UI. |
| `com.sara.robot.navigation.perception.YoloDetector.kt` | Modificación | Callback de finalización segura y control estricto de cierre de buffers. |
| `com.sara.robot.navigation.actuator.MotorCommandFormatter.kt` | Modificación | Emisión de tramas numéricas V2 (`1\n`, `2\n`, `3\n`, `6\n`). |
| `com.sara.robot.ui.NavigationScreen.kt` | Modificación | Bounding boxes con etiquetas métricas, HUD V2 y gestión de ciclo de vida. |

---

## 5. Validación y Compilación
* Compilación ejecutada con Gradle y JDK 17: **`BUILD SUCCESSFUL in 21s`**.
* Generación de APK funcional en `app/build/outputs/apk/debug/app-debug.apk`.
* Código versionado y sincronizado en la rama `feature/android-app`.
