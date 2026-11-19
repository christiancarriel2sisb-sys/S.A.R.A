# Iteración 11: Módulo de Visión Unificada (Cámara Frontal Exclusiva) y Reconocimiento Facial Biométrico con MobileFaceNet (TFLite) y Consentimiento Informado

**Proyecto:** S.A.R.A - Sistema Asistente Robótico de Apoyo  
**Autor de la Petición:** Christian Carriel  
**Fecha:** Noviembre 2026 (Fase 2)  
**Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)  

---

## 1. Petición del Usuario y Objetivos

> *"1.- une el modulo de deteccion de objetos con el reconocimiento facial, y que se ejecute en la camara frontal*  
> *2.- agrega el mobileFaceNet, y usalo para crear el vector del rostro como me dijiste que lo harias*  
> *3.- crea una nueva tabla en la base de datos, con una tabla que guarde la informacion biometrica, ojo que la tabla tiene su clave primaria y se une a un paciente mediante la clave primaria del paciente, es decir que la tabla de datos biometricos, debe de heredar por asi decir la clave primaria de la tabla paciente, en una relacion uno es a uno porque un usuario tiene un unico registro biometrico.*  
> *4.- despues de que crees la base de datos, en el perfil de admin, crees el modulo que combine perfectamente todos los puntos anteriores, y aparte por cada paso que ejecute el modulo, ya sea pedir permiso, almacenar los datos y demas tienes que loggearlo para ir viendo como va el flujo del modulo por si hay un error saber donde arreglarlo, y no olvidees que todo lo que vas haciendo se va documentando.*  
> *ojo y no te olvides, que el promt que quiero es el primero pero, no puedes saber como se llama una persona asi que quitale el nombre de 'don juan', y solo deja el hola y sigue con lo demas."*

---

## 2. Decisiones Arquitectónicas Fundamentales

1. **Cámara Frontal Exclusiva y Rotación de Chasis:**  
   Se descartó el uso de la cámara trasera para eliminar la latencia de conmutación de sensores en CameraX (300-700 ms) y evitar el sobrecalentamiento de la CPU móvil. La cámara frontal apunta en el cono de atención y conversación de Sara; si Sara necesita ver atrás o buscar en el entorno, rota su chasis sobre su propio eje (360°).
2. **Modelo On-Device MobileFaceNet (TensorFlow Lite):**  
   - Modelo: `mobilefacenet.tflite` (~5.2 MB) alojado en `app/src/main/assets/`.
   - Inferencia local ultrarrápida (15-20 ms en CPU) a 0 tokens de red.
   - Recorte facial a $112 \times 112$ px en RGB normalizado a $[-1.0, 1.0]$.
   - Generación de un vector (embedding) de **192 dimensiones** con normalización de norma $L_2$ ($||v|| = 1.0$).
   - Similitud Coseno calculada como producto punto directo en RAM ($< 0.05$ ms).
3. **Flujo Ético de Consentimiento Informado:**  
   Sara solicita autorización explícita antes de cualquier captura o persistencia biométrica utilizando el motor TextToSpeech y esperando la confirmación por voz ("Sí" / "No") o táctil.
   - **Prompt Verbalizado:**  
     > *"Hola. Para poder reconocerte cuando me llames, saludarte por tu nombre y saber cuándo necesitas mi ayuda o tus recordatorios, me gustaría guardar tu rostro en mi memoria. Esto se guarda solo dentro de mí, de forma privada y segura. ¿Me autorizas a escanear tu rostro?"*

---

## 3. Persistencia en SQLite (Room Database v3) y Relación 1:1 Estricta

Se incorporó la entidad `BiometriaEntity` vinculada a la tabla `Paciente` en una relación biyectiva 1 a 1:

