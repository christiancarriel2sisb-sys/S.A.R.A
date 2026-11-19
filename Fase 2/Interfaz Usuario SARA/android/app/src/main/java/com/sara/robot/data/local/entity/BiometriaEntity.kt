package com.sara.robot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de Información Biométrica de Pacientes (1 a 1 con Paciente).
 * - Hereda y se vincula a la clave primaria de Paciente (Id_paciente).
 * - El índice unique en id_paciente garantiza una relación estricta 1:1.
 * - Almacena el embedding facial de 192 dimensiones generado por MobileFaceNet,
 *   junto con el registro auditable de consentimiento informado.
 */
@Entity(
    tableName = "biometria_paciente",
    foreignKeys = [
        ForeignKey(
            entity = PacienteEntity::class,
            parentColumns = ["Id_paciente"],
            childColumns = ["id_paciente"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id_paciente"], unique = true)
    ]
)
data class BiometriaEntity(
    @PrimaryKey(autoGenerate = true)
    val id_biometria: Long = 0L,

    @ColumnInfo(name = "id_paciente")
    val id_paciente: Long,

    @ColumnInfo(name = "vector_biometrico")
    val vector_biometrico: String, // 192 floats serializados en formato CSV

    @ColumnInfo(name = "consentimiento_otorgado")
    val consentimiento_otorgado: Boolean = true,

    @ColumnInfo(name = "fecha_registro")
    val fecha_registro: Long = System.currentTimeMillis()
)
