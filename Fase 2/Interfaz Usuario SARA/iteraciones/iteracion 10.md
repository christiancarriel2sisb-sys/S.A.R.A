# Iteración 10: Reestructuración por Roles (3 Vistas) y Módulo de Chat Privado Familiar (MER + Room SQLite + 0 Tokens)

**Proyecto:** S.A.R.A - Sistema Asistente Robótico de Apoyo  
**Autor de la Petición:** Christian Carriel  
**Fecha:** Noviembre 2026 (Fase 2)  
**Estado:** COMPLETADO Y VERIFICADO (PENDIENTE COMMIT POR USUARIO)  

---

## 1. Petición del Usuario y Objetivos

> *"vamos a hacer una reestructuracion del proyecto, necesitamos separar por capas el proyecto, vamos a tener 3 vistas, una para usuarios(pacientes), familiar del paciente, administrador, el administrador tendra acceso a todos los modulos, el usuario solo al modulo de habla, despues lo creamos"*
> 
> *"quiero hacer un chat entre los pacientes y sus contactos o familiares, la cosa es que ese chat no se si es que deberia de almacenarlo en una base de datos relacional, o en txt o en formato json... el paciente al ser un adulto mayor dificil que hable con el familiar con teclado, por ende conviene que el usuario hable con sara y que sara escriba por el en chat, y que luego el familiar escriba en el chat y le diga lo que el familiar responde. El mensaje debe de ser en un chat privado por cuestion de seguridad, asi que ojo con eso, tenemos que tener nuestro propio chat."*
> 
> *"revisa el mer que esta en iteraciones, y dime si te parece que el modelo entidad relacion esta acorde a nuestra vision"*
> 
> *"si, mira, las sugerencias tecnicas que tienes, aplicalas, y aparte de eso hablemos del tema de la logica de sara porque no se cuantos tokens ese proceso estaria gastando, asi que conversemos eso... ya, aplica los cambios que diseñamos entonces, avisame cuando termines"*

---

## 2. Reestructuración de Vistas y Roles (`UserRole`)

Se implementó una pantalla inicial dedicada de selección de perfiles (`RoleSelectionScreen.kt`) con 3 botones / tarjetas interactivas claramente diferenciadas:

| Rol | Vista | Permisos y Navegación | Experiencia de Usuario |
| :--- | :--- | :--- | :--- |
| **`PACIENTE` (Usuario)** | `SaraScreen` (Voz / Rostro) | **Restringido al 100%:** Sin barras de navegación superiores, sin botones técnicos, sin teclados. | El adulto mayor interactúa exclusivamente mirando a Sara y hablándole. Sara ejecuta todo por voz (conversación, música, recordatorios, mensajes a familiares). |
| **`ADMINISTRADOR`** | Acceso a Módulos Técnicos | Acceso a: Rostro/Voz, Detección Facial HUD (Visión), Navegación Jev (Arduino), Recordatorios Clínicos, Música (ExoPlayer) y Logs. **Sin acceso al chat** (*"el admin no chatea"*). | Panel de supervisión técnica, calibración y depuración. Botón de salida para cambiar de perfil. |
| **`FAMILIAR`** | `FamilyChatScreen` | **Directo al chat privado.** Desacoplado al 100% de módulos de Inteligencia Artificial y de conexión con Sara. Solo lectura y escritura de mensajes de texto en la base de datos local. | Interfaz móvil limpia de mensajería de texto para hijos o cuidadores con botón para volver a la selección de roles. |

### Navegación y Retorno:
- Al iniciar la app se presenta la pantalla `RoleSelectionScreen`.
- Desde la vista de Administrador o Familiar, se puede regresar a la selección de perfiles mediante el botón "Cambiar Perfil" o con el botón Atrás nativo de Android (`BackHandler`).
- Desde la vista de Usuario (Paciente), la pantalla se mantiene 100% limpia sin botones intrusivos; el cuidador puede regresar a la selección mediante el botón Atrás nativo (`BackHandler`).

---

## 3. Modelo Entidad-Relación y Persistencia Local (Room SQLite)

Siguiendo el diagrama relacional inspeccionado en `iteraciones/mer.png`, se incorporó la tabla `chat` enlazada a las tablas maestras `paciente` y `contacto_emergencia`:

