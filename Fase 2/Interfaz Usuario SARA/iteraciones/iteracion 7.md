# Iteración 7: Creación de la Infraestructura de Base de Datos Local (Room / SQLite)

**Proyecto:** S.A.R.A - Sistema Asistente Robótico de Apoyo  
**Autor de la Petición:** Christian Carriel  
**Fecha:** Noviembre 2026 (Fase 2)  
**Estado:** COMPLETADO Y VERIFICADO  

---

## 1. Petición del Usuario

> *"Hola amigo, quiero que por favor leas el .md que está en iteraciones que se llama 'b_d', ese tiene la configuración de la base de datos, y quiero que la crees. Cuando termines solo déjame ver la estructura de las tablas y los tipos de datos de cada atributo de las tablas para verificar que se crearon bien, buena suerte mi pana."*

### Requisitos Específicos:
1. **Lectura y Análisis de `b_d.md`:** Interpretar la especificación del modelo entidad-relación y dependencias de Room.
2. **Configuración de Dependencias Gradle:** Integrar Google KSP (`com.google.devtools.ksp:1.9.23-1.0.20`) y las librerías oficiales de Room Runtime, Room KTX y Room Compiler (`2.6.1`).
3. **Creación de Entidades y Modelos de Datos:** Implementar las 5 entidades físicas de persistencia (`Paciente`, `recordatorios`, `Contacto_emergencia`, `juegos`, `historial_estimulacion`).
4. **Creación de Interfaces DAO:** Definir los métodos CRUD asíncronos con soporte a Corrutinas (`suspend`) y flujos reactivos (`Flow`).
5. **Configuración de `SaraAppDatabase`:** Crear la base de datos abstracta de Room bajo el patrón Singleton (`sara_local_db`).
6. **Verificación y Extracción de Esquema SQLite:** Generar y validar la estructura física de tablas, tipos de datos, llaves primarias, llaves foráneas e índices.

---

## 2. Implementación Técnica Paso a Paso

### Paso 1: Configuración de KSP y Room en Gradle
Se configuró en el archivo raíz `build.gradle.kts` el plugin de procesamiento de anotaciones KSP compatible con Kotlin 1.9.23:
```kotlin
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
}
```
Y en `app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

dependencies {
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}
```

---

### Paso 2: Entidades Creadas (`com.sara.robot.data.local.entity`)

1. **`PacienteEntity.kt` (Tabla: `Paciente`):**
   * `Id_paciente` (Long, PK)
   * `Nombre` (String)
   * `ape_p` (String)
   * `ape_m` (String)
   * `Fech_nac` (String)
   * `edad` (Int)
   * `genero` (String)

2. **`RecordatorioEntity.kt` (Tabla: `recordatorios`):**
   * `id_recordatorio` (Long, PK autogenerada)
   * `descripcion` (String)
   * `fecha_recor` (String)
   * `Paciente_Id_paciente` (Long, FK referenciando a `Paciente(Id_paciente)` con `ON DELETE CASCADE`)
   * Índice en `Paciente_Id_paciente`.

3. **`ContactoEmergenciaEntity.kt` (Tabla: `Contacto_emergencia`):**
   * `id_contacto` (Long, PK autogenerada)
   * `nombre` (String)
   * `apellido` (String)
   * `numero_tel` (Long)
   * `Paciente_Id_paciente` (Long, FK referenciando a `Paciente(Id_paciente)` con `ON DELETE CASCADE`)
   * Índice en `Paciente_Id_paciente`.

4. **`JuegoEntity.kt` (Tabla: `juegos`):**
   * `id_juego` (Long, PK autogenerada)
   * `descripcion` (String)

5. **`HistorialEstimulacionEntity.kt` (Tabla: `historial_estimulacion`):**
   * `id_historial` (Long, PK autogenerada)
   * `fecha_rea` (String)
   * `puntuacion` (Int)
   * `observaciones` (String)
   * `Paciente_Id_paciente` (Long, FK referenciando a `Paciente(Id_paciente)` con `ON DELETE CASCADE`)
   * `juegos_id_juego2` (Long, FK referenciando a `juegos(id_juego)` con `ON DELETE CASCADE`)
   * Índices en `Paciente_Id_paciente` y `juegos_id_juego2`.

