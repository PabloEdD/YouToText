package com.pabortpag.youtotext.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

// Entidad de la base de datos Room que representa cada captura guardada en la galería
@Entity(tableName = "ascii_gallery")
data class AsciiRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val asciiText: String,          // Texto ASCII completo de la captura
    val baseColor: Int,             // Color base en formato Int (ARGB) aplicado en el momento de la captura
    val blockFactor: Int,           // Densidad o tamaño de bloque utilizado para el muestreo espacial
    val characterPalette: String,   // Conjunto de caracteres (paleta) utilizado para la conversión
    val timestamp: Long = System.currentTimeMillis() // Fecha y hora de la creación del registro (usado para ordenar)
)