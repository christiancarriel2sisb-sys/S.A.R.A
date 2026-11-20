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