---

### Paso 3: Interfaces DAO (`com.sara.robot.data.local.dao`)

* **`PacienteDao.kt`:** Consultas por ID, lista completa en `Flow`, inserción (`REPLACE`), actualización y borrado.
* **`RecordatorioDao.kt`:** Obtención ordenada cronológicamente de recordatorios por paciente, inserción, actualización y borrado.
* **`ContactoEmergenciaDao.kt`:** Consulta de contactos de emergencia asociados a un paciente, inserción, actualización y borrado.
* **`JuegoDao.kt`:** Obtención de juegos cognitivos disponibles, inserción y mantenimiento.
* **`HistorialEstimulacionDao.kt`:** Registro de sesiones de estimulación cognitiva, puntuación obtenida y observaciones clínicas.

---

### Paso 4: Base de Datos Central (`com.sara.robot.data.local.database.SaraAppDatabase`)

Clase abstracta Singleton anotada con `@Database` que reúne las 5 entidades físicas y expone los 5 DAOs. Crea la base de datos SQLite bajo el identificador físico `"sara_local_db"`.

---

## 3. Estructura de las Tablas y Tipos de Datos (Verificación SQLite)

### 3.1 Tabla: `Paciente`
| N° | Columna / Atributo | Tipo SQLite | Tipo Kotlin | Nullable | Llave Primaria |
| :-: | :--- | :--- | :--- | :-: | :-: |
| 0 | `Id_paciente` | **INTEGER** | `Long` | NOT NULL | **PRIMARY KEY** |
| 1 | `Nombre` | **TEXT** | `String` | NOT NULL | No |
| 2 | `ape_p` | **TEXT** | `String` | NOT NULL | No |
| 3 | `ape_m` | **TEXT** | `String` | NOT NULL | No |
| 4 | `Fech_nac` | **TEXT** | `String` | NOT NULL | No |
| 5 | `edad` | **INTEGER** | `Int` | NOT NULL | No |
| 6 | `genero` | **TEXT** | `String` | NOT NULL | No |

---

### 3.2 Tabla: `recordatorios`
| N° | Columna / Atributo | Tipo SQLite | Tipo Kotlin | Nullable | Llave Primaria | Llave Foránea |
| :-: | :--- | :--- | :--- | :-: | :-: | :--- |
| 0 | `id_recordatorio` | **INTEGER** | `Long` | NOT NULL | **PK (AUTOINCREMENT)** | - |
| 1 | `descripcion` | **TEXT** | `String` | NOT NULL | No | - |
| 2 | `fecha_recor` | **TEXT** | `String` | NOT NULL | No | - |
| 3 | `Paciente_Id_paciente` | **INTEGER** | `Long` | NOT NULL | No | **FK ➔ `Paciente(Id_paciente)`** [CASCADE] |

* **Índices:** `index_recordatorios_Paciente_Id_paciente` en `(Paciente_Id_paciente)`.

---

### 3.3 Tabla: `Contacto_emergencia`
| N° | Columna / Atributo | Tipo SQLite | Tipo Kotlin | Nullable | Llave Primaria | Llave Foránea |
| :-: | :--- | :--- | :--- | :-: | :-: | :--- |
| 0 | `id_contacto` | **INTEGER** | `Long` | NOT NULL | **PK (AUTOINCREMENT)** | - |
| 1 | `nombre` | **TEXT** | `String` | NOT NULL | No | - |
| 2 | `apellido` | **TEXT** | `String` | NOT NULL | No | - |
| 3 | `numero_tel` | **INTEGER** | `Long` | NOT NULL | No | - |
| 4 | `Paciente_Id_paciente` | **INTEGER** | `Long` | NOT NULL | No | **FK ➔ `Paciente(Id_paciente)`** [CASCADE] |

