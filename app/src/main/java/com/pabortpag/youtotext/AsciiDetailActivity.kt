package com.pabortpag.youtotext

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.pabortpag.youtotext.data.room.AsciiRecord
import com.pabortpag.youtotext.data.room.YouToTextDatabase
import com.pabortpag.youtotext.databinding.ActivityAsciiDetailBinding
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModel
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModelFactory
import com.pabortpag.youtotext.util.AsciiCanvasRenderer
import com.pabortpag.youtotext.util.copyAsciiToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Activity que muestra el detalle de una captura ASCII guardada
class AsciiDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAsciiDetailBinding
    private var currentRecord: AsciiRecord? = null
    private lateinit var galleryViewModel: GalleryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAsciiDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        hideSystemBars()
        setupViewModel()
        loadRecord()
        setupActionButtons()
    }

    // Inicializa el ViewModel con el DAO de la base de datos
    private fun setupViewModel() {
        val dao = YouToTextDatabase.getInstance(this).asciiDao()
        galleryViewModel = ViewModelProvider(this, GalleryViewModelFactory(dao))
            .get(GalleryViewModel::class.java)
    }

    // Carga el registro desde la base de datos y lo renderiza
    private fun loadRecord() {
        val recordId = intent.getLongExtra("RECORD_ID", -1L)
        if (recordId == -1L) {
            finish()
            return
        }

        lifecycleScope.launch {
            currentRecord = galleryViewModel.getRecordById(recordId)
            currentRecord?.let { renderAscii(it) }
        }
    }

    // Configura los listeners de los botones de acción
    private fun setupActionButtons() {
        binding.btnCopy.setOnClickListener {
            currentRecord?.asciiText?.let { text ->
                copyAsciiToClipboard(this, text)
                Toast.makeText(this, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownload.setOnClickListener {
            currentRecord?.let { record -> exportToPng(record) }
        }

        binding.btnDelete.setOnClickListener {
            currentRecord?.let { record -> showDeleteConfirmation(record) }
        }
    }

    // Renderiza el texto ASCII en la vista con el color guardado
    private fun renderAscii(record: AsciiRecord) {
        binding.asciiView.setBaseColor(record.baseColor)
        binding.asciiView.updateFrame(record.asciiText, null, false)
    }

    // Exporta el ASCII a un archivo PNG en la galería del dispositivo
    private fun exportToPng(record: AsciiRecord) {
        binding.btnDownload.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val bitmap = AsciiCanvasRenderer.renderToBitmap(
                        asciiText = record.asciiText,
                        baseColor = record.baseColor,
                        targetWidthPx = 1080,
                        targetHeightPx = 1920
                    )
                    val uri = saveBitmapToMediaStore(bitmap, "YouToText_${record.id}.png")
                    bitmap.recycle()
                    uri != null
                } catch (e: Exception) {
                    false
                }
            }

            val message = if (success) "Imagen guardada en Galería" else "Error al exportar"
            Toast.makeText(this@AsciiDetailActivity, message, Toast.LENGTH_SHORT).show()
            binding.btnDownload.isEnabled = true
        }
    }

    // Guarda un Bitmap en MediaStore y devuelve la URI del archivo creado
    private suspend fun saveBitmapToMediaStore(bitmap: Bitmap, fileName: String): Uri? {
        return withContext(Dispatchers.IO) {
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/YouToText/")
                }
            }

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let { targetUri ->
                contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }
            uri
        }
    }

    // Muestra un diálogo de confirmación antes de eliminar el registro
    private fun showDeleteConfirmation(record: AsciiRecord) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar captura")
            .setMessage("¿Seguro que quieres borrar este ASCII de la galería?")
            .setPositiveButton("Eliminar") { _, _ ->
                galleryViewModel.deleteRecord(record)
                Toast.makeText(this, "Captura eliminada", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Oculta las barras de sistema para modo inmersivo
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, binding.root)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}