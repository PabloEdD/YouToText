package com.pabortpag.youtotext.data

import android.content.Context
import android.content.SharedPreferences


object SettingsPrefs {
    private lateinit var prefs: SharedPreferences
    private const val PREF_NAME = "youtotext_prefs"

    private val BLOCK_FACTOR_KEY = "block_factor"
    private val PALETTE_KEY = "palette"
    private val COLOR_KEY = "color"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Lectura directa (síncrona, nativa)
    fun getBlockFactor() = prefs.getInt(BLOCK_FACTOR_KEY, 12)
    fun getPalette() = prefs.getString(PALETTE_KEY, "@%#*+=-:. ") ?: "@%#*+=-:. "
    fun getColor() = prefs.getInt(COLOR_KEY, -16711936)

    // Escritura directa
    fun updateBlockFactor(factor: Int) {
        prefs.edit().putInt(BLOCK_FACTOR_KEY, factor.coerceIn(4, 24)).apply()
    }

    fun updatePalette(palette: String) {
        prefs.edit().putString(PALETTE_KEY, palette).apply()
    }

    fun updateColor(color: Int) {
        prefs.edit().putInt(COLOR_KEY, color).apply()
    }
}