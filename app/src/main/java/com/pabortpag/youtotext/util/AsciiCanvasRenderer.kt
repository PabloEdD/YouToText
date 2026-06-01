package com.pabortpag.youtotext.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object AsciiCanvasRenderer {
    fun renderToBitmap(text: String, textColor: Int, widthPx: Int = 1080, heightPx: Int = 1920): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            this.color = textColor
            textSize = 28f
            isAntiAlias = false
        }

        val lineHeight = paint.fontMetrics.run { descent - ascent }
        var y = lineHeight

        for (line in text.split('\n')) {
            if (y > heightPx) break
            canvas.drawText(line, 20f, y, paint)
            y += lineHeight
        }
        return bitmap
    }
}