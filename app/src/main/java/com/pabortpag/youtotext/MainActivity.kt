package com.pabortpag.youtotext

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.ImageView
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.pabortpag.youtotext.databinding.ActivityMainBinding
import com.pabortpag.youtotext.ui.view.AsciiView
import com.pabortpag.youtotext.ui.viewmodel.AsciiViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.appcompat.app.AlertDialog
import android.widget.SeekBar
import android.graphics.Color
import com.pabortpag.youtotext.data.SettingsPrefs
import kotlinx.coroutines.flow.first

typealias LumaListener = (luma: Double) -> Unit
typealias LuminanceGridListener = (grid: ByteArray, width: Int, height: Int) -> Unit

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

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        setContentView(binding.root)
        SettingsPrefs.init(this)

        binding.asciiView.setBaseColor(SettingsPrefs.getColor())

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
        binding.copyButton.setOnClickListener { copyAsciiToClipboard(asciiViewModel.currentAscii) }

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

        // 🔹 Observación segura al ciclo de vida
        val asciiView = binding.asciiView
        asciiView.bringToFront() // Garantiza que se pinte encima del PreviewView

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                asciiViewModel.asciiFrame.collectLatest { text ->
                    asciiView.updateText(text)
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
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("YouToText ASCII", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

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
                    analysis.setAnalyzer(
                        cameraExecutor, LuminosityAnalyzer(
                            listener = { luma -> Log.d(TAG, "Avg luminosity: $luma") },
                            gridListener = { grid, width, height ->
                                asciiViewModel.onGridReceived(grid, width, height)
                                Log.d(
                                    TAG,
                                    "ASCII grid ready: ${width}x${height} = ${grid.size} celdas"
                                )
                            },
                            blockFactor = SettingsPrefs.getBlockFactor(),
                            mirrorHorizontally = true
                        )
                    )
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
//                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
                cameraProvider.bindToLifecycle(
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
        private val mirrorHorizontally: Boolean = false
    ) : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            try {
                val yBuffer = image.planes[0].buffer
                val yRowStride = image.planes[0].rowStride
                val yPixelStride = image.planes[0].pixelStride
                val srcWidth = image.width
                val srcHeight = image.height
                val rotation = image.imageInfo.rotationDegrees // detectar rotación

                if (listener != null) {
                    var sum = 0L
                    var count = 0
                    var y = 0
                    while (y < srcHeight) {
                        var x = 0
                        while (x < srcWidth) {
                            val idx = y * yRowStride + x * yPixelStride
                            sum += yBuffer.get(idx).toInt() and 0xFF
                            count++
                            x += yPixelStride // Avanzar según stride real
                        }
                        y++
                    }
                    listener(if (count > 0) sum.toDouble() / count else 0.0)
                }

                // Generar grilla para ASCII (solo si hay listener registrado)
                if (gridListener != null) {
                    val outWidth = (srcWidth + blockFactor - 1) / blockFactor
                    val outHeight = (srcHeight + blockFactor - 1) / blockFactor
                    val grid = ByteArray(outWidth * outHeight)
                    var outIndex = 0

                    var y = 0
                    while (y < srcHeight) {
                        var x = 0
                        while (x < srcWidth) {
                            var blockSum = 0
                            var blockCount = 0

                            var dy = 0
                            while (dy < blockFactor && (y + dy) < srcHeight) {
                                var dx = 0
                                while (dx < blockFactor && (x + dx) < srcWidth) {
                                    val bufIdx = (y + dy) * yRowStride + (x + dx) * yPixelStride
                                    blockSum += yBuffer.get(bufIdx).toInt() and 0xFF
                                    blockCount++
                                    dx++
                                }
                                dy++
                            }

                            grid[outIndex++] = (blockSum / blockCount).toByte()
                            x += blockFactor
                        }
                        y += blockFactor
                    }

                    val finalGrid: ByteArray;
                    var finalW: Int;
                    var finalH: Int
                    if (rotation == 90 || rotation == 270) {
                        finalGrid = transposeGrid(grid, outWidth, outHeight)
                        finalW = outHeight; finalH = outWidth
                    } else {
                        finalGrid = grid; finalW = outWidth; finalH = outHeight
                    }

                    val outputGrid =
                        if (mirrorHorizontally) mirrorGrid(finalGrid, finalW, finalH) else finalGrid

                    gridListener(outputGrid, finalW, finalH)
                    Log.d("Grid", "Grid: $outputGrid, W: $finalW, H: $finalH")
                    Log.d(
                        "Frame",
                        "Frame: ${image.width}x${image.height} | Grid: ${outWidth}x${outHeight} | Rot: $rotation°"
                    )
                }
            } finally {
                image.close()
            }
        }

        private fun transposeGrid(input: ByteArray, width: Int, height: Int): ByteArray {
            val output = ByteArray(input.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    output[x * height + y] = input[y * width + x]
                }
            }
            return output
        }

        private fun mirrorGrid(input: ByteArray, width: Int, height: Int): ByteArray {
            val output = ByteArray(input.size)
            for (y in 0 until height) for (x in 0 until width) output[y * width + (width - 1 - x)] =
                input[y * width + x]
            return output
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
            "Verde" to Color.parseColor("#00FF00"),
            "Blanco" to Color.WHITE,
            "Rojo" to Color.parseColor("#FF3333"),
            "Azul" to Color.parseColor("#00FFFF"),
            "Ambar" to Color.parseColor("#FFBF00")
        )
        val options = colors.keys.toTypedArray()
        val currentIndex = options.indexOfFirst { colors[it] == currentColor }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Color de caracteres")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                onSave(colors[options[which]]!!)
                dialog.dismiss()
            }
            .show()
    }

    private fun restartCamera() {
        cameraProvider?.unbindAll() // Desvincula cámara actual
        startCamera() // Vuelve a vincular con la nueva configuración
    }
}