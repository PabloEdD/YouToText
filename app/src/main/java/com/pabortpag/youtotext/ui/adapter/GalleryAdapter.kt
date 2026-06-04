package com.pabortpag.youtotext.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pabortpag.youtotext.data.room.AsciiRecord
import com.pabortpag.youtotext.databinding.ItemGalleryBinding
import com.pabortpag.youtotext.util.AsciiCanvasRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryAdapter(
    private val onItemClick: (AsciiRecord) -> Unit
) : ListAdapter<AsciiRecord, GalleryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemGalleryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: AsciiRecord) {
            // Scope seguro e independiente para evitar problemas de LifecycleOwner en el Adapter
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            scope.launch {
                try {
                    val bmp = if (record.isOriginalColor && !record.colorsString.isNullOrEmpty()) {
                        // Si es modo original, deserializamos el array y lo pasamos al renderer
                        val colorsArray = record.colorsString.split(",").map { it.toInt() }.toIntArray()
                        AsciiCanvasRenderer.renderPreviewBitmap(
                            asciiText = record.asciiText,
                            baseColor = record.baseColor,
                            targetWidthPx = 250,
                            targetHeightPx = 500,
                            isOriginalColor = true,
                            colors = colorsArray
                        )
                    } else {
                        // Si es color sólido, usamos el método normal
                        AsciiCanvasRenderer.renderPreviewBitmap(
                            asciiText = record.asciiText,
                            baseColor = record.baseColor,
                            targetWidthPx = 250,
                            targetHeightPx = 500
                        )
                    }

                    withContext(Dispatchers.Main) {
                        binding.ivPreview.setImageBitmap(bmp)
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // Si hay error, lo verás en el Logcat
                }
            }

            binding.root.setOnClickListener { onItemClick(record) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<AsciiRecord>() {
        override fun areItemsTheSame(old: AsciiRecord, new: AsciiRecord) = old.id == new.id
        override fun areContentsTheSame(old: AsciiRecord, new: AsciiRecord) = old == new
    }
}