package com.pabortpag.youtotext.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Configuración de la base de datos Room
@Database(entities = [AsciiRecord::class], version = 1, exportSchema = false)
abstract class YouToTextDatabase : RoomDatabase() {

    // Proporciona el DAO para consultar y modificar la tabla de la galería
    abstract fun asciiDao(): AsciiDao

    companion object {
        // Instancia única de la base de datos (Singleton seguro para hilos)
        @Volatile
        private var instance: YouToTextDatabase? = null

        // Devuelve la instancia existente o crea una nueva si no existe
        fun getInstance(context: Context): YouToTextDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    YouToTextDatabase::class.java,
                    "youtotext_gallery.db"
                )
                    .fallbackToDestructiveMigration() // Borra y recrea la BD si cambia la versión
                    .build()
                    .also { instance = it }
            }
        }
    }
}