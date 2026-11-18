package pl.footballaipro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the API key encrypted with an AES-256 key held by Android Keystore.
 * The key itself is non-exportable and never written to the repository.
 */
object ApiFootballSettings {
    private const val PREFS = "football_ai_api_settings"
    private const val KEY_API = "api_football_key"
    private const val KEY_ALIAS = "football_ai_api_key_v1"
    private const val ENCRYPTED_PREFIX = "v1:"
    private const val GCM_TAG_BITS = 128

    @Synchronized
    fun getApiKey(context: Context): String {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY_API, "").orEmpty()
        if (stored.isBlank()) return ""

        if (stored.startsWith(ENCRYPTED_PREFIX)) {
            return runCatching { decrypt(stored.removePrefix(ENCRYPTED_PREFIX)) }
                .getOrElse {
                    // A restored preference without its device Keystore key cannot be
                    // decrypted. Ask the user to enter the key again rather than crash.
                    preferences.edit().remove(KEY_API).apply()
                    ""
                }
        }

        // One-time migration from the previous plaintext preference format.
        return runCatching {
            val encrypted = ENCRYPTED_PREFIX + encrypt(stored)
            check(preferences.edit().putString(KEY_API, encrypted).commit()) {
                "Nie udało się zapisać zaszyfrowanego klucza."
            }
            stored
        }.getOrElse { stored }
    }

    @Synchronized
    fun saveApiKey(context: Context, apiKey: String) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            preferences.edit().remove(KEY_API).apply()
            return
        }

        val encrypted = ENCRYPTED_PREFIX + encrypt(normalized)
        check(preferences.edit().putString(KEY_API, encrypted).commit()) {
            "Nie udało się zapisać klucza API."
        }
    }

    fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotBlank()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$iv:$payload"
    }

    private fun decrypt(value: String): String {
        val separator = value.indexOf(':')
        require(separator > 0 && separator < value.lastIndex) {
            "Nieprawidłowy zapis klucza API."
        }
        val iv = Base64.decode(value.substring(0, separator), Base64.NO_WRAP)
        val encrypted = Base64.decode(value.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
