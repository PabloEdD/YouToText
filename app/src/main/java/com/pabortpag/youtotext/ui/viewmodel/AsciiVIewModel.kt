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

    // Configuración UI (persistir con DataStore en Fase 3)
    var invertColors = false

    /**
     * Recibe la grilla del analyzer, procesa en hilo IO/Default y actualiza StateFlow.
     */
    fun onGridReceived(grid: ByteArray, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val ascii = AsciiMapper.mapToAscii(grid, width, height, invertColors)
            _asciiFrame.value = ascii
        }
    }
}