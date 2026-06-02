package com.pabortpag.youtotext.util

import android.graphics.Color
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pabortpag.youtotext.R
import com.pabortpag.youtotext.data.SettingsPrefs

// Muestra el diálogo para ajustar la densidad de caracteres (blockFactor)
fun AppCompatActivity.showDensityDialog(onDensityChanged: (Int) -> Unit) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_density, null)
    val tvDensityValue = dialogView.findViewById<TextView>(R.id.tvDensityValue)
    val seekBar = dialogView.findViewById<SeekBar>(R.id.seekBarDensity)

    // Cargar valor actual y ajustar al rango del SeekBar (0-20 representa 4-24)
    val currentDensity = SettingsPrefs.getBlockFactor()
    seekBar.progress = currentDensity - 4
    tvDensityValue.text = "Tamaño de bloque: $currentDensity"

    // Actualizar el texto en tiempo real mientras se mueve la barra
    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val realValue = progress + 4
            tvDensityValue.text = "Tamaño de bloque: $realValue"
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    AlertDialog.Builder(this)
        .setView(dialogView)
        .setPositiveButton("Aplicar") { dialog, _ ->
            val newDensity = seekBar.progress + 4
            SettingsPrefs.updateBlockFactor(newDensity)
            onDensityChanged(newDensity)
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

// Muestra el diálogo para seleccionar la paleta de caracteres
fun AppCompatActivity.showPaletteDialog(onPaletteChanged: (String, Boolean) -> Unit) {
    val palettes = mapOf(
        "Clásica" to "@%#*+=-:. ",
        "Denso" to "\$@B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~<>i!lI;:,\"^`'. ",
        "Cubos" to "█▓▒░ ",
        "Cubos Finos" to "▇▆▅▄▃▂▁ ",
        "Braille" to "⣿⣶⣤⣀⠄⠂⠁ ",
        "Círculos" to "⬤◉◎○ ",
        "Líneas" to "█▌▎▏ ",
        "Código" to "01 ",
    )

    val dialogView = layoutInflater.inflate(R.layout.dialog_palette, null)
    val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupPalettes)
    val cbInvert = dialogView.findViewById<CheckBox>(R.id.cbInvertPalette)

    val currentPalette = SettingsPrefs.getPalette()
    val isCurrentlyInverted = SettingsPrefs.isPaletteInverted()
    cbInvert.isChecked = isCurrentlyInverted

    // Variable para guardar el ID del RadioButton seleccionado
    var selectedRadioButtonId = -1

    // Lista para guardar referencias a los RadioButtons
    val radioButtons = mutableListOf<RadioButton>()

    // Generar RadioButtons dinámicamente con vista previa
    palettes.toList().forEachIndexed { index, (name, chars) ->
        val radioButton = RadioButton(this).apply {
            id = index + 1000
            text = "$name\n($chars)"
            setPadding(32, 24, 32, 24)
            textSize = 15f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF00"))

            if (currentPalette == chars) {
                isChecked = true
                selectedRadioButtonId = id
            }

            setOnClickListener {
                selectedRadioButtonId = id
            }
        }
        radioGroup.addView(radioButton)
        radioButtons.add(radioButton)
    }

    AlertDialog.Builder(this)
        .setView(dialogView)
        .setPositiveButton("Aplicar") { dialog, _ ->
            // Usar el ID guardado en lugar de checkedRadioButtonId
            val selectedIndex = selectedRadioButtonId - 1000
            val selectedPalette = if (selectedIndex >= 0 && selectedIndex < palettes.size) {
                palettes.values.elementAt(selectedIndex)
            } else {
                currentPalette // Si no hay selección válida, mantener la actual
            }

            val shouldInvert = cbInvert.isChecked

            // Guardar preferencias
            SettingsPrefs.updatePalette(selectedPalette)
            SettingsPrefs.setPaletteInverted(shouldInvert)

            // Notificar al MainActivity
            onPaletteChanged(selectedPalette, shouldInvert)
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

// Muestra el diálogo para configurar el color (RGB personalizado o color original)
fun AppCompatActivity.showColorDialog(onColorChanged: (Int) -> Unit) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_color_native, null)

    val cbOriginal = dialogView.findViewById<CheckBox>(R.id.cbOriginalColor)
    val rgbContainer = dialogView.findViewById<LinearLayout>(R.id.rgbContainer)
    val seekR = dialogView.findViewById<SeekBar>(R.id.seekR)
    val seekG = dialogView.findViewById<SeekBar>(R.id.seekG)
    val seekB = dialogView.findViewById<SeekBar>(R.id.seekB)
    val tvR = dialogView.findViewById<TextView>(R.id.tvR)
    val tvG = dialogView.findViewById<TextView>(R.id.tvG)
    val tvB = dialogView.findViewById<TextView>(R.id.tvB)
    val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)
    val tvHex = dialogView.findViewById<TextView>(R.id.tvHex)

    // Cargar estado actual
    val isOriginal = SettingsPrefs.isOriginalColorMode()
    val currentColor = SettingsPrefs.getColor()

    cbOriginal.isChecked = isOriginal
    rgbContainer.visibility = if (isOriginal) View.GONE else View.VISIBLE

    // Descomponer el color actual en componentes RGB
    seekR.progress = Color.red(currentColor)
    seekG.progress = Color.green(currentColor)
    seekB.progress = Color.blue(currentColor)

    updateColorPreview(colorPreview, tvHex, tvR, tvG, tvB, seekR.progress, seekG.progress, seekB.progress)

    // Listener compartido para actualizar la vista previa en tiempo real
    val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            updateColorPreview(colorPreview, tvHex, tvR, tvG, tvB, seekR.progress, seekG.progress, seekB.progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    seekR.setOnSeekBarChangeListener(seekBarListener)
    seekG.setOnSeekBarChangeListener(seekBarListener)
    seekB.setOnSeekBarChangeListener(seekBarListener)

    // Mostrar/ocultar los controles RGB según el modo seleccionado
    cbOriginal.setOnCheckedChangeListener { _, isChecked ->
        rgbContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
    }

    AlertDialog.Builder(this)
        .setTitle("Configurar Color")
        .setView(dialogView)
        .setPositiveButton("Aplicar") { dialog, _ ->
            if (cbOriginal.isChecked) {
                SettingsPrefs.setOriginalColorMode(true)
                onColorChanged(-1) // Valor especial para indicar modo original
            } else {
                SettingsPrefs.setOriginalColorMode(false)
                val selectedColor = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
                SettingsPrefs.updateColor(selectedColor)
                onColorChanged(selectedColor)
            }
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

// Actualiza la vista previa del color y los valores numéricos RGB
private fun updateColorPreview(
    preview: View, tvHex: TextView, tvR: TextView, tvG: TextView, tvB: TextView,
    r: Int, g: Int, b: Int
) {
    val color = Color.rgb(r, g, b)
    preview.setBackgroundColor(color)
    tvHex.text = String.format("#%02X%02X%02X", r, g, b)
    tvHex.setTextColor(color)
    tvR.text = r.toString()
    tvG.text = g.toString()
    tvB.text = b.toString()
}