# Iteración 8: Módulo de Recordatorios Clínicos con AlarmManager y Buffer Temporal en RAM

**Proyecto:** S.A.R.A - Sistema Asistente Robótico de Apoyo  
**Autor de la Petición:** Christian Carriel  
**Fecha:** Noviembre 2026 (Fase 2)  
**Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)  

---

## 1. Petición del Usuario

> *"Listo, terminamos la base de datos, lo que quiero hacer, es lo siguiente: primero vamos a definir un proceso para poder activar los recordatorios, vamos a coger, y tendremos una pantalla nueva, similar a todos los módulos, como el de reconocimiento de objetos, el módulo de conversación, etc, donde podemos registrar el recordatorio, la fecha y la hora a la que queremos que se haga el recordatorio, vamos a hacer un log, de cada uno de los procesos que se ejecutan en el módulo, desde donde se ingresa el recordatorio, hasta cuando se le avisa al usuario. Vamos a usar AlarmManager, que es una herramienta nativa de android, él va a ser el que se encargue de revisar cuándo se tenga que ejecutar la alarma, entonces cuando el AlarmManager avise que ya es la hora y el día, levanta a Sara y Sara le dice al usuario lo que la alarma diga. Como esto es una primera fase, lo que registremos ahora en el módulo de recordatorios, no debe registrarse en la base de datos, solo en un buffer temporal, así que necesito que crees uno, donde se van a almacenar por ahora las alarmas, para testearlas. Ojo, el buffer tiene que ser ejecutado en la RAM."*

### Requisitos Específicos:
1. **Buffer Temporal en Memoria RAM:** Crear un almacén de recordatorios en memoria volátil (RAM) sin persistencia en disco ni base de datos física para ciclos de prueba ultrarrápidos.
2. **Programador de Alarmas con `AlarmManager`:** Emplear la API nativa de Android para agendar disparos exactos con capacidad de despertar el CPU (`RTC_WAKEUP` y `setExactAndAllowWhileIdle`).
3. **Receptor de Alarma y Despertar de Sara:** `BroadcastReceiver` con `WakeLock` que capta la señal del sistema, actualiza el buffer en RAM, emite notificación prioritaria y lanza a Sara para que hable de viva voz al paciente.
4. **Trazabilidad Completa de Logs:** Registro en `AppLogger` de cada etapa: entrada de usuario ➔ almacenamiento en RAM ➔ agendamiento en AlarmManager ➔ disparo de señal ➔ síntesis de voz en TTS.
5. **Nueva Pantalla de Recordatorios (`ReminderScreen`):** Vista en Jetpack Compose integrada al ecosistema SARA, con formulario, botones de prueba rápida (+10s, +30s, +1m), monitor de alarmas en RAM con cuenta regresiva en vivo y acceso al panel de logs flotante.
6. **Autonomía de Git:** Todos los archivos quedan en el espacio de trabajo local sin ejecutar `commit` ni `push`, dejando el control de versiones en manos del usuario.

---

## 2. Arquitectura del Módulo de Recordatorios

```text
[ Pantalla ReminderScreen ] 
       │ 1. Usuario ingresa mensaje y tiempo (+10s, +30s, etc.)
       ▼
[ ReminderMemoryBuffer (RAM) ] ➔ 2. Guarda objeto InMemoryReminder en memoria volátil
       │
       ▼
[ AlarmScheduler (AlarmManager) ] ➔ 3. Registra alarma exacta RTC_WAKEUP en el SO Android
       │
       ▼ (Transcurre el tiempo programado...)
[ ReminderBroadcastReceiver ] ➔ 4. Android dispara la alarma (WakeLock + Notificación)
       │  5. Marca el recordatorio como DISPARADO en el buffer de RAM
       ▼
[ MainActivity & SaraViewModel ] ➔ 6. Levanta a Sara en pantalla
       │
       ▼
[ TextToSpeechManager ] ➔ 7. Sara habla: "Atención: Tienes un recordatorio programado. [Mensaje]"
```

---

## 3. Componentes Implementados