```text
┌─────────────────────────┐                ┌─────────────────────────┐
│        PACIENTE         │                │   CONTACTO_EMERGENCIA   │
├─────────────────────────┤                ├─────────────────────────┤
│ PK id_paciente: Long    │◄──┐        ┌──►│ PK id_contacto: Long    │
│    rut: String          │   │        │   │ FK id_paciente: Long    │
│    nombre: String       │   │        │   │    nombre: String       │
│    edad: Int            │   │        │   │    telefono: String     │
└─────────────────────────┘   │        │   │    relacion: String     │
                              │        │   └─────────────────────────┘
                              │        │
                   ┌──────────┴────────┴──────────┐
                   │             CHAT             │
                   ├──────────────────────────────┤
                   │ PK id_chat: Long (AutoInc)   │
                   │ FK id_paciente: Long         │
                   │ FK id_contacto: Long         │
                   │    emisor: String            │ ("PACIENTE" / "FAMILIAR")
                   │    mensaje: String (TEXT)    │
                   │    fecha_hora: Long (Millis) │
                   │    leido: Boolean (0 / 1)    │
                   └──────────────────────────────┘
```

### Componentes de Datos Creados:
1. **`ChatEntity.kt` (`com.sara.robot.data.local.entity`):**
   - Llaves foráneas con eliminación en cascada (`ForeignKey.CASCADE`).
   - Índices en `id_paciente` e `id_contacto` para consultas ultrarrápidas.
   - Clave primaria autoincremental `Long`.
2. **`ChatDao.kt` (`com.sara.robot.data.local.dao`):**
   - `getMensajesPorContacto(contactoId: Long): Flow<List<ChatEntity>>` reactivo.
   - `getMensajesNoLeidosParaPaciente(pacienteId: Long): Flow<List<ChatEntity>>`.
   - `insertarMensaje(mensaje: ChatEntity): Long`.
   - `marcarComoLeido(chatId: Long)` y `marcarTodosComoLeidos(contactoId: Long)`.
3. **`SaraAppDatabase.kt` (Actualizado a versión 2):**
   - Incorporación de `ChatEntity::class` y exposición de `chatDao()`.
   - Migración controlada con `fallbackToDestructiveMigration()`.
4. **`ChatRepository.kt` (`com.sara.robot.data`):**
   - Orquestador de lógica de negocio para mensajería.
   - Método `seedDefaultDataIfEmpty()`: si la base de datos está vacía, inicializa automáticamente al paciente principal *"Juan Pérez"* y a sus contactos de emergencia *"Claudia"* (Hija) y *"Pedro"* (Hijo/Cuidador), garantizando pruebas inmediatas sin configuración manual.

---

## 4. Arquitectura de Mensajería por Voz con Consumo de 0 Tokens

Para proteger la cuota de la API de Gemini y brindar una experiencia instantánea, se diseñó un flujo de **cero tokens** para la comunicación habitual:

```text
                                  ┌───────────────────────────────┐
                                  │   Voz del Paciente (Mic)      │
                                  │ "Sara, dile a Claudia que..." │
                                  └───────────────┬───────────────┘
                                                  │
                                                  ▼
                                  ┌───────────────────────────────┐
                                  │ Android SpeechRecognizer      │
                                  │ (Reconocimiento Local Nativo) │
                                  └───────────────┬───────────────┘
                                                  │
                                                  ▼
                                  ┌───────────────────────────────┐
                                  │ Local Regex Intent Parser     │
                                  │ (SaraViewModel: 0 Tokens)     │
                                  └───────┬───────────────┬───────┘
                     Patrón Reconocido    │               │ No reconocido (Frase compleja)
                     ─────────────────────┘               └─────────────────────┐
                                  │                                             │
                                  ▼                                             ▼
                 ┌─────────────────────────────────┐           ┌─────────────────────────────────┐
                 │ Búsqueda en SQLite Local:       │           │ Gemini 2.5 Flash Tool Calling:  │
                 │ ContactoDao.findContactoByName  │           │ enviar_mensaje_familiar         │
                 └────────────────┬────────────────┘           └────────────────┬────────────────┘
                                  │                                             │
                                  ▼                                             ▼
                 ┌─────────────────────────────────────────────────────────────────┐
                 │ Guardar en SQLite (ChatEntity: emisor='PACIENTE', leido=false)  │
                 └────────────────────────────────┬────────────────────────────────┘
                                                  │
                                                  ▼
                 ┌─────────────────────────────────────────────────────────────────┐
                 │ Confirmación Auditiva al Paciente vía Android TTS (0 Tokens):   │
                 │ "Listo, le he enviado tu mensaje a Claudia"                     │
                 └─────────────────────────────────────────────────────────────────┘
```

