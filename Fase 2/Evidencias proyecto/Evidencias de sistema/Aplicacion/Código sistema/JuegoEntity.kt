package com.sara.robot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "juegos")
data class JuegoEntity(
    @PrimaryKey(autoGenerate = true)
    val id_juego: Long = 0,       // NUMERIC (10) -> Primary Key
    val descripcion: String       // VARCHAR2 (210)
)
