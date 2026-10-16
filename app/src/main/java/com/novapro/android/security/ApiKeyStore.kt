package com.novapro.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps user-provided provider API keys encrypted by Android Keystore. Keys stay on this device. */
object ApiKeyStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val PREFS = "nova_secure_settings"
    private const val LEGACY_GEMINI_ALIAS = "nova_gemini_api_key_v1"
    private const val LEGACY_GEMINI_PREF_KEY = "gemini_api_key_ciphertext"

    /** Backwards-compatible Gemini helpers for existing 0.3.1 settings. */
    fun save(context: Context, apiKey: String) = save(context, "Gemini", apiKey)
    fun load(context: Context): String = load(context, "Gemini")

    fun save(context: Context, provider: String, apiKey: String) {
        val key = prefKey(provider)
        if (apiKey.isBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(provider))
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, encoded).apply()
    }

    fun load(context: Context, provider: String): String {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefKey(provider)
        val encoded = preferences.getString(key, null) ?: return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > 12) { "Invalid encrypted key" }
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(provider), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun normalize(provider: String): String = provider.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "gemini" }

    private fun prefKey(provider: String): String = if (normalize(provider) == "gemini") LEGACY_GEMINI_PREF_KEY else "${normalize(provider)}_api_key_ciphertext"

    private fun alias(provider: String): String = if (normalize(provider) == "gemini") LEGACY_GEMINI_ALIAS else "nova_${normalize(provider)}_api_key_v1"

    private fun getOrCreateKey(provider: String): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(alias(provider), null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias(provider),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
