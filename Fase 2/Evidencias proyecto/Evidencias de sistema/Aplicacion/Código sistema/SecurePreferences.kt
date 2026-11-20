package com.sara.robot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Gestor de almacenamiento seguro respaldado por Android Keystore (AES-256).
 * Protege la API Key de Gemini contra accesos no autorizados.
 */
class SecurePreferences(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String {
        val storedKey = sharedPreferences.getString(KEY_GEMINI_API_KEY, "") ?: ""
        if (storedKey.isNotBlank()) {
            return storedKey
        }
        // Fallback a la clave inyectada desde el archivo .env vía BuildConfig
        return com.sara.robot.BuildConfig.GEMINI_API_KEY
    }

    fun setApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .apply()
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun getGoogleCloudApiKey(): String {
        val storedKey = sharedPreferences.getString(KEY_GOOGLE_CLOUD_API_KEY, "") ?: ""
        if (storedKey.isNotBlank()) {
            return storedKey
        }
        // Fallback a la clave inyectada desde el archivo .env vía BuildConfig
        val buildKey = com.sara.robot.BuildConfig.GOOGLE_CLOUD_API_KEY
        if (buildKey.isNotBlank()) {
            return buildKey
        }
        // Fallback a GEMINI_API_KEY si provienen del mismo proyecto de Google Cloud
        return getApiKey()
    }

    fun setGoogleCloudApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_GOOGLE_CLOUD_API_KEY, apiKey.trim())
            .apply()
    }

    fun hasGoogleCloudApiKey(): Boolean {
        return getGoogleCloudApiKey().isNotBlank()
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "sara_secure_vault"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key_secure"
        private const val KEY_GOOGLE_CLOUD_API_KEY = "google_cloud_api_key_secure"
    }
}

