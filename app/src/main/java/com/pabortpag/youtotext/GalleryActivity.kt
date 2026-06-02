package com.pabortpag.youtotext

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.pabortpag.youtotext.data.room.YouToTextDatabase
import com.pabortpag.youtotext.databinding.ActivityGalleryBinding
import com.pabortpag.youtotext.ui.adapter.GalleryAdapter
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModel
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModelFactory
import kotlinx.coroutines.launch

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var viewModel: GalleryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, binding.root)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val dao = YouToTextDatabase.getInstance(this).asciiDao()
        viewModel = androidx.lifecycle.ViewModelProvider(this, GalleryViewModelFactory(dao))
            .get(GalleryViewModel::class.java)

        // 🔹 Grid de 2 columnas
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        val adapter = GalleryAdapter { record ->
            Intent(this, AsciiDetailActivity::class.java).apply {
                putExtra("RECORD_ID", record.id)
            }.also { startActivity(it) }
        }
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.galleryItems.collect { list ->
                if (list.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }
                adapter.submitList(list)
            }
        }
    }
}