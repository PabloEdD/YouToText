package com.pabortpag.youtotext.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pabortpag.youtotext.data.SettingsPrefs
import com.pabortpag.youtotext.domain.AsciiMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ViewModel que gestiona el procesamiento de frames ASCII
class AsciiViewModel : ViewModel() {

    // === FLUJO REACTIVO PARA LA UI ===
    private val _asciiTextFlow = MutableStateFlow("")
    val asciiTextFlow: StateFlow<String> = _asciiTextFlow.asStateFlow()

    // === ESTADO ACTUAL DEL FRAME (accesible para la View) ===
    var currentAsciiText: String = ""
        private set
    var currentFrameColors: IntArray? = null
        private set

    // Procesa el grid de luminancia recibido y lo convierte a texto ASCII
    fun onGridReceived(
        luminanceGrid: ByteArray,
        frameColors: IntArray?,
        gridWidth: Int,
        gridHeight: Int
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val characterPalette = SettingsPrefs.getPalette()
            val isInverted = SettingsPrefs.isPaletteInverted()

            val asciiText = AsciiMapper.mapToAscii(
                luminanceGrid,
                gridWidth,
                gridHeight,
                characterPalette,
                invert = isInverted
            )

            // Actualiza el flujo reactivo y el estado actual
            _asciiTextFlow.value = asciiText
            currentAsciiText = asciiText
            currentFrameColors = frameColors
        }
    }
}