* **Índices:** `index_Contacto_emergencia_Paciente_Id_paciente` en `(Paciente_Id_paciente)`.

---

### 3.4 Tabla: `juegos`
| N° | Columna / Atributo | Tipo SQLite | Tipo Kotlin | Nullable | Llave Primaria |
| :-: | :--- | :--- | :--- | :-: | :-: |
| 0 | `id_juego` | **INTEGER** | `Long` | NOT NULL | **PK (AUTOINCREMENT)** |
| 1 | `descripcion` | **TEXT** | `String` | NOT NULL | No |

---

### 3.5 Tabla: `historial_estimulacion`
| N° | Columna / Atributo | Tipo SQLite | Tipo Kotlin | Nullable | Llave Primaria | Llave Foránea |
| :-: | :--- | :--- | :--- | :-: | :-: | :--- |
| 0 | `id_historial` | **INTEGER** | `Long` | NOT NULL | **PK (AUTOINCREMENT)** | - |
| 1 | `fecha_rea` | **TEXT** | `String` | NOT NULL | No | - |
| 2 | `puntuacion` | **INTEGER** | `Int` | NOT NULL | No | - |
| 3 | `observaciones` | **TEXT** | `String` | NOT NULL | No | - |
| 4 | `Paciente_Id_paciente` | **INTEGER** | `Long` | NOT NULL | No | **FK ➔ `Paciente(Id_paciente)`** [CASCADE] |
| 5 | `juegos_id_juego2` | **INTEGER** | `Long` | NOT NULL | No | **FK ➔ `juegos(id_juego)`** [CASCADE] |

* **Índices:**
  * `index_historial_estimulacion_Paciente_Id_paciente` en `(Paciente_Id_paciente)`.
  * `index_historial_estimulacion_juegos_id_juego2` en `(juegos_id_juego2)`.

---

## 4. Archivos Creados y Modificados

| Archivo | Tipo | Descripción |
| :--- | :--- | :--- |
| `android/build.gradle.kts` | Modificado | Declaración del plugin Google KSP 1.9.23-1.0.20. |
| `android/app/build.gradle.kts` | Modificado | Aplicación del plugin KSP y dependencias de Room 2.6.1. |
| `entity/PacienteEntity.kt` | Creado | Entidad Room para la tabla `Paciente`. |
| `entity/RecordatorioEntity.kt` | Creado | Entidad Room para la tabla `recordatorios`. |
| `entity/ContactoEmergenciaEntity.kt` | Creado | Entidad Room para la tabla `Contacto_emergencia`. |
| `entity/JuegoEntity.kt` | Creado | Entidad Room para la tabla `juegos`. |
| `entity/HistorialEstimulacionEntity.kt` | Creado | Entidad Room para la tabla `historial_estimulacion`. |
| `dao/PacienteDao.kt` | Creado | DAO para gestión de pacientes. |
| `dao/RecordatorioDao.kt` | Creado | DAO para gestión de recordatorios clínicos. |
| `dao/ContactoEmergenciaDao.kt` | Creado | DAO para contactos de emergencia. |
| `dao/JuegoDao.kt` | Creado | DAO para juegos cognitivos. |
| `dao/HistorialEstimulacionDao.kt` | Creado | DAO para métricas de estimulación. |
| `database/SaraAppDatabase.kt` | Creado | Base de datos Room abstracta Singleton (`sara_local_db`). |
| `android/sara_local_db.db` | Creado | Base de datos SQLite física validada. |
| `iteraciones/iteracion 7.md` | Creado | Documentación de la iteración y estructura de tablas. |

---

## 5. Validación y Compilación
* **Procesamiento de Anotaciones:** KSP generó exitosamente los adaptadores y tablas de Room (`SaraAppDatabase_Impl`).
* **Compilación de Gradle:** **`BUILD SUCCESSFUL in 2m 26s`** (40 tareas ejecutadas limpiamente sin errores de sintaxis ni de tipos).
