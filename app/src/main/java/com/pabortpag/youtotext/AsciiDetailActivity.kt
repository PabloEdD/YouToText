package com.pabortpag.youtotext

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pabortpag.youtotext.data.room.AsciiRecord
import com.pabortpag.youtotext.data.room.YouToTextDatabase
import com.pabortpag.youtotext.databinding.ActivityAsciiDetailBinding
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModel
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModelFactory
import com.pabortpag.youtotext.util.AsciiCanvasRenderer
import kotlinx.coroutines.launch

class AsciiDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAsciiDetailBinding
    private var currentRecord: AsciiRecord? = null
    private lateinit var viewModel: GalleryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAsciiDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, binding.root)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val dao = YouToTextDatabase.getInstance(this).asciiDao()
        viewModel = androidx.lifecycle.ViewModelProvider(this, GalleryViewModelFactory(dao))
            .get(GalleryViewModel::class.java)

        val recordId = intent.getLongExtra("RECORD_ID", -1)
        if (recordId == -1L) { finish(); return }

        // Cargar registro y renderizar
        lifecycleScope.launch {
            currentRecord = dao.getRecordById(recordId)
            currentRecord?.let { renderAscii(it) }
        }

        // 🔹 Botones inferiores
        binding.btnCopy.setOnClickListener { currentRecord?.asciiText?.let { copyToClipboard(it) } }
        binding.btnDownload.setOnClickListener { currentRecord?.let { exportToPng(it) } }
        binding.btnDelete.setOnClickListener { currentRecord?.let { showDeleteDialog(it) } }
    }

    private fun renderAscii(record: AsciiRecord) {
        // Reutiliza tu AsciiView. Se fuerza color sólido para coherencia en galería
        binding.asciiView.setBaseColor(record.color)
        binding.asciiView.updateFrame(record.asciiText, null, false)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("YouToText", text))
    }

    private fun exportToPng(record: AsciiRecord) {
        binding.btnDownload.isEnabled = false
        lifecycleScope.launch {
            val success = try {
                // Renderiza offscreen para evitar dependencias de UI
                val bitmap = AsciiCanvasRenderer.renderToBitmap(
                    record.asciiText, record.color, 1080, 1920
                )
                val uri = saveBitmapToMediaStore(bitmap, "YouToText_${record.id}.png")
                uri != null
            } catch (e: Exception) { false }

            val msg = if (success) "Imagen guardada en Galería" else "❌ Error al exportar"
            Toast.makeText(this@AsciiDetailActivity, msg, Toast.LENGTH_SHORT).show()
            binding.btnDownload.isEnabled = true
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap, name: String): android.net.Uri? {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/YouToText/")
            }
        }
        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
        return uri
    }

    private fun showDeleteDialog(record: AsciiRecord) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar captura")
            .setMessage("¿Seguro que quieres borrar este ASCII de la galería?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteRecord(record)
                Toast.makeText(this, "Captura eliminada", Toast.LENGTH_SHORT).show()
                finish() // Volver a la galería
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}