### 3.1 Buffer en Memoria RAM (`ReminderMemoryBuffer.kt`)
* **Ubicación:** `com.sara.robot.reminders.ReminderMemoryBuffer`
* **Almacenamiento:** `ConcurrentHashMap<Long, InMemoryReminder>` gestionado en la memoria RAM del proceso.
* **Flujo Reactivo:** Expone `remindersFlow: StateFlow<List<InMemoryReminder>>` para actualización automática de la UI en tiempo real sin recargar la pantalla.
* **Estados:**
  * `PROGRAMADA`: Alarma agendada y en espera del tiempo límite.
  * `DISPARADA`: Alarma ejecutada por AlarmManager y anunciada por Sara.
  * `CANCELADA`: Alarma cancelada por el usuario antes de su disparo.

### 3.2 Programador de Alarmas Nativo (`AlarmScheduler.kt`)
* **Ubicación:** `com.sara.robot.reminders.AlarmScheduler`
* Utiliza `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMillis, pendingIntent)`, garantizando ejecución incluso bajo el modo Doze de Android (ahorro extremo de energía).
* Crea un `PendingIntent.getBroadcast` inmutable vinculado a la acción `ACTION_TRIGGER_REMINDER`.
* Permite cancelación selectiva mediante `cancel(reminderId)`.

### 3.3 Receptor de Eventos (`ReminderBroadcastReceiver.kt`)
* **Ubicación:** `com.sara.robot.reminders.ReminderBroadcastReceiver`
* Registrado en `AndroidManifest.xml` con permisos `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` y `WAKE_LOCK`.
* **Secuencia de disparo:**
  1. Recibe la señal del sistema y extrae el ID y mensaje.
  2. Actualiza el estado en `ReminderMemoryBuffer.markTriggered(id)`.
  3. Adquiere un `WakeLock` temporal para encender la pantalla.
  4. Genera una notificación de canal prioritario con vibración.
  5. Despacha un Intent a `MainActivity` con flags `NEW_TASK` y `SINGLE_TOP` para enfocar a Sara.

### 3.4 Síntesis de Voz en Sara (`SaraViewModel.kt` y `MainActivity.kt`)
* Se integró el método `viewModel.hablarRecordatorio(mensaje)`:
  * Cambia el estado emocional del rostro a `SPEAKING`.
  * Agrega el mensaje al historial de chat.
  * Ejecuta la voz nativa de Sara por TTS: *"Atención: Tienes un recordatorio programado. [Mensaje]"*.
* `MainActivity` procesa el evento en `onCreate` y `onNewIntent` sin reiniciar la actividad.

### 3.5 Interfaz de Usuario Táctica (`ReminderScreen.kt`)
* Diseñada para orientación horizontal (`Landscape`):
  * **Columna Izquierda:** Formulario con campo de texto para el recordatorio, selectores de fecha y hora, accesos directos para pruebas rápidas (+30s, +1m, +5m, +1 día), visor de cálculo en tiempo real y botón de programación en neón púrpura.
  * **Columna Derecha:** Monitor de alarmas en RAM en tiempo real, con badge de estado, fecha programada (`dd/MM/yyyy`), hora programada (`HH:mm:ss`), cuenta regresiva dinámica (`EN X SEG` / `EN X MIN`) y botón para cancelar o eliminar.
  * **Barra Superior:** Acceso a módulos (Rostro, Visión, Navegación) y botón flotante de Terminal de Logs (`LogPanelDialog`).
  * **Corrección de Diseño (Error 4):** En orientación horizontal compacta, el botón quedaba aplastado en una línea delgada por la compresión vertical de la columna. Se eliminó el `Spacer(weight=1f)` elástico, se agregó `verticalScroll(formScrollState)`, se redujeron los paddings y se asignó una altura mínima inmune a compresión (`defaultMinSize(minHeight = 46.dp)`).

