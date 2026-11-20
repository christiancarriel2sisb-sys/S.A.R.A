# Iteración 12: 4 Perfiles (Modo Desarrollador), Panel de Monitoreo Administrativo, Eliminación Lógica en SQLite, Subproceso Detector de Errores (.md) y Red Neuronal de Intenciones Jev

**Proyecto:** S.A.R.A - Sistema Asistente Robótico de Apoyo  
**Autor de la Petición:** Christian Carriel  
**Fecha:** Noviembre 2026 (Fase 2)  
**Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)  

---

## 1. Resumen de Requerimientos

1. **Nuevo Perfil "Desarrollador" (4 Roles):** Agregar el 4° perfil (`DESARROLLADOR`) para que Christian tenga acceso a todos los módulos operativos de Sara y a los nuevos módulos de experimentación e IA.
2. **Separación de Logs por Módulo:** Aislar un buffer de logs independiente para cada módulo del sistema para monitorear su salud individual.
3. **Eliminación Lógica (`estado: Boolean`):** Agregar la columna `estado` a las tablas `Paciente` y `Contacto_emergencia` en Room SQLite (`version = 4`), de modo que al eliminar un registro solo pase a `inactivo` (`estado = false`) sin borrar los datos históricos ni incluirlos en telemetría o informes.
4. **Nueva Interfaz de Monitoreo para el Perfil Administrador (`AdminDashboardScreen.kt`):**
   - Panel interactivo con barra lateral izquierda y 5 herramientas:
     - **Pacientes:** Módulo reutilizable de búsqueda por RUT (`Id_paciente`), Nombre o Apellido, ficha clínica de base de datos, y botones para Agregar, Modificar y Eliminar (Inactivar).
     - **Familiares:** Integra el módulo de búsqueda de paciente en la parte superior, lista los contactos de emergencia activos asociados y provee botones para Agregar, Modificar y Eliminar (Inactivar).
     - **Recordatorios:** Módulo de recordatorios clínicos integrado.
     - **Música:** Módulo multimedia integrado.
     - **Control de Sara:** Tablero de tarjetas por módulo con nombre, estado (`Operando`, `Dormido`, `Detenido`), indicador luminoso LED (Verde / Plateado / Rojo), subproceso en segundo plano de detección de errores en logs y botón **"Notificar Error (.md)"** cuando un módulo se detiene en rojo.
5. **Módulo de Red Neuronal de Detección de Intenciones Jev en Desarrollador (`JevNeuralIntentClassifier.kt` & `JevNeuralStudioScreen.kt`):**
   - Red neuronal entrenable on-device con diccionario dinámico TF-IDF, lematización en español (stemming), bigramas y anclas temporales, capa densa $W \cdot X + b$, activación Softmax y entrenamiento con Backpropagation (Gradiente Descendente).

---

## 2. Arquitectura del Subproceso de Detección de Errores y Logs Independientes

Ubicación: `android/app/src/main/java/com/sara/robot/core/AppLogger.kt`

Para garantizar máxima eficiencia ($O(1)$ en tiempo y 0% de consumo de CPU en reposo):
1. **Aislamiento por Módulo (`SaraModule`):** Cada evento de log es enrutado instantáneamente a su módulo (`CONVERSACION_IA`, `VISION_BIOMETRIA`, `RECORDATORIOS`, `MUSICA_MULTIMEDIA`, `CHAT_FAMILIAR`, `NAVEGACION_HARDWARE`, `ORQUESTADOR_JEV`, `SISTEMA_BD`).
2. **Cola Reactiva No Bloqueante (`Channel<LogEntry>`):** En lugar de recorrer listas completas mediante *polling* ($O(N)$), `AppLogger.log(...)` deposita cada nuevo evento en un `Channel.UNLIMITED` (`trySend`).
3. **Subproceso Consumidor Asíncrono (`Dispatchers.Default`):** Una corrutina dedicada (`startErrorDetectionSubprocess`) inspecciona incrementalmente cada evento a medida que llega:
   - Si detecta `LogLevel.ERROR` o patrones de fallo crítico (ej. desconexión de API de Gemini, falta de API Key, excepción HTTP 4xx/5xx, fallo de cámara), conmuta el módulo inmediatamente a **`ModuleOperationalState.DETENIDO` (Luz Roja)** y almacena el evento en `detectedErrors`.
   - Si registra actividad normal, pasa a **`OPERANDO` (Luz Verde)**.
   - Un *watchdog* ligero verifica cada 15 segundos si un módulo sin errores lleva más de 90 segundos sin actividad para pasarlo a **`DORMIDO` (Luz Plateada)**.
4. **Generador de Informe Markdown (`.md`):** Cuando un módulo está en rojo (`DETENIDO`), se habilita el botón **`NOTIFICAR ERROR (.MD)`**, que genera un archivo estructurado `informe_error_<modulo>_<timestamp>.md` con la tabla de errores detectados, la traza de los últimos 25 eventos del módulo y el diagnóstico preliminar.

---

## 3. Ubicación y Arquitectura de la Red Neuronal de Intenciones Jev

- **Motor Matemático y Red Neuronal:**  
  `android/app/src/main/java/com/sara/robot/navigation/reasoning/JevNeuralIntentClassifier.kt`
- **Interfaz de Testeo y Entrenamiento en Vivo (Desarrollador):**  
  `android/app/src/main/java/com/sara/robot/ui/JevNeuralStudioScreen.kt`

### Funcionamiento Interno de `JevNeuralIntentClassifier.kt`:
1. **Tokenizador Morfológico y Stemmer en Español (`tokenizeAndExtractFeatures`):**
   - Detecta patrones sintácticos de tiempo (`<PATRON_HORA>`), números (`<NUMERO>`), bigramas clave (`a_las`, `tengo_que`, `me_toca`, `dile_a`, `busca_a`) y reduce palabras a su raíz (`medicamento` $\rightarrow$ `medicam`, `pastillas` $\rightarrow$ `pastill`).
2. **Vectorizador TF-IDF con Normalización $L_2$ (`tokensToTfIdfVector`):**
   - Pondera automáticamente cada raíz según su exclusividad en el diccionario aprendido.
3. **Forward Pass y Activación Softmax (`forwardSoftmax`):**
   - Calcula logits $z_k = \sum_i W_{k,i} x_i + b_k$ y probabilidades $P(y=k|X)$ para los 5 módulos (`RECORDATORIO`, `MUSICA`, `CHAT_FAMILIAR`, `MOVIMIENTO_ROSTRO`, `CONVERSACION_LIBRE`).
4. **Backward Pass / Entrenamiento en Vivo (`reconstruirDiccionarioYEntrenar` y `aprenderNuevaFrase`):**
   - Ejecuta **Backpropagation con Gradiente Descendente y Cross-Entropy Loss** ($\delta_k = P_k - y_k$), ajustando los pesos sinápticos $W$ y sesgos $b$ en milisegundos directamente desde la interfaz del Desarrollador.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
