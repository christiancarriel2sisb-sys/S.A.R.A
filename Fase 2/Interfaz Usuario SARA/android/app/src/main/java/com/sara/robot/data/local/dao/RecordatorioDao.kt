package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sara.robot.data.local.entity.RecordatorioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordatorioDao {
    @Query("SELECT * FROM recordatorios WHERE Paciente_Id_paciente = :pacienteId ORDER BY fecha_recor ASC")
    fun getRecordatoriosByPaciente(pacienteId: Long): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios ORDER BY fecha_recor ASC")
    fun getAllRecordatorios(): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios ORDER BY fecha_recor ASC")
    suspend fun getAllRecordatoriosDirecto(): List<RecordatorioEntity>

    @Query("SELECT * FROM recordatorios WHERE fecha_recor LIKE :datePrefix || '%' ORDER BY fecha_recor ASC")
    suspend fun getRecordatoriosDelDia(datePrefix: String): List<RecordatorioEntity>

    @Query("SELECT * FROM recordatorios WHERE fecha_recor LIKE :datePrefix || '%' ORDER BY fecha_recor ASC")
    fun getRecordatoriosDelDiaFlow(datePrefix: String): Flow<List<RecordatorioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordatorio(recordatorio: RecordatorioEntity): Long

    @Update
    suspend fun updateRecordatorio(recordatorio: RecordatorioEntity)

    @Delete
    suspend fun deleteRecordatorio(recordatorio: RecordatorioEntity)

    @Query("DELETE FROM recordatorios WHERE id_recordatorio = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recordatorios")
    suspend fun deleteAllRecordatorios()
}
