package com.pabortpag.youtotext

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pabortpag.youtotext.databinding.ActivityMainBinding
import com.pabortpag.youtotext.ui.viewmodel.AsciiViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.appcompat.app.AlertDialog
import android.widget.SeekBar
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.pabortpag.youtotext.data.SettingsPrefs
import com.pabortpag.youtotext.data.room.YouToTextDatabase
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModel
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.java

typealias LumaListener = (luma: Double) -> Unit
typealias LuminanceGridListener = (grid: ByteArray, colors: IntArray?, width: Int, height: Int) -> Unit

//  __    __               ______        ______                __
// /\ \  /\ \             /\__  _\      /\__  _\              /\ \__
// \ `\`\\/'/ ___   __  __\/_/\ \/   ___\/_/\ \/    __   __  _\ \ ,_\
//  `\ `\ /' / __`\/\ \/\ \  \ \ \  / __`\ \ \ \  /'__`\/\ \/'\\ \ \/
//    `\ \ \/\ \L\ \ \ \_\ \  \ \ \/\ \L\ \ \ \ \/\  __/\/>  </ \ \ \_
//      \ \_\ \____/\ \____/   \ \_\ \____/  \ \_\ \____\/\_/\_\ \ \__\
//       \/_/\/___/  \/___/     \/_/\/___/    \/_/\/____/\//\/_/  \/__/

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private val asciiViewModel: AsciiViewModel by lazy { AsciiViewModel() }
    private var cameraProvider: ProcessCameraProvider? = null
    private var useFrontCamera = false

    lateinit var db: YouToTextDatabase
    lateinit var galleryViewModel: GalleryViewModel

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        setContentView(binding.root)
        SettingsPrefs.init(this)

        binding.asciiView.setBaseColor(SettingsPrefs.getColor())

        db = YouToTextDatabase.getInstance(this)
        val dao = db.asciiDao()
        galleryViewModel = ViewModelProvider(this, GalleryViewModelFactory(dao))
            .get(GalleryViewModel::class.java)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, binding.root)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera perfmissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set UP THE LISTENERS FOR TAKE PHOTO AND VIDEO CAPTURE BUTTONS
        binding.imageCaptureButton.setOnClickListener { takePhoto() }
        binding.videoCaptureButton.setOnClickListener { captureVideo() }
        binding.copyButton.setOnClickListener {
            val text = asciiViewModel.currentAscii
            if (text.isEmpty()) return@setOnClickListener

            copyAsciiToClipboard(text)
            galleryViewModel.saveRecord(
                text, SettingsPrefs.getColor(), SettingsPrefs.getBlockFactor(), SettingsPrefs.getPalette()
            )
        }
        binding.switchCamButton.setOnClickListener {
            useFrontCamera = !useFrontCamera
            restartCamera()
        }
        binding.photoButton.setOnClickListener {
            val text = asciiViewModel.currentAscii
            if (text.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                val success = exportAsciiViewToPng(binding.asciiView)
                if (success) {
                    galleryViewModel.saveRecord(
                        text, SettingsPrefs.getColor(), SettingsPrefs.getBlockFactor(), SettingsPrefs.getPalette()
                    )
                }
            }
        }

        if (allPermissionsGranted()) startCamera() else requestPermissions()
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnDensity.setOnClickListener {
            val current = SettingsPrefs.getBlockFactor()
            showDensityDialog(current) { newFactor ->
                SettingsPrefs.updateBlockFactor(newFactor)
                restartCamera()
            }
        }

        binding.btnPalette.setOnClickListener {
            val current = SettingsPrefs.getPalette()
            showPaletteDialog(current) { newPalette ->
                SettingsPrefs.updatePalette(newPalette)
                restartCamera()
            }
        }

        binding.btnColor.setOnClickListener {
            val current = SettingsPrefs.getColor()
            showColorDialog(current) { newColor ->
                SettingsPrefs.updateColor(newColor)
                binding.asciiView.setBaseColor(newColor) // Aplica color al vuelo
            }
        }

        binding.btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }

        // 🔹 Observación segura al ciclo de vida
        val asciiView = binding.asciiView
        asciiView.bringToFront() // Garantiza que se pinte encima del PreviewView

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                asciiViewModel.asciiFrame.collectLatest { text ->
                    binding.asciiView.updateFrame(
                        text,
                        asciiViewModel.currentColors,
                        SettingsPrefs.isOriginalColorMode()
                    )
                }
            }
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YouToText-Images")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                }

                override fun onPostviewBitmapAvailable(bitmap: Bitmap) {
                    super.onPostviewBitmapAvailable(bitmap)

                    Log.d(TAG, "bitmap: " + bitmap)
                }
            }
        )
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        binding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YouToText-Videos")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )
                        }
                        binding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun copyAsciiToClipboard(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, "Esperando primer frame ASCII...", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("YouToText ASCII", text)
        clipboard.setPrimaryClip(clip)
    }

    private suspend fun exportAsciiViewToPng(view: View): Boolean {
        if (view.width <= 0 || view.height <= 0) return false

        val bitmap = withContext(Dispatchers.Main) {
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            view.draw(canvas) // Renderiza exactamente lo que ve el usuario
            bmp
        }

        return withContext(Dispatchers.IO) {
            try {
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "YouToText_${System.currentTimeMillis()}.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/YouToText/")
                    }
                }

                val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }
                uri != null
            } catch (e: Exception) {
                Log.e("YouToText", "Error exportando PNG", e)
                false
            } finally {
                bitmap.recycle() // Libera memoria nativa inmediatamente
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageCapture = ImageCapture.Builder().build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(480, 640), // Target: ~480x640 píxeles
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER // Si no existe exacto, toma la siguiente más alta
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Evita cola de frames
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor, LuminosityAnalyzer(
                        listener = { luma -> Log.d(TAG, "Avg luminosity: $luma") },
                        gridListener = { grid, colors, width, height ->
                            asciiViewModel.onGridReceived(grid, colors, width, height)
                        },
                        blockFactor = SettingsPrefs.getBlockFactor(),
                        mirrorHorizontally = useFrontCamera,
                        extractColors = SettingsPrefs.isOriginalColorMode() // 🔹 CLAVE
                    ))
                }

            val cameraSelector = if (useFrontCamera)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
