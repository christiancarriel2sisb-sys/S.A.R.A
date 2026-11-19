package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sara.robot.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para la tabla 'chat'.
 * Provee operaciones asíncronas reactivas (Flow / Corrutinas) para el chat privado
 * entre el Paciente y sus Familiares.
 */
@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMensaje(mensaje: ChatEntity): Long

    @Query("SELECT * FROM chat WHERE Contacto_emergencia_id_contacto = :contactoId ORDER BY fecha_hora ASC")
    fun getMensajesPorContacto(contactoId: Long): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat WHERE leido = 0 AND emisor = 'FAMILIAR' ORDER BY fecha_hora ASC")
    fun getMensajesNoLeidosParaPaciente(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat WHERE leido = 0 AND emisor = 'FAMILIAR' ORDER BY fecha_hora ASC")
    suspend fun getMensajesNoLeidosParaPacienteDirecto(): List<ChatEntity>

    @Query("SELECT * FROM chat WHERE Contacto_emergencia_id_contacto = :contactoId ORDER BY fecha_hora DESC LIMIT 1")
    fun getUltimoMensajePorContacto(contactoId: Long): Flow<ChatEntity?>

    @Query("UPDATE chat SET leido = 1 WHERE id_chat = :idChat")
    suspend fun marcarComoLeido(idChat: Long)

    @Query("UPDATE chat SET leido = 1 WHERE Contacto_emergencia_id_contacto = :contactoId AND emisor = 'FAMILIAR'")
    suspend fun marcarTodosComoLeidosPorContacto(contactoId: Long)

    @Query("SELECT * FROM chat ORDER BY fecha_hora DESC")
    fun getAllMensajes(): Flow<List<ChatEntity>>

    @Query("DELETE FROM chat WHERE Contacto_emergencia_id_contacto = :contactoId")
    suspend fun eliminarHistorialPorContacto(contactoId: Long)
}
