package com.pabortpag.youtotext.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class AsciiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
        color = Color.parseColor("#00FF00")
        textAlign = Paint.Align.LEFT
    }

    private var asciiText = ""
    private var lineHeight = 0f

    fun updateText(text: String) {
        asciiText = text
        if (width > 0 && height > 0) calculateTextSize()
        invalidate()
    }

    private fun calculateTextSize() {
        val lines = asciiText.split('\n').filter { it.isNotEmpty() }
        if (lines.isEmpty()) return

        val maxCols = lines.maxOf { it.length }

        // 🔹 1. Medimos proporciones reales a un tamaño base seguro (20f)
        paint.textSize = 20f
        val baseCharWidth = paint.measureText("M")
        val baseLineHeight = paint.fontMetrics.run { descent - ascent }

        // 🔹 2. Calculamos el tamaño exacto para que llene el ANCHO de la pantalla
        // Fórmula directa: AnchoPantalla / (Columnas * AnchoCaracterUnitario)
        val finalSize = width.toFloat() / (maxCols * (baseCharWidth / 20f))

        // Aplicamos tamaño (mínimo 10f para seguridad, sin límite superior)
        paint.textSize = finalSize.coerceAtLeast(10f)

        // Recalculamos altura de línea con el tamaño real
        lineHeight = paint.fontMetrics.run { descent - ascent }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) calculateTextSize() // Recalcula al girar o cambiar layout
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK) // Fondo sólido evita parpadeo
        if (asciiText.isEmpty()) return

        var y = lineHeight
        val lines = asciiText.split('\n')
        for (line in lines) {
            if (y > height) break // Corta renderizado fuera de pantalla
            canvas.drawText(line, 0f, y, paint)
            y += lineHeight
        }
    }
}