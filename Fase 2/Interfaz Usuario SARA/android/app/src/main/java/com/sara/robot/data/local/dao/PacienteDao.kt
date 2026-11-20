package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sara.robot.data.local.entity.PacienteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PacienteDao {
    @Query("SELECT * FROM Paciente WHERE Id_paciente = :id AND estado = 1")
    suspend fun getPacienteById(id: Long): PacienteEntity?

    @Query("SELECT * FROM Paciente WHERE Id_paciente = :id")
    suspend fun getPacienteByIdIncluyendoInactivos(id: Long): PacienteEntity?

    @Query("SELECT * FROM Paciente WHERE estado = 1 ORDER BY Nombre ASC")
    fun getAllPacientes(): Flow<List<PacienteEntity>>

    @Query("SELECT * FROM Paciente ORDER BY estado DESC, Nombre ASC")
    fun getAllPacientesIncluyendoInactivos(): Flow<List<PacienteEntity>>

    /**
     * Búsqueda unificada de pacientes activos por RUT (Id_paciente), Nombre o Apellidos.
     */
    @Query(
        """
        SELECT * FROM Paciente 
        WHERE estado = 1 AND (
            CAST(Id_paciente AS TEXT) LIKE '%' || :query || '%' OR
            LOWER(Nombre) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(ape_p) LIKE '%' || LOWER(:query) || '%' OR
            LOWER(ape_m) LIKE '%' || LOWER(:query) || '%'
        )
        ORDER BY Nombre ASC
        """
    )
    fun buscarPacientesActivos(query: String): Flow<List<PacienteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaciente(paciente: PacienteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pacientes: List<PacienteEntity>)

    @Query("SELECT COUNT(*) FROM Paciente")
    suspend fun getPacienteCount(): Int

    @Query("SELECT * FROM Paciente WHERE estado = 1 ORDER BY Id_paciente ASC LIMIT 1")
    suspend fun getFirstActivePaciente(): PacienteEntity?

    @Update
    suspend fun updatePaciente(paciente: PacienteEntity)

    /**
     * Eliminación lógica: cambia el atributo 'estado' a false (0) sin borrar el registro físico de SQLite.
     */
    @Query("UPDATE Paciente SET estado = 0 WHERE Id_paciente = :idPaciente")
    suspend fun desactivarPaciente(idPaciente: Long)

    @Query("UPDATE Paciente SET estado = 1 WHERE Id_paciente = :idPaciente")
    suspend fun reactivarPaciente(idPaciente: Long)

    @Delete
    suspend fun deletePaciente(paciente: PacienteEntity)
}
