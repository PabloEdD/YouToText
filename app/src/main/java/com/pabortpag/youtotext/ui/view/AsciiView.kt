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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 8f // Se ajustará dinámicamente en onSizeChanged
        color = Color.parseColor("#00FF00") // Estilo terminal clásico
        isAntiAlias = false // Renderizado nítido y más rápido
    }

    private var asciiText: String = ""
    private var lineHeight = 0f
    private var charWidth = 0f

    fun updateText(text: String) {
        asciiText = text
        if (width > 0 && height > 0 && text.isNotBlank()) {
            calculateScale()
        }
        invalidate() // Solicita redraw en el hilo UI
    }

    private fun calculateScale() {
        val lines = asciiText.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        val maxCols = lines.maxOf { it.length }
        val fm = paint.fontMetrics
        lineHeight = fm.descent - fm.ascent

        val widthScale = width.toFloat() / (maxCols * paint.measureText("M"))
        val heightScale = height.toFloat() / (lines.size * lineHeight)
        paint.textSize = (10f * minOf(widthScale, heightScale)).coerceIn(6f, 16f)

        val newFm = paint.fontMetrics
        lineHeight = newFm.descent - newFm.ascent
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Calcula tamaño de fuente para que el ASCII ocupe la vista completa
            val lines = asciiText.split('\n').filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val maxCols = lines.maxOf { it.length }
                val fontMetrics = paint.fontMetrics
                lineHeight = fontMetrics.descent - fontMetrics.ascent

                // Ajusta textSize proporcionalmente
                paint.textSize = minOf(w / maxCols.toFloat(), h / (lines.size * lineHeight)) * 1.2f
                // Recalcula métricas tras cambiar textSize
                val fm = paint.fontMetrics
                lineHeight = fm.descent - fm.ascent
                charWidth = paint.measureText("M") // Carácter de referencia monospace
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (asciiText.isEmpty()) return

        val lines = asciiText.split('\n')
        var y = lineHeight
        for (line in lines) {
            if (y > height) break // Optimización: no dibujar fuera de vista
            canvas.drawText(line, 0f, y, paint)
            y += lineHeight
        }
    }
}