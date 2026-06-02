package com.pabortpag.youtotext.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

// Vista personalizada que renderiza el texto ASCII carácter a carácter
class AsciiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // === ESTADO DEL FRAME ACTUAL ===
    private var asciiText = ""
    private var frameColors: IntArray? = null
    private var useOriginalMode = false

    // === MÉTRICAS DE DIBUJADO ===
    private var lineHeight = 0f
    private var characterWidth = 12f
    private var baseColor = Color.parseColor("#00FF00")

    // Paint reutilizable para evitar crear objetos en cada frame
    private val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
        color = baseColor
        textAlign = Paint.Align.LEFT
    }

    // Actualiza el frame ASCII completo y fuerza el redibujado
    fun updateFrame(text: String, colors: IntArray?, useOriginal: Boolean) {
        asciiText = text
        frameColors = colors
        useOriginalMode = useOriginal

        // Libera memoria si no se usa el modo original
        if (!useOriginal) frameColors = null

        if (width > 0 && height > 0) recalculateTextMetrics()
        invalidate()
    }

    // Cambia el color base y redibuja inmediatamente
    fun setBaseColor(color: Int) {
        if (baseColor != color) {
            baseColor = color
            paint.color = color
            invalidate()
        }
    }

    // Calcula el tamaño de texto óptimo para llenar el ancho disponible
    private fun recalculateTextMetrics() {
        val lines = asciiText.split('\n').filter { it.isNotEmpty() }
        if (lines.isEmpty()) return

        val maxColumns = lines.maxOf { it.length }

        // 1. Medir con tamaño de referencia seguro
        paint.textSize = 100f
        val referenceAdvance = paint.measureText("M")

        // 2. Calcular escala para llenar el ancho disponible
        val neededWidth = maxColumns * referenceAdvance
        val scale = width.toFloat() / neededWidth
        val finalSize = (100f * scale).coerceAtLeast(8f)

        // 3. Aplicar tamaño final y recalcular métricas reales
        paint.textSize = finalSize
        characterWidth = paint.measureText("M")
        lineHeight = paint.fontMetrics.run { descent - ascent }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) recalculateTextMetrics()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        if (asciiText.isEmpty()) return

        // Fallback si el primer frame llega antes del onSizeChanged
        if (characterWidth <= 0f) recalculateTextMetrics()

        val activeColors = frameColors
        val drawWithOriginalColors = useOriginalMode && activeColors != null

        val lines = asciiText.split('\n')
        var yPosition = lineHeight
        var charIndex = 0

        for (line in lines) {
            if (yPosition > height) break

            if (drawWithOriginalColors) {
                // Modo color original: dibuja cada carácter con su color específico
                var xPosition = 0f
                for (character in line) {
                    val color = if (charIndex < activeColors.size) activeColors[charIndex] else baseColor
                    paint.color = color
                    canvas.drawText(character.toString(), xPosition, yPosition, paint)
                    xPosition += characterWidth
                    charIndex++
                }
            } else {
                // Modo color sólido: dibuja la línea completa con el color base
                paint.color = baseColor
                canvas.drawText(line, 0f, yPosition, paint)
                charIndex += line.length
            }
            yPosition += lineHeight
        }
    }
}