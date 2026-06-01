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

    private var asciiText = ""
    private var lineHeight = 0f
    private var baseColor = Color.parseColor("#00FF00")
    private var useOriginalMode = false
    private var colors: IntArray? = null
    private var charAdvance = 12f

    private val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
        color = baseColor
        textAlign = Paint.Align.LEFT
    }

    fun updateFrame(text: String, colors: IntArray?, useOriginal: Boolean) {
        this.colors = colors
        this.useOriginalMode = useOriginal
        asciiText = text

        // 🔹 Limpia el array si no se usa para evitar referencias fantasma y ahorrar RAM
        if (!useOriginal) this.colors = null

        if (width > 0 && height > 0) calculateTextSize()
        invalidate()
    }

    fun setBaseColor(color: Int) {
        if (baseColor != color) {
            baseColor = color
            paint.color = color // Sincroniza el Paint inmediatamente
            invalidate()        // Fuerza redraw sin esperar nuevo frame ASCII
        }
    }

    private fun calculateTextSize() {
        val lines = asciiText.split('\n').filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        val maxCols = lines.maxOf { it.length }

        // 1. Medir referencia a tamaño base seguro
        paint.textSize = 100f
        val baseAdvance = paint.measureText("M")

        // 2. Cálculo dinámico para llenar SIEMPRE el ancho disponible
        val neededWidth = maxCols * baseAdvance
        val scale = width.toFloat() / neededWidth
        // Mínimo 8px para legibilidad. Sin límite superior artificial.
        val finalSize = (100f * scale).coerceAtLeast(8f)

        paint.textSize = finalSize
        charAdvance = paint.measureText("M") // Recalcular avance real
        lineHeight = paint.fontMetrics.run { descent - ascent }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) calculateTextSize() // Recalcula al girar o cambiar layout
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        if (asciiText.isEmpty()) return

        // Fallback seguro si el primer frame llega antes del onSizeChanged
        if (charAdvance <= 0f) calculateTextSize()

        val safeColors = colors
        val drawWithOriginal = useOriginalMode && safeColors != null

        val lines = asciiText.split('\n')
        var y = lineHeight
        var cIdx = 0

        for (line in lines) {
            if (y > height) break
            if (drawWithOriginal) {
                var x = 0f
                for (ch in line) {
                    val color = if (cIdx < safeColors.size) safeColors[cIdx] else baseColor
                    paint.color = color
                    canvas.drawText(ch.toString(), x, y, paint)
                    x += charAdvance
                    cIdx++
                }
            } else {
                // 🔹 CLAVE: Restaurar color base al salir de modo píxel
                paint.color = baseColor
                canvas.drawText(line, 0f, y, paint)
                cIdx += line.length
            }
            y += lineHeight
        }
    }
}