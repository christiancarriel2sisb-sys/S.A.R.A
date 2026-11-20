package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Paciente")
data class PacienteEntity(
    @PrimaryKey(autoGenerate = false)
    val Id_paciente: Long,         // RUT numérico del paciente (Primary Key)
    val Nombre: String,            // VARCHAR2 (20)
    val ape_p: String,             // VARCHAR2 (20)
    val ape_m: String,             // VARCHAR2 (20)
    val Fech_nac: String,          // DATE (Almacenado como texto ISO YYYY-MM-DD)
    val edad: Int,                 // NUMBER
    val genero: String,            // VARCHAR2
    val estado: Boolean = true     // true = Activo, false = Inactivo (Eliminado lógico; no se usa en telemetría/informes)
)