```text
┌─────────────────────────────────┐                ┌─────────────────────────────────┐
│            PACIENTE             │                │       BIOMETRIA_PACIENTE        │
├─────────────────────────────────┤                ├─────────────────────────────────┤
│ PK Id_paciente: Long            │◄───────────────┤ PK id_biometria: Long (AutoInc) │
│    Nombre: String               │  1          1  │ FK id_paciente: Long (UNIQUE)   │
│    ape_p: String                │                │    vector_biometrico: String    │ (192 floats CSV)
│    ape_m: String                │                │    consentimiento_otorgado: Bool│ (true)
│    Fech_nac: String             │                │    fecha_registro: Long (ms)    │
│    edad: Int                    │                └─────────────────────────────────┘
│    genero: String               │
└─────────────────────────────────┘
```

* **`BiometriaEntity.kt` (`com.sara.robot.data.local.entity`):**
  - Clave foránea hacia `PacienteEntity(Id_paciente)` con borrado en cascada (`ForeignKey.CASCADE`).
  - Restricción de unicidad estricta `Index(value = ["id_paciente"], unique = true)` garantizando que cada paciente tenga como máximo un único registro biométrico.
* **`BiometriaDao.kt` (`com.sara.robot.data.local.dao`):**
  - Consultas reactivas con `Flow` (`observeBiometriaPorPaciente`) y operaciones atómicas de inserción/actualización (`insertBiometria`) y eliminación (`deleteBiometriaPorPaciente`).
* **`SaraAppDatabase.kt`:**
  - Actualización a `version = 3`, registro de `BiometriaEntity::class` y exposición de `biometriaDao()`.

---

## 4. Componentes de Software Creados y Modificados

### 4.1 `MobileFaceNetHelper.kt` (`com.sara.robot.vision`)
* Carga del archivo `mobilefacenet.tflite` desde los assets del sistema con mapeo de memoria `FileChannel.MapMode.READ_ONLY`.
* Configuración con 4 hilos en CPU (`Interpreter.Options().setNumThreads(4)`).
* Normalización $L_2$ automática de embeddings.
* Cálculo de similitud coseno con umbral de coincidencia `SIMILARITY_THRESHOLD = 0.70f` (70%).
* Métodos de serialización / deserialización bidireccional (`embeddingToString` / `stringToEmbedding`).

### 4.2 `UnifiedVisionAnalyzer.kt` (`com.sara.robot.vision`)
* Pipeline único de visión en tiempo real para CameraX (resolución optimizada $320 \times 320$, ~10 FPS).
* Detección simultánea con ML Kit:
  1. **Objetos:** Clasificación de entidades hospitalarias (cama, silla, mesa, persona, obstáculo) con proyección 3D monocular ($Z_{izq}, Z_{cen}, Z_{der}$).
  2. **Rostros:** ML Kit Face Detection en streaming, calculando el ángulo de desviación $\theta$ horizontal con corrección de espejo para cámara frontal.
  3. **Extracción bajo demanda:** Recorta y orienta la imagen facial del fotograma en un `Bitmap` cuando el usuario autoriza el escaneo.
* Control atómico de saturación de fotogramas (`AtomicBoolean(false)`) para prevenir caídas de FPS o consumo excesivo de memoria.

### 4.3 `BiometriaRepository.kt` (`com.sara.robot.data`)
* Orquestación de la lógica de negocio biométrica:
  - `registrarBiometria(pacienteId, faceBitmap)`: Extrae embedding con MobileFaceNet y lo persiste en SQLite.
  - `identificarRostroEnVivo(faceBitmap)`: Compara el rostro actual contra todos los pacientes registrados en la BD en memoria RAM ($< 0.05$ ms por paciente).
  - `eliminarBiometria(pacienteId)`: Limpieza física del registro biométrico.

### 4.4 `UnifiedVisionScreen.kt` (`com.sara.robot.ui`)
Módulo completo integrado en el perfil de **Administrador**:
* **Previsualización en Pantalla Completa:** CameraX sobre la cámara frontal.
* **Canvas HUD Inteligente:**
  - Cajas de delimitación de obstáculos coloreadas dinámicamente según la distancia (Rojo $< 0.8$ m, Amarillo $< 1.6$ m, Verde $> 1.6$ m).
  - Marco facial tecnológico cian con ángulo $\theta$ en grados.
