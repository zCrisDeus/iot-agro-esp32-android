package com.example.myapplication.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionCryptoManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "agro_iot_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun guardarSesionCifrada(uid: String, email: String, rol: String) {
        securePrefs.edit()
            .putString("uid", uid)
            .putString("email", email)
            .putString("rol", rol)
            .apply()
    }

    fun obtenerRol(): String = securePrefs.getString("rol", "operador") ?: "operador"

    fun obtenerEmail(): String = securePrefs.getString("email", "") ?: ""

    fun limpiarSesion() {
        securePrefs.edit().clear().apply()
    }

    fun hayConexionInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}