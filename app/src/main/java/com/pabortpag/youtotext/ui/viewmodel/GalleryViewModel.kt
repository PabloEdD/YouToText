package com.pabortpag.youtotext.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pabortpag.youtotext.data.room.AsciiDao
import com.pabortpag.youtotext.data.room.AsciiRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class GalleryViewModel(private val dao: AsciiDao) : ViewModel() {
    val galleryItems: Flow<List<AsciiRecord>> = dao.getAllRecords()

    fun saveRecord(text: String, color: Int, density: Int, palette: String) {
        viewModelScope.launch {
            dao.insertRecord(AsciiRecord(asciiText = text, color = color, density = density, palette = palette))
        }
    }

    fun deleteRecord(record: AsciiRecord) {
        viewModelScope.launch { dao.deleteRecord(record) }
    }
}

// Factory simple para inyección manual
class GalleryViewModelFactory(private val dao: AsciiDao) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}