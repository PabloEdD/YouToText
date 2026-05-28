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
        textSize = 12f
        color = Color.parseColor("#00FF00")
        isAntiAlias = false
    }

    private var asciiText = ""
    private var lineHeight = 0f
    private var isScaleCalculated = false

    fun updateText(text: String) {
        asciiText = text
        if (!isScaleCalculated && width > 0 && height > 0 && text.isNotBlank()) {
            calculateTextSize()
            isScaleCalculated = true
        }
        postInvalidateOnAnimation() // ✅ Sincroniza con refresco de pantalla
    }

    private fun calculateTextSize() {
        val lines = asciiText.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        val maxCols = lines.maxOf { it.length }
        val fm = paint.fontMetrics
        lineHeight = fm.descent - fm.ascent

        val widthScale = width.toFloat() / (maxCols * paint.measureText("M"))
        val heightScale = height.toFloat() / (lines.size * lineHeight)
        paint.textSize = (12f * minOf(widthScale, heightScale)).coerceIn(6f, 18f)

        val newFm = paint.fontMetrics
        lineHeight = newFm.descent - newFm.ascent
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        isScaleCalculated = false // 🔓 Recalcular al girar/redimensionar
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        if (asciiText.isEmpty()) return

        val lines = asciiText.split('\n')
        var y = lineHeight
        for (line in lines) {
            if (y > height) break
            canvas.drawText(line, 0f, y, paint)
            y += lineHeight
        }
    }
}