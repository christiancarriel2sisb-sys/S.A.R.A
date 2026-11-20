package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad física de la tabla 'chat' según el Modelo Entidad-Relación (MER).
 * Almacena los mensajes privados entre un Paciente y sus Familiares/Contactos de emergencia.
 *
 * Mejoras técnicas aplicadas:
 * - id_chat: Long autoincremental administrado por SQLite.
 * - mensaje: TEXT sin límite de 200 caracteres para permitir mensajes extensos por voz o texto.
 * - leido: Boolean (0 o 1) para consultas reactivas instantáneas.
 */
@Entity(
    tableName = "chat",
    indices = [
        Index(value = ["Paciente_id_paciente"]),
        Index(value = ["Contacto_emergencia_id_contacto"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = PacienteEntity::class,
            parentColumns = ["Id_paciente"],
            childColumns = ["Paciente_id_paciente"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContactoEmergenciaEntity::class,
            parentColumns = ["id_contacto"],
            childColumns = ["Contacto_emergencia_id_contacto"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id_chat: Long = 0,                             // Primary Key Autogenerada
    val emisor: String,                                // "PACIENTE" o "FAMILIAR"
    val mensaje: String,                               // Contenido del mensaje (TEXT)
    val fecha_hora: Long = System.currentTimeMillis(), // TIMESTAMP en milisegundos
    val leido: Boolean = false,                        // true = Leído, false = Pendiente de lectura
    val Paciente_id_paciente: Long,                    // Foreign Key -> Paciente
    val Contacto_emergencia_id_contacto: Long          // Foreign Key -> Contacto_emergencia
)
