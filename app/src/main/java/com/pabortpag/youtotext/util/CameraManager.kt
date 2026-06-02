package com.pabortpag.youtotext.util

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pabortpag.youtotext.databinding.ActivityMainBinding
import com.pabortpag.youtotext.domain.LuminosityAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService

    var useFrontCamera: Boolean = false
    var onGridReceived: (ByteArray, IntArray?, Int, Int) -> Unit = { _, _, _, _ -> }

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    // Inicia la cámara con Preview + ImageAnalysis (sin vídeo ni fotos normales)
    fun startCamera(blockFactor: Int, extractColors: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview: muestra el flujo de la cámara en el viewFinder
            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = binding.viewFinder.surfaceProvider }

            // ImageAnalysis: extrae frames YUV para convertirlos a ASCII
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(480, 640),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        LuminosityAnalyzer(
                            blockFactor = blockFactor,
                            mirrorHorizontally = useFrontCamera,
                            extractColors = extractColors,
                            gridListener = { grid, colors, width, height ->
                                onGridReceived(grid, colors, width, height)
                            }
                        )
                    )
                }

            // Selección de cámara (frontal o trasera)
            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                // Desvincula casos de uso anteriores antes de re-vincular
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Reinicia la cámara aplicando una nueva configuración
    fun restartCamera(blockFactor: Int, extractColors: Boolean) {
        cameraProvider?.unbindAll()
        startCamera(blockFactor, extractColors)
    }

    // Libera el executor al destruir la actividad
    fun shutdown() {
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}