package com.sara.robot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sara.robot.data.local.entity.JuegoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JuegoDao {
    @Query("SELECT * FROM juegos WHERE id_juego = :id")
    suspend fun getJuegoById(id: Long): JuegoEntity?

    @Query("SELECT * FROM juegos")
    fun getAllJuegos(): Flow<List<JuegoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJuego(juego: JuegoEntity): Long

    @Update
    suspend fun updateJuego(juego: JuegoEntity)

    @Delete
    suspend fun deleteJuego(juego: JuegoEntity)
}
