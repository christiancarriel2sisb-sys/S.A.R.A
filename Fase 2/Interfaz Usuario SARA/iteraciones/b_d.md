# Guía Completa: Creación y Configuración de Base de Datos Local (Room / SQLite)

**Proyecto:** Robot Sara  
**Módulo:** Persistencia Local (Semana 2)  
**Objetivo:** Establecer la infraestructura de base de datos local en Android utilizando Room, abarcando desde las dependencias hasta la definición de tablas con sus respectivos tipos de datos y operaciones CRUD.

---

## 1. Instalación y Dependencias (`build.gradle.kts`)

Para utilizar Room en tu proyecto con Kotlin y soportar el procesamiento de anotaciones, agrega las siguientes líneas en el archivo `build.gradle.kts` a nivel de módulo (`app`):

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") // Procesador de anotaciones requerido por Room
}

android {
    namespace = "com.sara.robot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sara.robot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Dependencias principales de Room y soporte para Corrutinas (suspend/Flow)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}
```

---

## 2. Creación de Entidades y Tipos de Datos (Modelo Entidad-Relación)

Las clases de entidad representan las tablas físicas en SQLite. Los tipos de datos se mapean directamente de Kotlin a tipos compatibles con SQL (ej. `Long` a `INTEGER`, `String` a `TEXT`, `Int` a `INTEGER`).

### 2.1 Tabla: `Paciente`
```kotlin
package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Paciente")
data class PacienteEntity(
    @PrimaryKey(autoGenerate = false)
    val Id_paciente: Long,         // NUMERIC (10) -> Primary Key
    val Nombre: String,            // VARCHAR2 (20)
    val ape_p: String,             // VARCHAR2 (20)
    val ape_m: String,             // VARCHAR2 (20)
    val Fech_nac: String,          // DATE (Almacenado como texto ISO o timestamp)
    val edad: Int,                 // NUMBER
    val genero: String             // VARCHAR2
)
```

### 2.2 Tabla: `recordatorios`
```kotlin
package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recordatorios",
    indices = [Index(value = ["Paciente_Id_paciente"])],
    foreignKeys = [
        ForeignKey(
            entity = PacienteEntity::class,
            parentColumns = ["Id_paciente"],
            childColumns = ["Paciente_Id_paciente"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecordatorioEntity(
    @PrimaryKey(autoGenerate = true)
    val id_recordatorio: Long = 0, // NUMERIC -> Primary Key Auto-generada
    val descripcion: String,       // VARCHAR
    val fecha_recor: String,       // DATE
    val Paciente_Id_paciente: Long // NUMERIC -> Foreign Key referenciando a Paciente
)
```

### 2.3 Tabla: `Contacto_emergencia`
```kotlin
package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Contacto_emergencia",
    indices = [Index(value = ["Paciente_Id_paciente"])],
    foreignKeys = [
        ForeignKey(
            entity = PacienteEntity::class,
            parentColumns = ["Id_paciente"],
            childColumns = ["Paciente_Id_paciente"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ContactoEmergenciaEntity(
    @PrimaryKey(autoGenerate = true)
    val id_contacto: Long = 0,     // NUMERIC -> Primary Key Auto-generada
    val nombre: String,            // VARCHAR2 (20)
    val apellido: String,          // VARCHAR2 (20)
    val numero_tel: Long,          // NUMBER (10)
    val Paciente_Id_paciente: Long // NUMERIC -> Foreign Key
)
```

### 2.4 Tabla: `juegos`
```kotlin
package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "juegos")
data class JuegoEntity(
    @PrimaryKey(autoGenerate = true)
    val id_juego: Long = 0,       // NUMERIC (10) -> Primary Key
    val descripcion: String       // VARCHAR2 (210)
)
```

### 2.5 Tabla: `historial_estimulacion`
```kotlin
package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "historial_estimulacion",
    indices = [
        Index(value = ["Paciente_Id_paciente"]),
        Index(value = ["juegos_id_juego2"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = PacienteEntity::class,
            parentColumns = ["Id_paciente"],
            childColumns = ["Paciente_Id_paciente"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = JuegoEntity::class,
            parentColumns = ["id_juego"],
            childColumns = ["juegos_id_juego2"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HistorialEstimulacionEntity(
    @PrimaryKey(autoGenerate = true)
    val id_historial: Long = 0,    // NUMERIC (10) -> Primary Key
    val fecha_rea: String,         // DATE
    val puntuacion: Int,           // NUMBER (4)
    val observaciones: String,     // VARCHAR2 (420)
    val Paciente_Id_paciente: Long,// NUMERIC (10) -> FK Paciente
    val juegos_id_juego2: Long     // NUMERIC (10) -> FK Juego
)
```

---

## 3. Interfaces DAO (Métodos Insert, Delete, Update, Queries)

Definen las operaciones lógicas de acceso a la base de datos de manera asíncrona mediante corrutinas de Kotlin.

### 3.1 `PacienteDao.kt`
```kotlin
package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sara.robot.data.local.entity.PacienteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PacienteDao {
    @Query("SELECT * FROM Paciente WHERE Id_paciente = :id")
    suspend fun getPacienteById(id: Long): PacienteEntity?

    @Query("SELECT * FROM Paciente")
    fun getAllPacientes(): Flow<List<PacienteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaciente(paciente: PacienteEntity)

    @Update
    suspend fun updatePaciente(paciente: PacienteEntity)

    @Delete
    suspend fun deletePaciente(paciente: PacienteEntity)
}
```

### 3.2 `RecordatorioDao.kt`
```kotlin
package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sara.robot.data.local.entity.RecordatorioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordatorioDao {
    @Query("SELECT * FROM recordatorios WHERE Paciente_Id_paciente = :pacienteId ORDER BY fecha_recor ASC")
    fun getRecordatoriosByPaciente(pacienteId: Long): Flow<List<RecordatorioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordatorio(recordatorio: RecordatorioEntity)

    @Update
    suspend fun updateRecordatorio(recordatorio: RecordatorioEntity)

    @Delete
    suspend fun deleteRecordatorio(recordatorio: RecordatorioEntity)
}
```

### 3.3 `ContactoEmergenciaDao.kt`
```kotlin
package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sara.robot.data.local.entity.ContactoEmergenciaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactoEmergenciaDao {
    @Query("SELECT * FROM Contacto_emergencia WHERE Paciente_Id_paciente = :pacienteId")
    fun getContactosByPaciente(pacienteId: Long): Flow<List<ContactoEmergenciaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacto(contacto: ContactoEmergenciaEntity)

    @Update
    suspend fun updateContacto(contacto: ContactoEmergenciaEntity)

    @Delete
    suspend fun deleteContacto(contacto: ContactoEmergenciaEntity)
}
```

---

## 4. Configuración Principal de la Base de Datos (`SaraAppDatabase.kt`)

Clase abstracta central que instancia la base de datos local SQLite bajo el patrón Singleton.

```kotlin
package com.sara.robot.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sara.robot.data.local.dao.ContactoEmergenciaDao
import com.sara.robot.data.local.dao.PacienteDao
import com.sara.robot.data.local.dao.RecordatorioDao
import com.sara.robot.data.local.entity.*

@Database(
    entities = [
        PacienteEntity::class,
        RecordatorioEntity::class,
        ContactoEmergenciaEntity::class,
        JuegoEntity::class,
        HistorialEstimulacionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SaraAppDatabase : RoomDatabase() {

    abstract fun pacienteDao(): PacienteDao
    abstract fun recordatorioDao(): RecordatorioDao
    abstract fun contactoEmergenciaDao(): ContactoEmergenciaDao

    companion object {
        @Volatile
        private var INSTANCE: SaraAppDatabase? = null

        fun getDatabase(context: Context): SaraAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaraAppDatabase::class.java,
                    "sara_local_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```