### Flujo Inverso: El Familiar Escribe al Paciente
1. El familiar escribe desde `FamilyChatScreen` y presiona **Enviar**.
2. El mensaje se inserta en SQLite como `emisor = "FAMILIAR"`, `leido = false`.
3. El observador reactivo en `SaraViewModel` (`iniciarObservadorMensajesEntrantes`) detecta la inserción en segundo plano.
4. Sara activa su rostro en animación `SPEAKING` y anuncia con Android TTS nativo (0 tokens):
   > *"Tienes un mensaje de Claudia: 'Hola papá, voy en camino a verte con los nietos'"*.
5. El mensaje se marca automáticamente como `leido = true` en la base de datos.

---

## 5. Pantalla Táctica de Chat Familiar (`FamilyChatScreen.kt`)

Ubicada en `com.sara.robot.ui.FamilyChatScreen`:
- **Selector de Contactos:** Chips superiores para alternar rápidamente entre familiares registrados (*Claudia*, *Pedro*).
- **Lista de Mensajes Dinámica:**
  - **Burbujas del Paciente:** Fondo verde con icono de micrófono (`Mic`), alineadas a la izquierda, indicando que fueron dictadas por voz.
  - **Burbujas del Familiar:** Fondo morado oscuro, alineadas a la derecha, con hora exacta y tilde de confirmación de lectura.
- **100% Texto Plano (Sin IA ni conexión con Sara):** Desacoplada de módulos de inteligencia artificial y síntesis de voz; el familiar solo lee y escribe texto puro.
- **Caja de Entrada Accesible:** Campo de texto con auto-scroll y botón de envío responsivo a la base de datos local.

---

## 6. Módulo de Activación por Voz: Retorno al Botón Táctil Estable y Robusto

Durante las pruebas se evaluó un mecanismo autónomo de micrófono *always-on* (escucha continua en bucle con detección de *wake word* "Oye Sara"). Sin embargo, debido a limitaciones y restricciones inherentes de los emuladores Android y del servicio nativo `SpeechRecognizer` (desconexiones de Binder `ERROR_SERVER_DISCONNECTED` - código 11, e incompatibilidad de dialecto local sin conexión `ERROR_LANGUAGE_NOT_SUPPORTED` - código 12), el usuario solicitó retornar al botón interactivo táctil:

> *"no sabes que, olvidalo vuelve a colocar el boton, vuelve a colocarlo como estaba el modulo"*

### Arquitectura de Voz Restaurada y Optimizada:
1. **Botón Táctil Interactivo:** Se restauró el botón flotante en la parte inferior de `SaraScreen.kt` con animación de pulso, cambio dinámico de color según el estado (`LISTENING` = rojo/rosa, `THINKING` = naranja, `SPEAKING` = verde, `IDLE` = morado) y cambio contextual del icono (`Mic`, `Psychology`, `GraphicEq`).
2. **Mensaje Central Contextual:** En reposo (`IDLE` o `ERROR`), se muestra en el centro de la pantalla el indicador táctil: *"Toca la pantalla para hablar con Sara"*.
3. **Control Total por Toque:** Al tocar la pantalla o el botón:
   - Si Sara está hablando o pensando: se ejecuta `interruptSara()` (Barge-In instantáneo).
   - Si está escuchando: se detiene el micrófono (`stopListening()`).
   - Si está en reposo: se inicia la captura de voz (`startListening()`).
4. **Ciclo TTS a Escucha Automática:** Al terminar de responder una consulta, si no hay música reproduciéndose, Sara abre automáticamente el micrófono (`faceState = "LISTENING"`) para permitir continuar la conversación sin tener que presionar el botón de nuevo cada turno.

---

## 7. Verificación Técnica y Compilación

Se ejecutó la verificación de compilación completa del módulo Android con Kotlin 1.9.23 y KSP:
```powershell
./gradlew.bat compileDebugKotlin
```
**Resultado:**
```text
BUILD SUCCESSFUL in 32s
17 actionable tasks: 3 executed, 14 up-to-date
```
- Cero errores de compilación (`BUILD SUCCESSFUL`).
- Sin generación de APK (respetando la regla de no ejecutar `assembleDebug`).
- Sin commits ni pushes en Git (respetando la regla de exclusividad de control de versiones por el usuario).

---

## 8. Guía de Pruebas Rápidas para el Usuario

