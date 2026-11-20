# Diccionario de Datos — S.A.R.A

**Proyecto:** S.A.R.A — Sistema Asistente Robótico de Apoyo para Adultos Mayores  
**Base de Datos:** `sara_local_db` (`SaraAppDatabase`, Android Room `2.6.1`, versión `4`)  
**Motor Físico:** SQLite 3  
**Última actualización:** Noviembre 2026

---

## 1. Convenciones

* **PK:** Clave Primaria (*Primary Key*). `(AI)` indica `AUTOINCREMENT`.
* **FK:** Clave Foránea (*Foreign Key*). Todas las FK del esquema aplican `ON UPDATE NO ACTION` y `ON DELETE CASCADE`.
* **UK:** Restricción Única (*Unique Index*).
* **Tipo SQLite / Tipo Kotlin / Tipo Lógico:** Se documentan los tres niveles para trazabilidad entre el diseño conceptual/MER, la implementación en Kotlin (Room) y el almacenamiento físico SQLite.
* **Booleanos en SQLite:** Room persiste `Boolean` como `INTEGER` (`1 = true`, `0 = false`).

---

## 2. Resumen General de Tablas

| N° | Tabla física | Clase Kotlin (Room) | DAO Asociado | Clave Primaria | Volumen inicial |
| :-: | :--- | :--- | :--- | :--- | :---: |
| 1 | `Paciente` | `PacienteEntity` | `PacienteDao` | `Id_paciente` | 50 filas |
| 2 | `Contacto_emergencia` | `ContactoEmergenciaEntity` | `ContactoEmergenciaDao` | `id_contacto` (AI) | 50 filas |
| 3 | `recordatorios` | `RecordatorioEntity` | `RecordatorioDao` | `id_recordatorio` (AI) | 10 filas |
| 4 | `juegos` | `JuegoEntity` | `JuegoDao` | `id_juego` (AI) | 5 filas |
| 5 | `historial_estimulacion` | `HistorialEstimulacionEntity` | `HistorialEstimulacionDao` | `id_historial` (AI) | 125 filas |
| 6 | `chat` | `ChatEntity` | `ChatDao` | `id_chat` (AI) | 0 filas *(uso en vivo)* |
| 7 | `biometria_paciente` | `BiometriaEntity` | `BiometriaDao` | `id_biometria` (AI) | 0 filas *(enrolamiento)* |
| — | `room_master_table` | *(interna de Room)* | — | `id` | 1 fila |

---

## 3. Especificación Detallada por Tabla

### 3.1 Tabla `Paciente`
* **Descripción:** Almacena la ficha demográfica y el estado de vigencia de cada adulto mayor atendido por S.A.R.A.
* **Clave Primaria:** `Id_paciente` (manual, no autoincremental).
* **Claves Foráneas:** Ninguna.
* **Índices:** Índice implícito sobre `PRIMARY KEY (Id_paciente)`.
* **Política de borrado:** Eliminación lógica desde la UI administrativa (`estado = 0`); el borrado físico propaga `CASCADE` a todas las tablas dependientes.

