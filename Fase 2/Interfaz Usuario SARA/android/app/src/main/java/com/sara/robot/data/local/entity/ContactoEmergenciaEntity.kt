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
