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

    @Query("SELECT * FROM Contacto_emergencia")
    fun getAllContactos(): Flow<List<ContactoEmergenciaEntity>>

    @Query("SELECT * FROM Contacto_emergencia WHERE LOWER(nombre) LIKE '%' || LOWER(:query) || '%' LIMIT 1")
    suspend fun findContactoByName(query: String): ContactoEmergenciaEntity?

    @Query("SELECT * FROM Contacto_emergencia WHERE id_contacto = :id LIMIT 1")
    suspend fun getContactoById(id: Long): ContactoEmergenciaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacto(contacto: ContactoEmergenciaEntity)

    @Update
    suspend fun updateContacto(contacto: ContactoEmergenciaEntity)

    @Delete
    suspend fun deleteContacto(contacto: ContactoEmergenciaEntity)
}
