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
        targetHeightPx: Int,
        isOriginalColor: Boolean = false,
        colors: IntArray? = null
    ): Bitmap {
        if (asciiText.isBlank()) {
            return Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        }

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

        // 2. Calcular escala PARA LLENAR EL ANCHO
        val scaleByWidth = targetWidthPx.toFloat() / (charWidthAt100 * maxColumns)
        val naturalHeight = (lineHeightAt100 * scaleByWidth * maxRows).toInt()

        val maxHeight = targetHeightPx.coerceAtLeast(200)
        val finalScale = if (naturalHeight > maxHeight) {
            scaleByWidth * (maxHeight.toFloat() / naturalHeight)
        } else {
            scaleByWidth
        }

        // 3. Aplicar la escala final
        paint.textSize = (100f * finalScale).coerceIn(8f, 200f)
        val finalLineHeight = paint.fontMetrics.run { descent - ascent }
        val charWidth = paint.measureText("M") // Ancho real de un carácter

        val bitmapHeight = (finalLineHeight * maxRows).toInt().coerceAtLeast(100)

        val bitmap = Bitmap.createBitmap(targetWidthPx, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        // 4. Centrar verticalmente si sobra espacio
        val totalTextHeight = finalLineHeight * maxRows
        val startY = if (totalTextHeight < bitmapHeight) {
            (bitmapHeight - totalTextHeight) / 2 + finalLineHeight
        } else {
            finalLineHeight
        }

        var yPosition = startY
        var colorIndex = 0

        // 5. Dibujar el texto (carácter a carácter si es modo original)
        for (textLine in lines) {
            if (yPosition > bitmapHeight) break

            if (isOriginalColor && colors != null) {
                var xPosition = 0f
                for (char in textLine) {
                    val color = if (colorIndex < colors.size) colors[colorIndex] else baseColor
                    paint.color = color
                    canvas.drawText(char.toString(), xPosition, yPosition, paint)
                    xPosition += charWidth
                    colorIndex++
                }
            } else {
                paint.color = baseColor
                canvas.drawText(textLine, 0f, yPosition, paint)
                colorIndex += textLine.length
            }
            yPosition += finalLineHeight
        }

        return bitmap
    }
}