### 3.6 Configuración de Fecha con `LocalDateTime` y Cálculo de Milisegundos
* **Petición del Usuario:** Configuración de la alarma especificando fecha (día y mes) y hora exacta, calculando los milisegundos necesarios para el `AlarmManager` mediante la API moderna de `java.time.LocalDateTime`.
* **Implementación:**
  1. **Estado reactivo:** `var targetDateTime by remember { mutableStateOf(LocalDateTime.now().plusMinutes(2)...) }`
  2. **Selectores Nativos:**
     * **Día y Mes:** `DatePickerDialog` nativo de Android que permite seleccionar con precisión el día y mes (y año), validando que no sea anterior al día actual (`minDate`).
     * **Hora y Minuto:** `TimePickerDialog` nativo en formato 24 horas para definir la hora exacta de la toma o recordatorio.
  3. **Cálculo de Milisegundos Epoch:**
     ```kotlin
     val targetEpochMillis = targetDateTime
         .atZone(ZoneId.systemDefault())
         .toInstant()
         .toEpochMilli()
     ```
  4. **Validación y AlarmManager:** Se valida que la fecha/hora seleccionada sea estrictamente posterior al instante actual (`diff > 0`). El valor `targetEpochMillis` se suministra a `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMillis, pendingIntent)`.
  5. **Trazabilidad de Logs:**
     ```text
     [ReminderScreen] 1. Solicitud de registro con LocalDateTime: 2026-11-13T14:30:00
     [ReminderScreen]    -> Día: 13, Mes: 11 (NOVEMBER), Hora: 14:30
     [ReminderScreen]    -> Milisegundos calculados para AlarmManager: 1794591000000 ms (disparo en 120 seg)
     [ReminderBuffer] [RAM] Recordatorio registrado con LocalDateTime: ID=1001 | Titulo='...' | Programado para: 13/11/2026 14:30:00 (Epoch: 1794591000000ms)
     ```

---

## 4. Trazabilidad de Logs en Vivo (`AppLogger`)

Al programar y dispararse una alarma, el sistema genera la siguiente secuencia de auditoría:

```text
[14:10:02.120] [ReminderScreen]   1. Solicitud de registro: 'Tomar pastilla de presión' en 10 segundos
[14:10:02.125] [ReminderBuffer]   [RAM] Recordatorio registrado: ID=1001 | Titulo='Tomar pastilla de presión' | Programado para: 13/11/2026 14:10:12
[14:10:02.132] [AlarmScheduler]  [AlarmManager] Alarma programada: ID=1001 | Disparo en ~10 seg (13/11/2026 14:10:12)
... (10 segundos después) ...
[14:10:12.140] [ReminderReceiver] 🔔 [ALARMA DISPARADA] Señal de AlarmManager recibida | ID=1001 | 'Tomar pastilla de presión'
[14:10:12.145] [ReminderBuffer]   [RAM] Recordatorio DISPARADO: ID=1001 | Titulo='Tomar pastilla de presión'
[14:10:12.155] [ReminderReceiver] Levantando a Sara en pantalla principal para anunciar el recordatorio...
[14:10:12.170] [MainActivity]     Procesando recordatorio despertado por AlarmManager: 'Tomar pastilla de presión'
[14:10:12.185] [SaraViewModel]    Sara activada por recordatorio: 'Atención: Tienes un recordatorio programado. Tomar pastilla de presión'
```

---

## 5. Archivos Creados y Modificados

| Archivo | Tipo | Descripción |
| :--- | :--- | :--- |
| `reminders/ReminderMemoryBuffer.kt` | Creado | Buffer en memoria RAM para alarmas temporales (Fase 1). |
| `reminders/AlarmScheduler.kt` | Creado | Programador nativo con `AlarmManager` y soporte Doze. |
| `reminders/ReminderBroadcastReceiver.kt` | Creado | BroadcastReceiver que despierta a Sara ante el disparo de la alarma. |
| `ui/ReminderScreen.kt` | Creado | Pantalla completa de recordatorios con botones de prueba rápida y monitor en RAM. |
| `ui/SaraViewModel.kt` | Modificado | Método `hablarRecordatorio(mensaje)` con voz TTS y estado facial. |
| `MainActivity.kt` | Modificado | Manejo de intents de alarma, nuevo estado `AppScreen.REMINDERS` y ciclo `onNewIntent`. |
| `ui/SaraScreen.kt` | Modificado | Botón de acceso directo al módulo de recordatorios en barra superior. |
| `AndroidManifest.xml` | Modificado | Permisos de alarmas exactas, WakeLock y registro del BroadcastReceiver. |
| `iteraciones/iteracion 8.md` | Creado | Documentación de la iteración y especificación técnica. |

---

## 6. Verificación y Compilación
* Compilación ejecutada con Gradle y JDK 17: **`BUILD SUCCESSFUL`**.
* Todos los cambios se encuentran disponibles en el directorio local listos para que el usuario realice el commit y push correspondientes.
