package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sara.robot.data.local.entity.HistorialEstimulacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistorialEstimulacionDao {
    @Query("SELECT * FROM historial_estimulacion WHERE Paciente_Id_paciente = :pacienteId ORDER BY fecha_rea DESC")
    fun getHistorialByPaciente(pacienteId: Long): Flow<List<HistorialEstimulacionEntity>>

    @Query("SELECT * FROM historial_estimulacion WHERE id_historial = :id")
    suspend fun getHistorialById(id: Long): HistorialEstimulacionEntity?

    @Query("SELECT * FROM historial_estimulacion ORDER BY fecha_rea DESC")
    fun getAllHistorial(): Flow<List<HistorialEstimulacionEntity>>

    @Query("SELECT COUNT(*) FROM historial_estimulacion")
    suspend fun getHistorialCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorial(historial: HistorialEstimulacionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(historiales: List<HistorialEstimulacionEntity>)

    @Update
    suspend fun updateHistorial(historial: HistorialEstimulacionEntity)

    @Delete
    suspend fun deleteHistorial(historial: HistorialEstimulacionEntity)
}
