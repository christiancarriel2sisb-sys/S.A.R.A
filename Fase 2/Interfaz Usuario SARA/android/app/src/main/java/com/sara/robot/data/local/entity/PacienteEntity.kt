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
