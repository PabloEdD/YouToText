package com.pabortpag.youtotext.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pabortpag.youtotext.util.AsciiCanvasRenderer
import com.pabortpag.youtotext.data.room.AsciiRecord
import com.pabortpag.youtotext.databinding.ItemGalleryBinding
import kotlinx.coroutines.*

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
            CoroutineScope(Dispatchers.IO).launch {
                val bmp = AsciiCanvasRenderer.renderToBitmap(record.asciiText, record.color, 400, 300)
                withContext(Dispatchers.Main) {
                    binding.ivPreview.setImageBitmap(bmp)
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