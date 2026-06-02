package com.pabortpag.youtotext.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionUtils(private val activity: AppCompatActivity) {

    // Permisos necesarios: Solo cámara (y almacenamiento en APIs <= 28)
    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    // Launcher que gestiona la respuesta del usuario (conceder o denegar)
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = requiredPermissions.all {
                permissions[it] == true
            }
            onPermissionsResult(allGranted)
        }

    // Callback para notificar a la Activity si se concedieron o no
    var onPermissionsResult: (Boolean) -> Unit = {}

    // Comprueba si ya tenemos todos los permisos necesarios
    fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(
                activity,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Lanza el diálogo nativo de petición de permisos
    fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }
}