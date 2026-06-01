package com.pabortpag.youtotext.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AsciiRecord::class], version = 1, exportSchema = false)
abstract class YouToTextDatabase : RoomDatabase() {
    abstract fun asciiDao(): AsciiDao

    companion object {
        @Volatile private var INSTANCE: YouToTextDatabase? = null
        fun getInstance(context: Context): YouToTextDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, YouToTextDatabase::class.java, "youtotext_gallery.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}