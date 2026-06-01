package com.pabortpag.youtotext.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pabortpag.youtotext.domain.AsciiMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AsciiViewModel : ViewModel() {
    private val _asciiFrame = MutableStateFlow("")
    val asciiFrame: StateFlow<String> = _asciiFrame.asStateFlow()

    var currentAscii: String = ""
        private set // Cache del último frame ASCII procesado. Permite acceso rápido desde la UI (ej. botón copiar)

    // Configuración UI (persistir con DataStore en Fase 3)
    var invertColors = false

    /**
     * Recibe el grid del analyzer, procesa en hilo IO/Default y actualiza StateFlow.
     */
    fun onGridReceived(grid: ByteArray, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val palette = com.pabortpag.youtotext.data.SettingsPrefs.getPalette()

            val ascii = AsciiMapper.mapToAscii(
                grid, width, height,
                palette = palette, // 🔹 Pasar la paleta dinámica
                invert = invertColors
            )
            _asciiFrame.value = ascii
            currentAscii = ascii
        }
    }
}