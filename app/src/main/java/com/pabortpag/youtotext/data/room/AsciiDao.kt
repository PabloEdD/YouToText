package com.pabortpag.youtotext.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AsciiDao {
    @Query("SELECT * FROM ascii_gallery ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AsciiRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AsciiRecord)

    @Delete
    suspend fun deleteRecord(record: AsciiRecord)

    @Query("SELECT * FROM ascii_gallery WHERE id = :id")
    suspend fun getRecordById(id: Long): AsciiRecord?
}