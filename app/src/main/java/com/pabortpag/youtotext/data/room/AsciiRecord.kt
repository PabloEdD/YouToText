package com.pabortpag.youtotext.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ascii_gallery")
data class AsciiRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val asciiText: String,
    val color: Int,
    val density: Int,
    val palette: String,
    val timestamp: Long = System.currentTimeMillis()
)