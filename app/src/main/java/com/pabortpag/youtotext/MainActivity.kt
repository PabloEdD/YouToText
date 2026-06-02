package com.pabortpag.youtotext

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pabortpag.youtotext.data.SettingsPrefs
import com.pabortpag.youtotext.data.room.YouToTextDatabase
import com.pabortpag.youtotext.databinding.ActivityMainBinding
import com.pabortpag.youtotext.ui.viewmodel.AsciiViewModel
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModel
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModelFactory
import com.pabortpag.youtotext.util.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

//  __    __               ______        ______                __
// /\ \  /\ \             /\__  _\      /\__  _\              /\ \__
// \ `\`\\/'/ ___   __  __\/_/\ \/   ___\/_/\ \/    __   __  _\ \ ,_\
//  `\ `\ /' / __`\/\ \/\ \  \ \ \  / __`\ \ \ \  /'__`\/\ \/'\\ \ \/
//    `\ \ \/\ \L\ \ \ \_\ \  \ \ \/\ \L\ \ \ \ \/\  __/\/>  </ \ \ \_
//      \ \_\ \____/\ \____/   \ \_\ \____/  \ \_\ \____\/\_/\_\ \ \__\
//       \/_/\/___/  \/___/     \/_/\/___/    \/_/\/____/\//\/_/  \/__/

// Activity principal que orquesta la cámara, la UI y los ViewModels
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var asciiViewModel: AsciiViewModel
    private lateinit var galleryViewModel: GalleryViewModel
    private lateinit var cameraManager: CameraManager
    private lateinit var permissionUtils: PermissionUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuración inicial
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        SettingsPrefs.init(this)
        hideSystemBars()

        // Inicialización de ViewModels
        asciiViewModel = AsciiViewModel()
        val db = YouToTextDatabase.getInstance(this)
        galleryViewModel = ViewModelProvider(this, GalleryViewModelFactory(db.asciiDao()))
            .get(GalleryViewModel::class.java)

        // Aplicar color base guardado
        binding.asciiView.setBaseColor(SettingsPrefs.getColor())

        setupUI()
        setupCameraAndPermissions()
        setupAsciiObserver()
    }

    // Configura los listeners de los botones de la interfaz
    private fun setupUI() {
        // Botones de configuración
        binding.btnDensity.setOnClickListener {
            showDensityDialog { newFactor ->
                cameraManager.restartCamera(newFactor, SettingsPrefs.isOriginalColorMode())
            }
        }

        binding.btnPalette.setOnClickListener {
            showPaletteDialog { newPalette, isInverted ->
                cameraManager.restartCamera(
                    blockFactor = SettingsPrefs.getBlockFactor(),
                    extractColors = SettingsPrefs.isOriginalColorMode()
                )
            }
        }

        binding.btnColor.setOnClickListener {
            showColorDialog { color ->
                if (color != -1) {
                    // Color sólido personalizado
                    SettingsPrefs.setOriginalColorMode(false)
                    SettingsPrefs.updateColor(color)
                    binding.asciiView.setBaseColor(color)
                } else {
                    // Modo Color Original
                    SettingsPrefs.setOriginalColorMode(true)
                    cameraManager.restartCamera(SettingsPrefs.getBlockFactor(), true)
                }
            }
        }

        // Botones de acción y exportación
        binding.copyButton.setOnClickListener {
            val text = asciiViewModel.currentAsciiText
            if (text.isEmpty()) return@setOnClickListener

            if (copyAsciiToClipboard(this, text)) {
                galleryViewModel.saveAsciiCapture(
                    asciiText = text,
                    baseColor = SettingsPrefs.getColor(),
                    blockFactor = SettingsPrefs.getBlockFactor(),
                    characterPalette = SettingsPrefs.getPalette()
                )
            }
        }

        binding.photoButton.setOnClickListener {
            val text = asciiViewModel.currentAsciiText
            if (text.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                if (exportAsciiViewToPng(this@MainActivity, binding.asciiView)) {
                    galleryViewModel.saveAsciiCapture(
                        asciiText = text,
                        baseColor = SettingsPrefs.getColor(),
                        blockFactor = SettingsPrefs.getBlockFactor(),
                        characterPalette = SettingsPrefs.getPalette()
                    )
                    Toast.makeText(this@MainActivity, "Imagen guardada", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.switchCamButton.setOnClickListener {
            cameraManager.useFrontCamera = !cameraManager.useFrontCamera
            cameraManager.restartCamera(SettingsPrefs.getBlockFactor(), SettingsPrefs.isOriginalColorMode())
        }

        binding.btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }

    // Inicializa la cámara y gestiona los permisos
    private fun setupCameraAndPermissions() {
        permissionUtils = PermissionUtils(this)
        permissionUtils.onPermissionsResult = { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Permisos de cámara denegados", Toast.LENGTH_SHORT).show()
        }

        cameraManager = CameraManager(this, binding, this).apply {
            onGridReceived = { luminanceGrid, frameColors, gridWidth, gridHeight ->
                asciiViewModel.onGridReceived(luminanceGrid, frameColors, gridWidth, gridHeight)
            }
        }

        if (permissionUtils.hasAllPermissions()) {
            startCamera()
        } else {
            permissionUtils.requestPermissions()
        }
    }

    // Inicia la cámara con la configuración actual
    private fun startCamera() {
        cameraManager.startCamera(
            blockFactor = SettingsPrefs.getBlockFactor(),
            extractColors = SettingsPrefs.isOriginalColorMode()
        )
    }

    // Observa los frames ASCII generados por el ViewModel y los pinta en la vista
    private fun setupAsciiObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                asciiViewModel.asciiTextFlow.collectLatest { text ->
                    binding.asciiView.updateFrame(
                        text,
                        asciiViewModel.currentFrameColors,
                        SettingsPrefs.isOriginalColorMode()
                    )
                }
            }
        }
    }

    // Oculta las barras de sistema para modo inmersivo
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, binding.root)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
    }
}