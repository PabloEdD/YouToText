package com.pabortpag.youtotext

import android.Manifest
import android.content.ContentValues
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

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Request camera permissions
        if(allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set UP THE LISTENERS FOR TAKE PHOTO AND VIDEO CAPTURE BUTTONS
        binding.imageCaptureButton.setOnClickListener { takePhoto() }
        binding.videoCaptureButton.setOnClickListener { captureVideo() }

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
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YouToText-Images")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
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

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
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
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
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
                        gridListener = { grid, width, height ->
                            asciiViewModel.onGridReceived(grid, width, height)
                            Log.d(TAG, "ASCII grid ready: ${width}x${height} = ${grid.size} celdas")
                        },
                        blockFactor = 4 // Ajusta según rendimiento: 2=más detalle, 6=más FPS
                    ))
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
//                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture, imageAnalysis)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
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
                if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    private class LuminosityAnalyzer(
        private val listener: LumaListener? = null,
        private val gridListener: LuminanceGridListener? = null,
        private val blockFactor: Int = 4
    ) : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            try {
                val yBuffer = image.planes[0].buffer
                val yRowStride = image.planes[0].rowStride
                val yPixelStride = image.planes[0].pixelStride
                val srcWidth = image.width
                val srcHeight = image.height

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

                // 🔹 NUEVO: Generar grilla para ASCII (solo si hay listener registrado)
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

                    gridListener(grid, outWidth, outHeight)
                }
            } finally {
                image.close()
            }
        }
    }
}