| N° | Campo | Tipo SQLite | Tipo Kotlin | Tipo Lógico | Nulo | Clave | Valor por defecto | Restricciones / Formato | Descripción | Ejemplo |
| :-: | :--- | :--- | :--- | :--- | :-: | :-: | :--- | :--- | :--- | :--- |
| 1 | `Id_paciente` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **PK** | — | Único, positivo (RUT chileno sin dígito verificador) | Identificador único del paciente. | `5234819` |
| 2 | `Nombre` | `TEXT` | `String` | `VARCHAR2(20)` | NO | — | — | Longitud lógica máx. 20 caracteres, no vacío | Primer nombre del paciente. | `Christian` |
| 3 | `ape_p` | `TEXT` | `String` | `VARCHAR2(20)` | NO | — | — | Longitud lógica máx. 20 caracteres, no vacío | Apellido paterno del paciente. | `Carriel` |
| 4 | `ape_m` | `TEXT` | `String` | `VARCHAR2(20)` | NO | — | — | Longitud lógica máx. 20 caracteres | Apellido materno del paciente. | `González` |
| 5 | `Fech_nac` | `TEXT` | `String` | `DATE` | NO | — | — | Formato ISO `YYYY-MM-DD` | Fecha de nacimiento del paciente. | `1948-05-14` |
| 6 | `edad` | `INTEGER` | `Int` | `NUMBER(3)` | NO | — | — | Entero positivo (64–88 en dataset semilla) | Edad actual del paciente en años. | `78` |
| 7 | `genero` | `TEXT` | `String` | `VARCHAR2(20)` | NO | — | — | Dominio: `Masculino`, `Femenino` | Género declarado del paciente. | `Masculino` |
| 8 | `estado` | `INTEGER` | `Boolean` | `BOOLEAN` | NO | — | `1` (`true`) | `1 = Activo`, `0 = Inactivo` | Control de eliminación lógica; los inactivos se excluyen de telemetría e informes. | `1` |

---

### 3.2 Tabla `Contacto_emergencia`
* **Descripción:** Registra a los familiares o cuidadores de referencia vinculados a cada paciente para emergencias y mensajería privada.
* **Clave Primaria:** `id_contacto` (`AUTOINCREMENT`).
* **Claves Foráneas:** `Paciente_Id_paciente` → `Paciente(Id_paciente)` (`ON DELETE CASCADE`).
* **Índices:** `index_Contacto_emergencia_Paciente_Id_paciente` en `(Paciente_Id_paciente)`.
* **Política de borrado:** Eliminación lógica desde la UI administrativa (`estado = 0`).

| N° | Campo | Tipo SQLite | Tipo Kotlin | Tipo Lógico | Nulo | Clave | Valor por defecto | Restricciones / Formato | Descripción | Ejemplo |
| :-: | :--- | :--- | :--- | :--- | :-: | :-: | :--- | :--- | :--- | :--- |
| 1 | `id_contacto` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **PK (AI)** | Autogenerado | Secuencial positivo | Identificador único del contacto familiar. | `1` |
| 2 | `nombre` | `TEXT` | `String` | `VARCHAR2(20)` | NO | — | — | Longitud lógica máx. 20 caracteres, no vacío | Nombre del familiar o cuidador. | `Claudia` |
| 3 | `apellido` | `TEXT` | `String` | `VARCHAR2(20)` | NO | — | — | Longitud lógica máx. 20 caracteres, no vacío | Apellido del familiar o cuidador. | `Pérez` |
| 4 | `numero_tel` | `INTEGER` | `Long` | `NUMBER(10)` | NO | — | — | 9 dígitos (móvil chileno `9XXXXXXXX`) | Número telefónico de contacto de emergencia. | `987654321` |
| 5 | `Paciente_Id_paciente` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **FK** | — | Debe existir en `Paciente(Id_paciente)` | RUT del paciente asociado al familiar. | `5234819` |
| 6 | `estado` | `INTEGER` | `Boolean` | `BOOLEAN` | NO | — | `1` (`true`) | `1 = Activo`, `0 = Inactivo` | Control de eliminación lógica del contacto. | `1` |

---

### 3.3 Tabla `recordatorios`
* **Descripción:** Persiste los recordatorios clínicos y de rutina diaria. Un subproceso diario carga en memoria RAM (`ReminderMemoryBuffer`) y programa en `AlarmManager` todos los registros cuya fecha corresponda al día en curso.
* **Clave Primaria:** `id_recordatorio` (`AUTOINCREMENT`).
* **Claves Foráneas:** `Paciente_Id_paciente` → `Paciente(Id_paciente)` (`ON DELETE CASCADE`).
* **Índices:** `index_recordatorios_Paciente_Id_paciente` en `(Paciente_Id_paciente)`.