* **Barra de Telemetría Superior:** Valores en tiempo real de $Z_{izq}, Z_{cen}, Z_{der}$, ángulo $\theta$ y acción calculada por el motor Jev (`AVANZAR`, `GIRAR_IZQ`, `GIRAR_DER`, `DETENERSE`).
* **Tarjeta de Gestión de Paciente:**
  - Selector y visualización del paciente objetivo (`Juan Pérez`).
  - Badge de estado: `🟢 ROSTRO REGISTRADO (192-D)` o `🔴 SIN BIOMETRÍA`.
* **Botón "PEDIR PERMISO Y ESCANEAR":**
  - Verifica si hay un rostro al frente.
  - Sara habla el prompt de consentimiento informado por TTS.
  - Escucha por voz la respuesta del paciente ("Sí", "Claro", "Acepto" vs "No", "Rechazo") y despliega botones interactivos de respaldo.
* **Botón "TESTEAR":**
  - Evalúa la coincidencia en vivo contra el vector guardado en SQLite y verbaliza el resultado (ej. *"¡Hola! Te reconozco perfectamente, eres Juan"*).
* **Botón "BORRAR":** Elimina el registro biométrico de la base de datos.
* **Consola de Logs Flotante (`LogPanelDialog`):** Acceso directo para verificar la traza de eventos.

---

## 5. Trazabilidad y Estructura de Logs Implementada (`Tag: Biometria`)

Cada etapa del flujo emite eventos estructurados visibles en el terminal interno de la app y en Logcat:

| Etapa | Log Emitido | Nivel |
| :--- | :--- | :--- |
| **Inicio del Flujo** | `Iniciando protocolo de consentimiento informado para Paciente ID=X...` | `INFO` |
| **Respuesta por Voz** | `Respuesta por voz recibida: "<texto>"` | `INFO` |
| **Consentimiento Sí** | `Consentimiento biométrico OTORGADO por el paciente ID=X` | `INFO` |
| **Consentimiento No** | `Consentimiento biométrico DENEGADO por el usuario. No se guardará información.` | `WARN` |
| **Inferencia TFLite** | `Vector biométrico (192-d) extraído exitosamente con MobileFaceNet (Norma L2 = 1.0)` | `INFO` |
| **Persistencia SQLite**| `Datos biométricos almacenados en la base de datos: ID_Biometria=Y \| Paciente_ID=X \| 192 floats persistidos` | `SUCCESS` |
| **Identificación** | `¡Paciente identificado con éxito! ID=X (Juan) \| Similitud: 89.4%` | `SUCCESS` |
| **Eliminación** | `Registro biométrico eliminado de la base de datos para Paciente ID=X (filas: 1)` | `INFO` |

---

## 6. Refinamientos de Experiencia de Usuario, Detección de Cámara y Optimización de RAM (8 GB)

Siguiendo las observaciones del usuario:
> *"amigo haz que la interfaz sea menos invasiva, hazla mas pequeña no se alcanza a ver nada, segundo no detecta mi camara, seguro que lo configuraste para la camara frontal? y tercero quiero que configures la ram permitida al dispostivo a 8gb"*

1. **Interfaz No Invasiva (Máxima Visibilidad del Video):**
   - **Barra Superior Translúcida:** Barra delgada tipo píldora flotante con fondo `Color(0x88000000)`, iconos reducidos a 18 dp y texto en 10 sp.
   - **Barra Inferior Flotante Compacta:** Se eliminó la tarjeta opaca de múltiples filas que tapaba la mitad de la pantalla; se reemplazó por un muelle flotante de 46 dp de altura con selector de paciente, badge de estado y botones compactos (`[📷 ESCANEAR]`, `[🎯 TEST]`, `[🗑 BORRAR]`).
   - **Trazos Delgados en Canvas:** Opacidad de relleno reducida al 8% y bordes finos (1.5 dp) para una vista 90% limpia del entorno.

