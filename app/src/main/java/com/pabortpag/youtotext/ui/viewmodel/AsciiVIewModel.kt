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

class AsciiViewModel : ViewModel() {
    private val _asciiFrame = MutableStateFlow("")
    val asciiFrame: StateFlow<String> = _asciiFrame.asStateFlow()

    var currentAscii: String = ""
        private set
    var currentColors: IntArray? = null // ✅ Array de colores (nullable)
    var usePixelColor = false           // ✅ Toggle del menú
    var baseColor = -16711936           // ✅ Color por defecto

    fun onGridReceived(grid: ByteArray, colors: IntArray?, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val palette = SettingsPrefs.getPalette()
            val ascii = AsciiMapper.mapToAscii(grid, width, height, palette)
            _asciiFrame.value = ascii
            currentAscii = ascii
            currentColors = colors // Se guarda para la View
        }
    }
}