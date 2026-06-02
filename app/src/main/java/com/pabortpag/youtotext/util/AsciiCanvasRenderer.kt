package com.pabortpag.youtotext.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

// Renderiza texto ASCII en un Bitmap con fondo negro y texto monocromo
object AsciiCanvasRenderer {

    // Genera un Bitmap de alta resolución para exportar a PNG (tamaño fijo)
    fun renderToBitmap(
        asciiText: String,
        baseColor: Int,
        targetWidthPx: Int = 1080,
        targetHeightPx: Int = 1920
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            color = baseColor
            textSize = 28f // Tamaño fijo para exportación de alta calidad
            isAntiAlias = false
        }

        val lineHeight = paint.fontMetrics.run { descent - ascent }
        var yPosition = lineHeight

        for (textLine in asciiText.split('\n')) {
            if (yPosition > targetHeightPx) break
            canvas.drawText(textLine, 20f, yPosition, paint)
            yPosition += lineHeight
        }

        return bitmap
    }

    // Genera un Bitmap ajustado para miniaturas de galería (texto adaptativo)
    fun renderPreviewBitmap(
        asciiText: String,
        baseColor: Int,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        if (asciiText.isBlank()) return bitmap

        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            color = baseColor
            isAntiAlias = false
        }

        val lines = asciiText.split('\n')
        val maxColumns = lines.maxOfOrNull { it.length } ?: 1
        val maxRows = lines.size.coerceAtLeast(1)

        // 1. Medir dimensiones reales a textSize = 100f
        paint.textSize = 100f
        val charWidthAt100 = paint.measureText("M").coerceAtLeast(1f)
        val lineHeightAt100 = paint.fontMetrics.run { descent - ascent }.coerceAtLeast(1f)

        // 2. Calcular factores de escala (SIN multiplicar por 100f extra)
        // Escala necesaria para que el ancho encaje en targetWidthPx
        val scaleByWidth = targetWidthPx.toFloat() / (charWidthAt100 * maxColumns)
        // Escala necesaria para que el alto encaje en targetHeightPx
        val scaleByHeight = targetHeightPx.toFloat() / (lineHeightAt100 * maxRows)

        // 3. Usamos la escala MENOR para garantizar que quepa en AMBOS ejes
        val finalScale = minOf(scaleByWidth, scaleByHeight)

        // 4. Aplicar escala (textSize = 100f * escala)
        // Coercionamos entre 8f y 200f para evitar renders invisibles o colapsos
        paint.textSize = (100f * finalScale).coerceIn(8f, 200f)

        val lineHeight = paint.fontMetrics.run { descent - ascent }

        // 5. Centrar verticalmente si sobra espacio
        val totalTextHeight = lineHeight * maxRows
        val startY = if (totalTextHeight < targetHeightPx) {
            (targetHeightPx - totalTextHeight) / 2 + lineHeight
        } else {
            lineHeight
        }

        var yPosition = startY

        for (textLine in lines) {
            if (yPosition > targetHeightPx) break
            canvas.drawText(textLine, 0f, yPosition, paint)
            yPosition += lineHeight
        }

        return bitmap
    }
}