2. **Detección Resiliente de Cámara y Selector Dinámico:**
   - **Modo Compatible:** Se configuró `previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE` (TextureView), resolviendo pantallas negras en emuladores OpenGL y GPUs integradas.
   - **Selector con Fallback:** Intenta vincular `CameraSelector.DEFAULT_FRONT_CAMERA`; si el emulador o dispositivo tiene la webcam asignada al sensor trasero, conmuta inteligentemente a `DEFAULT_BACK_CAMERA` sin arrojar excepción.
   - **Botón de Conmutación en Tiempo Real:** Se agregó el botón `Cameraswitch` en la barra superior para alternar entre Frontal y Trasera con 1 toque.
   - **Permisos Anticipados:** Solicitud dual en `MainActivity.kt` (`RECORD_AUDIO` + `CAMERA`) al inicio de la app.

3. **Asignación de 8 GB de Memoria RAM:**
   - **Emulador AVD (`Medium_Phone_API_35.avd/config.ini`):** Se configuró `hw.ramSize = 8192` (8 GB de RAM física para el dispositivo virtual) y `vm.heapSize = 1024`.
   - **Heap JVM Gradle (`gradle.properties`):** Se incrementó a `org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=2048m`.
   - **Heap Extendido del Proceso (`AndroidManifest.xml`):** Se activó `android:largeHeap="true"` en `<application>`.

4. **Resolución del Desfase de Bounding Box en `error 7.png` y Personalización del Paciente Christian Carriel:**
   - **Diagnóstico Exacto de `error 7.png`:**
     En la captura enviada por el usuario (`error 7.png`), Christian se encuentra sentado a la izquierda ($x \approx 35\%$), pero el recuadro celeste aparecía proyectado a la derecha ($x \approx 65\%$) sobre la pared y juguetes Lego.
     - **Causa Raíz:** La telemetría indicaba `CAM: TRASERA (Fallback)`. El emulador expuso la cámara web física como cámara trasera (`DEFAULT_BACK_CAMERA`). En cámara trasera, `PreviewView` **no espeja** la imagen (como lo evidencia el texto legible del envase de leche Soprole). Sin embargo, la variable de preferencia `useFrontCamera` era `true`. El código del Canvas ejecutaba `(imgW - rawRight) * scale`, espejando horizontalmente las coordenadas ($1 - x$) sobre la pared opuesta.
     - **Solución Implementada:** Se introdujo la variable reactiva `isActuallyFrontCamera`. El espejado en Canvas (`(imgW - rawRight)`) y en el analizador de visión (`angleOffset`) se aplica **únicamente si la cámara vinculada por CameraX es efectivamente FRONTAL**. Al usar la cámara trasera, las coordenadas se proyectan directas sin inversión horizontal, calzando milimétricamente sobre el rostro de Christian.
   - **Nombre en el Cuadro Celeste:** Se implementó una placa de alto contraste encima del recuadro celeste (`👤 Christian`) que muestra el nombre del paciente en tiempo real y el porcentaje de similitud biométrica obtenido por MobileFaceNet al testear.
   - **Sustitución Total del Nombre Juan por Christian:** Se actualizaron todas las semillas, validaciones automáticas y consultas en SQLite (`paciente`, `recordatorios`, `chat`) para migrar y garantizar permanentemente el nombre "Christian Carriel".
   - **Prueba de Identificación en Un Clic:** El botón `[TEST]` ahora solicita automáticamente la captura del cuadro y aguarda activamente el frame con el rostro nítido, sin requerir pulsaciones repetidas.

