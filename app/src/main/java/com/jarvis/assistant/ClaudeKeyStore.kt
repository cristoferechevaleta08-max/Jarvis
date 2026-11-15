package com.jarvis.assistant

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal object ClaudeKeyStore {
    private const val FILE_NAME = "jarvis_secure_secrets"
    private const val KEY_NAME = "anthropic_api_key"

    private fun preferences(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun get(context: Context): String =
        runCatching { preferences(context).getString(KEY_NAME, "").orEmpty() }.getOrDefault("")

    fun save(context: Context, value: String) {
        preferences(context).edit().putString(KEY_NAME, value.trim()).apply()
    }
}
