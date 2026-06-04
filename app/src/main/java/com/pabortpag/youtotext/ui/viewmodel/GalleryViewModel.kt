package com.pabortpag.youtotext.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pabortpag.youtotext.data.room.AsciiDao
import com.pabortpag.youtotext.data.room.AsciiRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ViewModel que gestiona la galería de capturas ASCII guardadas
class GalleryViewModel(private val asciiDao: AsciiDao) : ViewModel() {

    // Flujo reactivo con todos los registros de la galería (ordenados por fecha)
    val galleryRecords: Flow<List<AsciiRecord>> = asciiDao.getAllRecords()

    // Guarda una nueva captura ASCII en la base de datos
    fun saveAsciiCapture(
        asciiText: String,
        baseColor: Int,
        blockFactor: Int,
        characterPalette: String,
        isOriginalColor: Boolean,
        frameColors: IntArray?
    ) {
        viewModelScope.launch {
            val colorsString = if (isOriginalColor && frameColors != null) {
                frameColors.joinToString(",")
            } else null

            val newRecord = AsciiRecord(
                asciiText = asciiText,
                baseColor = baseColor,
                blockFactor = blockFactor,
                characterPalette = characterPalette,
                isOriginalColor = isOriginalColor,
                colorsString = colorsString
            )
            asciiDao.insertRecord(newRecord)
        }
    }

    // Elimina un registro específico de la galería
    fun deleteRecord(record: AsciiRecord) {
        viewModelScope.launch {
            asciiDao.deleteRecord(record)
        }
    }

    // Obtiene un registro específico por su identificador único
    suspend fun getRecordById(id: Long): AsciiRecord? {
        return asciiDao.getRecordById(id)
    }
}

// Factory para inyección manual del DAO en el ViewModel
class GalleryViewModelFactory(
    private val asciiDao: AsciiDao
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(asciiDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}