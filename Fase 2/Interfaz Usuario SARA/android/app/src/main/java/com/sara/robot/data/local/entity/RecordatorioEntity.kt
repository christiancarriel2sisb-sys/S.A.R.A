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
