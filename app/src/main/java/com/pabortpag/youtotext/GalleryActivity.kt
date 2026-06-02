package com.pabortpag.youtotext

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.pabortpag.youtotext.data.room.YouToTextDatabase
import com.pabortpag.youtotext.databinding.ActivityGalleryBinding
import com.pabortpag.youtotext.ui.adapter.GalleryAdapter
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModel
import com.pabortpag.youtotext.ui.viewmodel.GalleryViewModelFactory
import kotlinx.coroutines.launch

// Activity que muestra la galería de capturas ASCII guardadas
class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var galleryViewModel: GalleryViewModel
    private lateinit var galleryAdapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        hideSystemBars()

        setupViewModel()
        setupRecyclerView()
        observeGalleryRecords()
    }

    // Inicializa el ViewModel con el DAO de la base de datos
    private fun setupViewModel() {
        val dao = YouToTextDatabase.getInstance(this).asciiDao()
        galleryViewModel = ViewModelProvider(this, GalleryViewModelFactory(dao))
            .get(GalleryViewModel::class.java)
    }

    // Configura el RecyclerView con un GridLayoutManager de 2 columnas
    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        galleryAdapter = GalleryAdapter { record ->
            val intent = Intent(this, AsciiDetailActivity::class.java).apply {
                putExtra("RECORD_ID", record.id)
            }
            startActivity(intent)
        }
        binding.recyclerView.adapter = galleryAdapter
    }

    // Observa los cambios en la galería y alterna entre el RecyclerView y el estado vacío
    private fun observeGalleryRecords() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                galleryViewModel.galleryRecords.collect { records ->
                    toggleEmptyState(records.isEmpty())
                    galleryAdapter.submitList(records)
                }
            }
        }
    }

    // Muestra el mensaje de galería vacía o el RecyclerView según corresponda
    private fun toggleEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    // Oculta las barras de sistema para modo inmersivo
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, binding.root)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}