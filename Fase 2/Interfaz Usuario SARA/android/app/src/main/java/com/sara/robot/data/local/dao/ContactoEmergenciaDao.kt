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
    @Query("SELECT * FROM Contacto_emergencia WHERE Paciente_Id_paciente = :pacienteId AND estado = 1 ORDER BY nombre ASC")
    fun getContactosByPaciente(pacienteId: Long): Flow<List<ContactoEmergenciaEntity>>

    @Query(
        """
        SELECT c.* FROM Contacto_emergencia c
        WHERE c.estado = 1
        ORDER BY (
            SELECT MAX(ch.fecha_hora) FROM chat ch
            WHERE ch.Contacto_emergencia_id_contacto = c.id_contacto
        ) DESC, c.id_contacto ASC
        """
    )
    fun getAllContactos(): Flow<List<ContactoEmergenciaEntity>>

    @Query("SELECT * FROM Contacto_emergencia WHERE estado = 1 ORDER BY id_contacto ASC")
    suspend fun getAllContactosDirecto(): List<ContactoEmergenciaEntity>

    @Query("SELECT * FROM Contacto_emergencia WHERE estado = 1 AND LOWER(nombre) LIKE '%' || LOWER(:query) || '%' LIMIT 1")
    suspend fun findContactoByName(query: String): ContactoEmergenciaEntity?

    @Query("SELECT * FROM Contacto_emergencia WHERE id_contacto = :id AND estado = 1 LIMIT 1")
    suspend fun getContactoById(id: Long): ContactoEmergenciaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacto(contacto: ContactoEmergenciaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contactos: List<ContactoEmergenciaEntity>)

    @Query("SELECT COUNT(*) FROM Contacto_emergencia")
    suspend fun getContactoCount(): Int

    @Update
    suspend fun updateContacto(contacto: ContactoEmergenciaEntity)

    /**
     * Eliminación lógica: nunca borra el contacto de la BD, solo cambia su estado a inactivo (false = 0).
     */
    @Query("UPDATE Contacto_emergencia SET estado = 0 WHERE id_contacto = :idContacto")
    suspend fun desactivarContacto(idContacto: Long)

    @Delete
    suspend fun deleteContacto(contacto: ContactoEmergenciaEntity)
}
