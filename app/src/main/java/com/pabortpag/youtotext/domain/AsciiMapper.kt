package com.pabortpag.youtotext.domain

object AsciiMapper {


    /**
     * Mapea grilla de luminancia a texto ASCII.
     * Ejecutar en Dispatcher.Default (CPU-bound).
     */
    fun mapToAscii(grid: ByteArray, width: Int, height: Int, palette: String, invert: Boolean = false): String {
        val builder = StringBuilder(grid.size + height)
        val maxIdx = palette.length - 1 // 🔹 Calculado dinámicamente

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val lum = grid[rowOffset + x].toInt() and 0xFF
                // 🔹 Cálculo directo del índice según la paleta actual
                val idx = (lum * maxIdx / 255).coerceIn(0, maxIdx)
                builder.append(palette[idx])
            }
            builder.append('\n')
        }
        return builder.toString()
    }
}