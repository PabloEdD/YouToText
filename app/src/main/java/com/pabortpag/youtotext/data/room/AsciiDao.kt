package com.pabortpag.youtotext.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AsciiDao {

    // Obtiene todos los registros ordenados por fecha (del más reciente al más antiguo)
    @Query("SELECT * FROM ascii_gallery ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AsciiRecord>>

    // Inserta un nuevo registro (o reemplaza si ya existe con el mismo ID)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AsciiRecord)

    // Elimina un registro específico de la base de datos
    @Delete
    suspend fun deleteRecord(record: AsciiRecord)

    // Busca un registro por su identificador único
    @Query("SELECT * FROM ascii_gallery WHERE id = :id")
    suspend fun getRecordById(id: Long): AsciiRecord?
}