package com.pabortpag.youtotext.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

// Gestor centralizado de preferencias de usuario (Singleton)
object SettingsPrefs {

    private lateinit var prefs: SharedPreferences
    private const val PREF_NAME = "youtotext_prefs"

    // Claves para almacenar las preferencias
    private const val BLOCK_FACTOR_KEY = "block_factor"
    private const val PALETTE_KEY = "palette"
    private const val COLOR_KEY = "color"
    private const val ORIGINAL_COLOR_MODE_KEY = "original_color"
    private const val INVERT_PALETTE_KEY = "invert_palette"

    // Inicializa el gestor con el contexto de la aplicación
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // === LECTURA DE PREFERENCIAS ===

    // Obtiene el tamaño de bloque actual (densidad de caracteres)
    fun getBlockFactor(): Int = prefs.getInt(BLOCK_FACTOR_KEY, 12)

    // Obtiene la paleta de caracteres actual
    fun getPalette(): String = prefs.getString(PALETTE_KEY, "@%#*+=-:. ") ?: "@%#*+=-:. "

    // Obtiene el color base actual (verde por defecto: 0xFF00FF00)
    fun getColor(): Int = prefs.getInt(COLOR_KEY, Color.GREEN)

    // Comprueba si está activo el modo de color original (píxel real)
    fun isOriginalColorMode(): Boolean = prefs.getBoolean(ORIGINAL_COLOR_MODE_KEY, false)

    fun isPaletteInverted(): Boolean = prefs.getBoolean(INVERT_PALETTE_KEY, false)

    // === ESCRITURA DE PREFERENCIAS ===

    // Actualiza el tamaño de bloque (limitado entre 4 y 24)
    fun updateBlockFactor(factor: Int) {
        prefs.edit().putInt(BLOCK_FACTOR_KEY, factor.coerceIn(4, 24)).apply()
    }

    // Actualiza la paleta de caracteres
    fun updatePalette(palette: String) {
        prefs.edit().putString(PALETTE_KEY, palette).apply()
    }

    // Actualiza el color base
    fun updateColor(color: Int) {
        prefs.edit().putInt(COLOR_KEY, color).apply()
    }

    // Activa o desactiva el modo de color original
    fun setOriginalColorMode(enabled: Boolean) {
        prefs.edit().putBoolean(ORIGINAL_COLOR_MODE_KEY, enabled).apply()
    }

    fun setPaletteInverted(inverted: Boolean) {
        prefs.edit().putBoolean(INVERT_PALETTE_KEY, inverted).apply()
    }
}