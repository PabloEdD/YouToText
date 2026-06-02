package com.pabortpag.youtotext.domain

// Convierte una cuadrícula de valores de luminancia en texto ASCII
object AsciiMapper {

    // Mapea cada píxel de la cuadrícula a un carácter según su brillo
    // Debe ejecutarse en Dispatchers.Default (procesamiento intensivo de CPU)
    fun mapToAscii(
        luminanceGrid: ByteArray,
        gridWidth: Int,
        gridHeight: Int,
        characterPalette: String,
        invert: Boolean = false
    ): String {
        val asciiBuilder = StringBuilder(luminanceGrid.size + gridHeight)
        val paletteMaxIndex = characterPalette.length - 1

        for (row in 0 until gridHeight) {
            val rowOffset = row * gridWidth
            for (column in 0 until gridWidth) {
                val luminance = luminanceGrid[rowOffset + column].toInt() and 0xFF

                // Cálculo base del índice (0 = oscuro, maxIdx = claro)
                val rawIndex = (luminance * paletteMaxIndex / 255).coerceIn(0, paletteMaxIndex)

                // Si se invierte, el índice se refleja (0 se convierte en maxIdx, y viceversa)
                val finalIndex = if (invert) (paletteMaxIndex - rawIndex) else rawIndex

                asciiBuilder.append(characterPalette[finalIndex])
            }
            asciiBuilder.append('\n')
        }

        return asciiBuilder.toString()
    }
}