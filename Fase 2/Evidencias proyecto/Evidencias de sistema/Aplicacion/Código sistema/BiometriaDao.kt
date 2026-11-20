package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sara.robot.data.local.entity.BiometriaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BiometriaDao {

    @Query("SELECT * FROM biometria_paciente WHERE id_paciente = :pacienteId LIMIT 1")
    suspend fun getBiometriaPorPaciente(pacienteId: Long): BiometriaEntity?

    @Query("SELECT * FROM biometria_paciente WHERE id_paciente = :pacienteId LIMIT 1")
    fun observeBiometriaPorPaciente(pacienteId: Long): Flow<BiometriaEntity?>

    @Query("SELECT * FROM biometria_paciente")
    suspend fun getAllBiometrias(): List<BiometriaEntity>

    @Query("SELECT * FROM biometria_paciente")
    fun observeAllBiometrias(): Flow<List<BiometriaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBiometria(biometria: BiometriaEntity): Long

    @Query("DELETE FROM biometria_paciente WHERE id_paciente = :pacienteId")
    suspend fun deleteBiometriaPorPaciente(pacienteId: Long): Int

    @Query("DELETE FROM biometria_paciente")
    suspend fun deleteAll(): Int
}