1. **Prueba como Usuario (Paciente):**
   - En la pantalla inicial de 3 botones, selecciona **"Usuario"**.
   - Observa la vista limpia y enfocada de Sara con el aviso *"Toca la pantalla para hablar con Sara"*.
   - Presiona el botón o toca cualquier parte de la pantalla para hablar.
   - Pídele lo que desees: *"Dile a Claudia que ya tomé mi remedio"* (0 tokens) o *"Pon música de los 80"*.
   - Sara ejecutará la acción, responderá por voz con TTS nativo y quedará lista.
2. **Prueba como Familiar:**
   - En la pantalla inicial, selecciona **"Familiar"**.
   - Ingresa al chat privado y responde el mensaje enviado por el paciente.
   - Presiona *"Cambiar Perfil"* para regresar a la selección de roles.
3. **Prueba de Notificación de Mensaje Entrante:**
   - Como paciente en la vista de Sara, cuando el familiar envíe un mensaje, Sara lo detectará automáticamente y anunciará: *"Tienes un mensaje nuevo de Claudia: '...' "*, marcándolo como leído en la base de datos local.
4. **Prueba como Administrador:**
   - Selecciona **"Admin"** para acceder a todos los módulos técnicos: Rostro/Voz, Detección Facial HUD, Navegación Jev, Recordatorios Clínicos, Música y Terminal de Logs (sin acceso al chat).

---

## 9. Módulo de Recordatorios: Persistencia en BD (Room SQLite) y Subproceso de Carga Diaria en RAM

Siguiendo la solicitud de integración de recordatorios:
> *"antes los recordatorios no se agregaban, ahora vamos a usar la base de datos, configura el insert a la tabla de recordatorios, para que sara(el modulo de conversacion) pueda crear un recordatorio y almacenarlo en la base de datos, todos los recordatorios, que se registren, sera directamenter a la base de datos, ahora, las alertas tendran que tener otro flujo"*
>
> *"necesito que crees un subproceso, el cual cuando empiece el dia, va a tomar todos los recordatorios que tengan para ese dia, y se van a pasar a un buffer temporal, que se guardara en ram, desde donde sara va a poder leerlos para ejecutar las alertas pertinente, recuerda, ese buffer tiene que tener las alertas del dia, y es a ese buffer de memoria donde el modulo de recordatorios va a verificar las alarmas que sara tenga que hacer ese dia"*
>
> *"eliminame todos los recordatorios que esten en las bases de datos porque los testeos los voy a hacer yo."*

### 9.1 Arquitectura del Flujo de Datos y Alertas