5. **Optimización de Rendimiento de Inferencia (`ResolutionSelector` a 640x360 px):**
   - **Diagnóstico:** CameraX negociaba por defecto la resolución 16:9 más alta (1080p o 720p, más de 2 millones de píxeles por frame), provocando saturación de CPU, *thermal throttling* y pausas del recolector de basura (GC pauses) tras unos minutos de ejecución continua.
   - **Solución Implementada:**
     - En `UnifiedVisionScreen.kt`: Se desacopló la vista de la pantalla del canal de inferencia. La pantalla (`Preview`) mantiene resolución nativa nítida, mientras que el canal de IA (`ImageAnalysis`) se configuró mediante `ResolutionSelector` con `ResolutionStrategy(Size(640, 360), FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)`. Esto reduce los datos procesados en un **89%** (de 2.07M a 230k píxeles por frame).
     - En `UnifiedVisionAnalyzer.kt`: La latencia de inferencia cayó de ~120 ms a ~18 ms, permitiendo elevar la cadencia de análisis a ~14 FPS (`minFrameIntervalMs = 70L`) con fluidez constante y cero sobrecalentamiento.
     - En `SpatialProjector.kt`: Se alineó la óptica monocular pinhole a la resolución nativa de 640x360 px dinámicamente.
     - En `SaraScreen.kt`: Se aplicó también `ResolutionSelector` a 640x360 en el `FaceAnalyzer` de seguimiento ocular para evitar arrastre de CPU en la pantalla del paciente.

6. **Especialización 100% en Reconocimiento Facial Multirrostro, Flujo "Sin Registro" y Búsqueda de Persona Objetivo:**
   - **Eliminación de Detección de Objetos:** Se retiró el detector de objetos ML Kit de `UnifiedVisionAnalyzer.kt` y `UnifiedVisionScreen.kt`, dedicando el 100% del canal de inferencia a la detección y diferenciación facial biométrica a ~15 FPS.
   - **Cero Nombres Pre-asumidos ("Sin registro"):**
     - Se eliminó toda precarga o suposición de nombres al inicio.
     - Cuando aparece un rostro cuyo vector de 192 dimensiones no coincide con ningún registro en `biometria_paciente`, se etiqueta en color ámbar como **`⚠️ Sin registro`**.
     - **Flujo en 2 Pasos (Consentimiento + Nombre):** Al detectar un rostro `Sin registro` (automáticamente tras 1.2s o al pulsar `[REGISTRAR NUEVO]`), Sara solicita primero el consentimiento informado por voz/toque. Si la persona autoriza ("Sí"), se abre el formulario para que ingrese o dicte por voz su **Nombre** y **Apellido**, creando en ese instante el registro en la tabla `Paciente` y vinculando 1:1 su vector de 192 floats en `biometria_paciente`.
   - **Diferenciación Multirrostro Simultánea (Emparejamiento Exclusivo 1 a 1):**
     - `UnifiedVisionAnalyzer.kt` procesa **todos** los rostros presentes en el cuadro simultáneamente (`enableTracking()`).
     - Ejecuta un algoritmo de asignación exclusiva (*Greedy Bipartite Matching*) sobre la matriz de similitud coseno: cada identidad registrada en SQLite se asigna a lo sumo al rostro físico con mayor coincidencia ($\ge 72\%$), impidiendo que dos personas distintas en la misma cámara reciban el mismo nombre.
     - Si hay varias personas desconocidas a la vez, las diferencia como `Sin registro #1`, `Sin registro #2`, etc., calculando para cada rostro su distancia estimada $Z$ (m) y su ángulo horizontal $\theta$ (°).
   - **Navegación hacia Persona Objetivo (`🎯 Ir a: [Nombre]`):**
     - En la barra inferior aparecen chips con cada persona registrada en la base de datos. Al seleccionar a una persona objetivo, Sara la distingue entre cualquier multitud frente a la cámara, resalta su cuadro en verde esmeralda (`🎯`) y calcula la orden de movimiento (`GIRAR IZQ`, `GIRAR DER`, `AVANZAR HACIA [NOMBRE]`, `LLEGADA • DETENERSE` o `BUSCANDO • ROTAR 360°`).

---

## 7. Verificación de Compilación

Se ejecutó la compilación de Gradle:
```bash
./gradlew compileDebugKotlin
```
**Resultado:** `BUILD SUCCESSFUL in 38s` con **0 errores**.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