| N° | Campo | Tipo SQLite | Tipo Kotlin | Tipo Lógico | Nulo | Clave | Valor por defecto | Restricciones / Formato | Descripción | Ejemplo |
| :-: | :--- | :--- | :--- | :--- | :-: | :-: | :--- | :--- | :--- | :--- |
| 1 | `id_recordatorio` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **PK (AI)** | Autogenerado | Secuencial positivo | Identificador único del recordatorio. | `1` |
| 2 | `descripcion` | `TEXT` | `String` | `VARCHAR2(255)` | NO | — | — | Texto no vacío | Mensaje o instrucción que S.A.R.A recordará al paciente. | `Tomar Losartán 50 mg después del almuerzo con un vaso de agua` |
| 3 | `fecha_recor` | `TEXT` | `String` | `TIMESTAMP / DATE` | NO | — | — | Formato `YYYY-MM-DD HH:mm:ss` (`DATE_TIME_FORMATTER`) | Fecha y hora exacta programada para el disparo de la alarma. | `2026-11-20 13:30:00` |
| 4 | `Paciente_Id_paciente` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **FK** | — | Debe existir en `Paciente(Id_paciente)` | RUT del paciente destinatario del recordatorio. | `5234819` |

---

### 3.4 Tabla `juegos`
* **Descripción:** Catálogo de juegos y trivias de estimulación cognitiva disponibles en S.A.R.A.
* **Clave Primaria:** `id_juego` (`AUTOINCREMENT`).
* **Claves Foráneas:** Ninguna.
* **Índices:** Índice implícito sobre `PRIMARY KEY (id_juego)`.

| N° | Campo | Tipo SQLite | Tipo Kotlin | Tipo Lógico | Nulo | Clave | Valor por defecto | Restricciones / Formato | Descripción | Ejemplo |
| :-: | :--- | :--- | :--- | :--- | :-: | :-: | :--- | :--- | :--- | :--- |
| 1 | `id_juego` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **PK (AI)** | Autogenerado | Secuencial positivo (`1..5` en catálogo base) | Identificador único del juego de estimulación. | `1` |
| 2 | `descripcion` | `TEXT` | `String` | `VARCHAR2(210)` | NO | — | — | Longitud lógica máx. 210 caracteres, no vacío | Nombre, dinámica, área cognitiva medida y parámetros del juego. | `Trivia de Época (Música, Cine e Historia): Preguntas de los años 60, 70 u 80 con 3 opciones. Mide memoria remota y semántica. Parámetros: % aciertos y tiempo de respuesta.` |

#### Catálogo Maestro Poblado (`juegos`)

| `id_juego` | Juego | Área Cognitiva Medida | Parámetros en `historial_estimulacion` |
| :-: | :--- | :--- | :--- |
| `1` | **Trivia de Época** *(Música, Cine e Historia)* | Memoria remota (largo plazo) y memoria semántica | Porcentaje de aciertos y tiempo de respuesta |
| `2` | **¿Quién soy?** *(Adivinanza por Pistas)* | Razonamiento deductivo y acceso al léxico | Cantidad de pistas necesitadas (3 / 2 / 1 pts) y fallos |
| `3` | **Mito o Realidad** *(Verdadero / Falso)* | Velocidad de procesamiento, atención y juicio lógico | Aciertos totales, racha máxima seguida y latencia (s) |
| `4` | **Termina la Canción** | Memoria auditiva, lenguaje y atención sostenida | Frases completadas correctamente, ayudas solicitadas y fluidez |
| `5` | **¿Qué Pasó Primero?** | Orientación temporal y función ejecutiva | Aciertos sobre total de rondas y tiempo de deliberación (s) |

---