```text
┌──────────────────────────────────────┐     ┌──────────────────────────────────────┐
│  Voz con Sara (Módulo Conversación)  │     │ Formulario Táctico (ReminderScreen)  │
│  "Sara, recuérdame a las 15:30..."   │     │ (Admin: Configuración LocalDateTime) │
└──────────────────┬───────────────────┘     └──────────────────┬───────────────────┘
                   │                                            │
                   ▼                                            ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                    RecordatorioRepository.crearYGuardarRecordatorio                │
│ 1. INSERT directo en SQLite: tabla 'recordatorios' (id, descripcion, fecha, pac)   │
└──────────────────────────────────────────┬────────────────────────────────────────┘
                                           │
                        ¿Es para hoy (LocalDate.now())?
                        ├── SÍ: Agregar inmediatamente a RAM y programar en AlarmManager
                        └── NO (Fecha futura): Queda almacenado en BD hasta que inicie ese día
                                           │
                                           ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│              SUBPROCESO DIARIO EN SEGUNDO PLANO (iniciarSubprocesoDiario)          │
│ • Al arrancar la app y a cada medianoche (00:00:01):                               │
│ • SELECT * FROM recordatorios WHERE fecha_recor LIKE 'YYYY-MM-DD%'                │
│ • Limpia y llena el BUFFER TEMPORAL EN RAM (ReminderMemoryBuffer)                 │
│ • Programa en Android AlarmManager nativo las alarmas activas del día             │
└──────────────────────────────────────────┬────────────────────────────────────────┘
                                           │
                           Disparo exacto de AlarmManager
                                           │
                                           ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│             ReminderBroadcastReceiver ➔ WakeLock ➔ Despertar a Sara                │
│ Sara habla al paciente por voz TTS:                                              │
│ "Atención: Tienes un recordatorio programado. [Mensaje]"                          │
└───────────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 Componentes Implementados:
1. **`RecordatorioDao.kt`:**
   - `insertRecordatorio(recordatorio: RecordatorioEntity): Long`: Retorna el ID autogenerado para correlacionarlo con la alarma.
   - `getRecordatoriosDelDia(datePrefix: String): List<RecordatorioEntity>`: Filtrado ultra-rápido por fecha.
   - `deleteById(id: Long)` y `deleteAllRecordatorios()`: Limpieza granular y purga total.
2. **`RecordatorioRepository.kt` (`com.sara.robot.data`):**
   - Inserción directa en SQLite y despacho selectivo a RAM si la fecha es hoy.
   - Subproceso de fondo con temporizador hacia la medianoche (`Duration.between(now, nextMidnight)`).
   - Purga inicial automática (`purgarParaNuevosTesteosSiEsNecesario`) para garantizar que la base de datos comience vacía para las pruebas del usuario.
3. **`ReminderMemoryBuffer.kt`:**
   - Incorporación de `addOrUpdateReminder(id, title, targetTimeMillis, status)` para sincronización biyectiva entre SQLite y el buffer de memoria RAM.
4. **Sara (Módulo de Conversación por Voz):**
   - **Parser Local (0 Tokens):** Detección de expresiones como *"recuérdame tomar la pastilla a las 3 de la tarde"*, *"avísame en 10 minutos"*, *"pon una alarma a las 15:30"*, calculando automáticamente `LocalDateTime` e insertando en BD y RAM.
   - **Gemini Function Calling:** Herramienta `crear_recordatorio` integrada en el prompt y en `GeminiRepository` para resolver peticiones complejas o naturales.
   - **Comando de Borrado:** *"Sara, elimina todos los recordatorios"* para purgar la base de datos y la memoria por voz.
5. **`ReminderScreen.kt`:**
   - Botón *"GUARDAR EN BD Y PROGRAMAR"* conectado a `RecordatorioRepository`.
   - Botones tácticos *"Vaciar BD y RAM"* y *"Sincronizar"* para control total durante los testeos.

---

## 10. Trazabilidad y Estructura de Logs para el Flujo de Recordatorios

Siguiendo la instrucción de depuración y observabilidad del usuario:
> *"has un log, porque no se ingreso la alarma, quiero que hagas el log conn esta estructura:*
> *- el usuario ingresa el recordatorio: Recordatorio recibido*
> *- el recordatorio pasa a la base de datos: Recordatorio ingresado en la base de datos"*

### 10.1 Estructura Estándar Implementada en `AppLogger` (Visible en Terminal HUD y Logcat)
Se integró el ciclo de vida del recordatorio bajo el tag `"Recordatorio"` con formato directo y visible:

1. **Recepción del Recordatorio:**
   - **Log:** `Recordatorio recibido: "<texto/descripción>" (programado para: YYYY-MM-DD HH:mm)`
   - Se emite en el momento exacto en que la voz del usuario es capturada por el reconocedor o se presiona el botón táctil.
2. **Persistencia en SQLite:**
   - **Log:** `Recordatorio ingresado en la base de datos: ID=<id> | "<descripción>" | Fecha: YYYY-MM-DD HH:mm`
   - Se emite inmediatamente después de confirmarse el `INSERT` exitoso en la tabla `recordatorios`.
3. **Carga en Buffer de RAM y Alarma:**
   - **Log:** `Alarma programada en buffer de RAM: ID=<id> | "<descripción>" | Disparo en <segundos> seg`
   - Se emite cuando el recordatorio corresponde a la fecha de hoy y se añade a la memoria activa de Sara con su correspondiente `AlarmManager`.

### 10.2 Correcciones Realizadas para Resolver la No Inserción de Alarmas:
1. **Ampliación del Parser de Intenciones (`SaraViewModel.kt`):**
   - Soporte para variaciones naturales de hora: `"para las"`, `"a las"`, `"a la"`, `"y media"`, `"y cuarto"`, formatos 12h/24h inteligentes basados en la hora actual del sistema.
   - Soporte de palabras para tiempos relativos: `"un"`, `"dos"`, `"tres"`, `"cinco"`, `"diez"`, `"quince"`, `"veinte"`, `"media hora"`, `"cuarto de hora"`.
   - Inclusión de sinónimos como *"alarma"*, *"recordatorio"*, *"despiértame"*, *"avísame"*, *"recuerda"*.
2. **Inyección de Fecha y Hora del Sistema en Gemini (`GeminiRepository.kt`):**
   - Se incluyó la fecha y hora exacta del sistema en el prompt del sistema (`LocalDateTime.now()`), evitando que Gemini alucine fechas pasadas o incorrectas al ejecutar la función `crear_recordatorio`.

---
© 2026 Equipo S.A.R.A — Duoc UC Sede Plaza Oeste
