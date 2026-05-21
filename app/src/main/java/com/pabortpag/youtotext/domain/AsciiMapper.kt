package com.pabortpag.youtotext.domain

object AsciiMapper {
    // Paleta configurable (densa → ligera). Se puede invertir en UI.
    private const val PALETTE = "@%#*+=-:. "
    private val LOOKUP_TABLE = ByteArray(256)

    init {
        // Precomputar mapeo 0-255 → índice de paleta (O(1) en runtime)
        val maxIdx = PALETTE.length - 1
        for (i in 0..255) {
            LOOKUP_TABLE[i] = (i * maxIdx / 255).toByte()
        }
    }

    /**
     * Mapea grilla de luminancia a texto ASCII.
     * Ejecutar en Dispatcher.Default (CPU-bound).
     */
    fun mapToAscii(grid: ByteArray, width: Int, height: Int, invert: Boolean = false): String {
        // Reutilizamos StringBuilder con capacidad exacta para evitar reallocs
        val builder = StringBuilder(grid.size + height) // +1 por \n por fila
        val threshold = if (invert) 0 else 255

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val lum = grid[rowOffset + x].toInt() and 0xFF
                val idx = LOOKUP_TABLE[if (invert) 255 - lum else lum]
                builder.append(PALETTE[idx.toInt()])
            }
            builder.append('\n')
        }
        return builder.toString()
    }
}