### 3.5 Tabla `historial_estimulacion`
* **Descripción:** Registra cada sesión de estimulación cognitiva ejecutada por un paciente en uno de los juegos, almacenando fecha, puntuación numérica y observaciones detalladas con los parámetros medidos.
* **Clave Primaria:** `id_historial` (`AUTOINCREMENT`).
* **Claves Foráneas:**
  * `Paciente_Id_paciente` → `Paciente(Id_paciente)` (`ON DELETE CASCADE`)
  * `juegos_id_juego2` → `juegos(id_juego)` (`ON DELETE CASCADE`)
* **Índices:**
  * `index_historial_estimulacion_Paciente_Id_paciente` en `(Paciente_Id_paciente)`
  * `index_historial_estimulacion_juegos_id_juego2` en `(juegos_id_juego2)`

| N° | Campo | Tipo SQLite | Tipo Kotlin | Tipo Lógico | Nulo | Clave | Valor por defecto | Restricciones / Formato | Descripción | Ejemplo |
| :-: | :--- | :--- | :--- | :--- | :-: | :-: | :--- | :--- | :--- | :--- |
| 1 | `id_historial` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **PK (AI)** | Autogenerado | Secuencial positivo | Identificador único del registro de sesión. | `5` |
| 2 | `fecha_rea` | `TEXT` | `String` | `TIMESTAMP / DATE` | NO | — | — | Formato `YYYY-MM-DD HH:mm:ss` | Fecha y hora en que se realizó la sesión de estimulación. | `2026-11-06 14:30:00` |
| 3 | `puntuacion` | `INTEGER` | `Int` | `NUMBER(4)` | NO | — | — | Rango normalizado `0..100` | Puntuación alcanzada por el paciente en la sesión. | `90` |
| 4 | `observaciones` | `TEXT` | `String` | `VARCHAR2(420)` | NO | — | — | Longitud lógica máx. 420 caracteres | Resumen de métricas específicas del juego y notas de desempeño cognitivo. | `Aciertos: 9/10 (90%). Tiempo promedio de respuesta: 10.8 s. Temática: cine de oro y eventos deportivos de los 60-80. Excelente evocación de memoria remota y semántica.` |
| 5 | `Paciente_Id_paciente` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **FK** | — | Debe existir en `Paciente(Id_paciente)` | RUT del paciente que realizó la actividad. | `4812345` |
| 6 | `juegos_id_juego2` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **FK** | — | Debe existir en `juegos(id_juego)` | Identificador del juego ejecutado en la sesión. | `1` |

---

### 3.6 Tabla `chat`
* **Descripción:** Almacena la mensajería privada local intercambiada entre el paciente (vía dictado de voz en S.A.R.A) y sus contactos familiares (vía interfaz de texto).
* **Clave Primaria:** `id_chat` (`AUTOINCREMENT`).
* **Claves Foráneas:**
  * `Paciente_id_paciente` → `Paciente(Id_paciente)` (`ON DELETE CASCADE`)
  * `Contacto_emergencia_id_contacto` → `Contacto_emergencia(id_contacto)` (`ON DELETE CASCADE`)
* **Índices:**
  * `index_chat_Paciente_id_paciente` en `(Paciente_id_paciente)`
  * `index_chat_Contacto_emergencia_id_contacto` en `(Contacto_emergencia_id_contacto)`

