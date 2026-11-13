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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordatorio(recordatorio: RecordatorioEntity)

    @Update
    suspend fun updateRecordatorio(recordatorio: RecordatorioEntity)

    @Delete
    suspend fun deleteRecordatorio(recordatorio: RecordatorioEntity)
}
