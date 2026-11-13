package com.sara.robot.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sara.robot.data.local.dao.*
import com.sara.robot.data.local.entity.*

@Database(
    entities = [
        PacienteEntity::class,
        RecordatorioEntity::class,
        ContactoEmergenciaEntity::class,
        JuegoEntity::class,
        HistorialEstimulacionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SaraAppDatabase : RoomDatabase() {

    abstract fun pacienteDao(): PacienteDao
    abstract fun recordatorioDao(): RecordatorioDao
    abstract fun contactoEmergenciaDao(): ContactoEmergenciaDao
    abstract fun juegoDao(): JuegoDao
    abstract fun historialEstimulacionDao(): HistorialEstimulacionDao

    companion object {
        @Volatile
        private var INSTANCE: SaraAppDatabase? = null

        fun getDatabase(context: Context): SaraAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaraAppDatabase::class.java,
                    "sara_local_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