| N° | Campo | Tipo SQLite | Tipo Kotlin | Tipo Lógico | Nulo | Clave | Valor por defecto | Restricciones / Formato | Descripción | Ejemplo |
| :-: | :--- | :--- | :--- | :--- | :-: | :-: | :--- | :--- | :--- | :--- |
| 1 | `id_chat` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **PK (AI)** | Autogenerado | Secuencial positivo | Identificador único del mensaje. | `1` |
| 2 | `emisor` | `TEXT` | `String` | `VARCHAR2(15)` | NO | — | — | Dominio cerrado: `"PACIENTE"` o `"FAMILIAR"` | Rol de quien originó el mensaje. | `FAMILIAR` |
| 3 | `mensaje` | `TEXT` | `String` | `CLOB / TEXT` | NO | — | — | Sin límite fijo de 200 caracteres, no vacío | Cuerpo textual del mensaje enviado o dictado. | `Hola papá, paso a verte hoy a las 6 de la tarde.` |
| 4 | `fecha_hora` | `INTEGER` | `Long` | `TIMESTAMP` | NO | — | `System.currentTimeMillis()` | Epoch Unix en milisegundos | Marca temporal exacta del envío del mensaje. | `1795180200000` |
| 5 | `leido` | `INTEGER` | `Boolean` | `BOOLEAN` | NO | — | `0` (`false`) | `0 = Pendiente`, `1 = Leído` | Indica si S.A.R.A ya leyó el mensaje al paciente (o si lo envió el propio paciente). | `0` |
| 6 | `Paciente_id_paciente` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **FK** | — | Debe existir en `Paciente(Id_paciente)` | RUT del paciente participante en el chat. | `5234819` |
| 7 | `Contacto_emergencia_id_contacto` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **FK** | — | Debe existir en `Contacto_emergencia(id_contacto)` | Identificador del familiar participante en el chat. | `1` |

---

### 3.7 Tabla `biometria_paciente`
* **Descripción:** Guarda el vector biométrico facial (192 dimensiones generado por MobileFaceNet) y la constancia auditable de consentimiento informado, en relación estricta 1:1 con `Paciente`.
* **Clave Primaria:** `id_biometria` (`AUTOINCREMENT`).
* **Claves Foráneas:** `id_paciente` → `Paciente(Id_paciente)` (`ON DELETE CASCADE`).
* **Índices:** `index_biometria_paciente_id_paciente` (**UNIQUE**) en `(id_paciente)`.

| N° | Campo | Tipo SQLite | Tipo Kotlin | Tipo Lógico | Nulo | Clave | Valor por defecto | Restricciones / Formato | Descripción | Ejemplo |
| :-: | :--- | :--- | :--- | :--- | :-: | :-: | :--- | :--- | :--- | :--- |
| 1 | `id_biometria` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **PK (AI)** | Autogenerado | Secuencial positivo | Identificador único del registro biométrico. | `1` |
| 2 | `id_paciente` | `INTEGER` | `Long` | `NUMERIC(10)` | NO | **FK, UK** | — | Único por paciente; debe existir en `Paciente(Id_paciente)` | RUT del paciente dueño del perfil facial (garantiza relación 1:1). | `5234819` |
| 3 | `vector_biometrico` | `TEXT` | `String` | `CLOB / TEXT` | NO | — | — | 192 números flotantes separados por coma (CSV) | Embedding facial normalizado extraído por `mobilefacenet.tflite`. | `0.0412,-0.1185,0.0923,...` *(192 floats)* |
| 4 | `consentimiento_otorgado` | `INTEGER` | `Boolean` | `BOOLEAN` | NO | — | `1` (`true`) | `1 = Otorgado`, `0 = No otorgado` | Registro auditable de consentimiento informado para biometría facial. | `1` |
| 5 | `fecha_registro` | `INTEGER` | `Long` | `TIMESTAMP` | NO | — | `System.currentTimeMillis()` | Epoch Unix en milisegundos | Marca temporal en que se enroló el rostro del paciente. | `1795180200000` |

---

### 3.8 Tabla de Metadatos del Motor: `room_master_table` *(Interna)*
* **Descripción:** Tabla de control generada y consultada automáticamente por Android Room para verificar que el esquema físico de SQLite coincide con la versión compilada de `SaraAppDatabase`.

| N° | Campo | Tipo SQLite | Nulo | Clave | Descripción | Valor actual |
| :-: | :--- | :--- | :-: | :-: | :--- | :--- |
| 1 | `id` | `INTEGER` | SÍ | **PK** | Constante de fila maestra de Room (`42`). | `42` |
| 2 | `identity_hash` | `TEXT` | SÍ | — | Hash MD5 del esquema Room v4 generado por KSP. | `c156ccccc4ec2172821d7fb19d0ba657` |