//                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture,
                    imageAnalysis
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "YouToText"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    private class LuminosityAnalyzer(
        private val listener: LumaListener? = null,
        private val gridListener: LuminanceGridListener? = null,
        private val blockFactor: Int = 4,
        private val mirrorHorizontally: Boolean = false,
        private val extractColors: Boolean = false // 🔹 NUEVO
    ) : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            try {
                val yBuffer = image.planes[0].buffer
                val yRowStride = image.planes[0].rowStride
                val yPixelStride = image.planes[0].pixelStride
                val srcWidth = image.width
                val srcHeight = image.height
                val rotation = image.imageInfo.rotationDegrees

                if (listener != null) {
                    var sum = 0L; var count = 0; var y = 0
                    while (y < srcHeight) { var x = 0; while (x < srcWidth) {
                        sum += yBuffer.get(y * yRowStride + x * yPixelStride).toInt() and 0xFF
                        count++; x += yPixelStride }; y++ }
                    listener(if (count > 0) sum.toDouble() / count else 0.0)
                }

                if (gridListener != null) {
                    val outWidth = (srcWidth + blockFactor - 1) / blockFactor
                    val outHeight = (srcHeight + blockFactor - 1) / blockFactor
                    val grid = ByteArray(outWidth * outHeight)
                    val colors = if (extractColors) IntArray(outWidth * outHeight) else null // 🔹 Solo si activa

                    // Buffers U/V (solo se leen si extractColors == true)
                    val uPlane = image.planes[1]; val vPlane = image.planes[2]
                    val uBuffer = uPlane.buffer; val vBuffer = vPlane.buffer
                    val uRowStride = uPlane.rowStride; val vRowStride = vPlane.rowStride
                    val uPixelStride = uPlane.pixelStride; val vPixelStride = vPlane.pixelStride

                    var idx = 0; var by = 0
                    while (by < outHeight) { var bx = 0; while (bx < outWidth) {
                        val cx = minOf(bx * blockFactor + blockFactor / 2, srcWidth - 1)
                        val cy = minOf(by * blockFactor + blockFactor / 2, srcHeight - 1)
                        val y = yBuffer.get(cy * yRowStride + cx).toInt() and 0xFF

                        var r = 255; var g = 255; var b = 255
                        if (extractColors) {
                            val ux = cx / 2; val uy = cy / 2
                            val u = uBuffer.get(uy * uRowStride + ux * uPixelStride).toInt() and 0xFF
                            val v = vBuffer.get(uy * vRowStride + ux * vPixelStride).toInt() and 0xFF
                            val c = y - 16; val d = u - 128; val e = v - 128
                            r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                            g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                            b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
                            colors!![idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                        grid[idx++] = y.toByte()
                        bx++
                    }; by++ }

                    // Geometría (igual que antes)
                    var tempGrid = grid
                    var tempColors = colors // Nullable según extractColors
                    var tempW = outWidth
                    var tempH = outHeight

                    // 1. Rotación/Transposición (90°/270°)
                    if (rotation == 90 || rotation == 270) {
                        val newGrid = ByteArray(tempGrid.size)
                        val newColors = if (tempColors != null) IntArray(tempColors.size) else null
                        for (y in 0 until tempH) {
                            for (x in 0 until tempW) {
                                newGrid[x * tempH + y] = tempGrid[y * tempW + x]
                                if (newColors != null) newColors[x * tempH + y] = tempColors!![y * tempW + x]
                            }
                        }
                        tempGrid = newGrid
                        tempColors = newColors
                        val aux = tempW; tempW = tempH; tempH = aux
                    }

                    // 2. Volteo Horizontal (Corrección base para TODAS las cámaras)
                    val flipHGrid = ByteArray(tempGrid.size)
                    val flipHColors = if (tempColors != null) IntArray(tempColors.size) else null
                    for (y in 0 until tempH) {
                        for (x in 0 until tempW) {
                            flipHGrid[y * tempW + (tempW - 1 - x)] = tempGrid[y * tempW + x]
                            if (flipHColors != null) flipHColors[y * tempW + (tempW - 1 - x)] = tempColors!![y * tempW + x]
                        }
                    }
                    tempGrid = flipHGrid
                    tempColors = flipHColors

                    // 3. Volteo Vertical (SOLO cámara frontal)
                    if (mirrorHorizontally) {
                        val finalGrid = ByteArray(tempGrid.size)
                        val finalColors = if (tempColors != null) IntArray(tempColors.size) else null
                        for (y in 0 until tempH) {
                            for (x in 0 until tempW) {
                                finalGrid[(tempH - 1 - y) * tempW + x] = tempGrid[y * tempW + x]
                                if (finalColors != null) finalColors[(tempH - 1 - y) * tempW + x] = tempColors!![y * tempW + x]
                            }
                        }
                        tempGrid = finalGrid
                        tempColors = finalColors
                    }

                    // Enviamos grid y colores YA alineados espacialmente
                    gridListener(tempGrid, tempColors, tempW, tempH)
                }
            } finally { image.close() }
        }
    }

    private fun showDensityDialog(currentValue: Int, onSave: (Int) -> Unit) {
        val seekBar = SeekBar(this).apply {
            progress = currentValue - 4 // Ajuste visual
            max = 20 // Rango de 0 a 20 (real 4 a 24)
        }

        AlertDialog.Builder(this)
            .setTitle("Densidad de caracteres")
            .setView(seekBar)
            .setPositiveButton("Aceptar") { _, _ ->
                onSave(seekBar.progress + 4)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPaletteDialog(currentPalette: String, onSave: (String) -> Unit) {
        val palettes = mapOf(
            "Clásica" to "@%#*+=-:. ",
            "Denso" to "\$@B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~<>i!lI;:,\"^`'. ",
            "Minimal" to " .:-=+*#%@",
            "Código" to "01 "
        )
        val options = palettes.keys.toTypedArray()
        val currentIndex = options.indexOfFirst { palettes[it] == currentPalette }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Seleccionar Paleta")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                onSave(palettes[options[which]]!!)
                dialog.dismiss()
            }
            .show()
    }

    private fun showColorDialog(currentColor: Int, onSave: (Int) -> Unit) {
        val colors = mapOf(
            "Color Original" to -2,
            "Verde" to Color.parseColor("#00FF00"),
            "Blanco" to Color.WHITE,
            "Rojo" to Color.parseColor("#FF3333"),
            "Azul" to Color.parseColor("#00FFFF"),
            "Ámbar" to Color.parseColor("#FFBF00")
        )
        val options = colors.keys.toTypedArray()
        val currentIsOriginal = SettingsPrefs.isOriginalColorMode()
        val currentIndex = if (currentIsOriginal) 0 else options.indexOfFirst { colors[it] == currentColor }.coerceAtLeast(1)

        AlertDialog.Builder(this)
            .setTitle("Color de caracteres")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selectedValue = colors[options[which]]!!
                if (selectedValue == -2) {
                    SettingsPrefs.setOriginalColorMode(true)
                    restartCamera() // Extrae U/V en el siguiente frame
                } else {
                    SettingsPrefs.setOriginalColorMode(false)
                    SettingsPrefs.updateColor(selectedValue)
                    binding.asciiView.setBaseColor(selectedValue) // 🔹 Aplica color en vivo
                    restartCamera() // Apaga extracción U/V para ahorrar CPU
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun restartCamera() {
        cameraProvider?.unbindAll() // Desvincula cámara actual
        startCamera() // Vuelve a vincular con la nueva configuración
    }
}