package com.pabortpag.youtotext.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Copia el texto ASCII al portapapeles del sistema
fun copyAsciiToClipboard(context: Context, text: String): Boolean {
    if (text.isEmpty()) return false

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("YouToText ASCII", text)
    clipboard.setPrimaryClip(clip)
    return true
}

// Exporta la vista ASCII renderizada a un archivo PNG en la galería
suspend fun exportAsciiViewToPng(context: Context, view: View): Boolean {
    if (view.width <= 0 || view.height <= 0) return false

    // 1. Captura el canvas en el hilo principal (requisito de la UI)
    val bitmap = withContext(Dispatchers.Main) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        bmp
    }

    // 2. Guarda el archivo en MediaStore en un hilo de E/S
    return withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "YouToText_${System.currentTimeMillis()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/YouToText/")
                }
            }

            val uri: Uri? = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            // Escribe los bytes del bitmap en el flujo de salida
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }
            uri != null
        } catch (e: Exception) {
            Log.e("ExportUtils", "Error exportando PNG", e)
            false
        } finally {
            bitmap.recycle() // Libera la memoria nativa inmediatamente
        }
    }
}