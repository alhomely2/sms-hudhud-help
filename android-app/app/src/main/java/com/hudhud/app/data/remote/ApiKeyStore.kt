package com.hudhud.app.data.remote

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
 * Stores the API key encrypted with an AES key held by the Android Keystore.
 *
 * No secret is ever written to the source tree, and the key material cannot be
 * read back from the preferences file alone.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(siteId: String, apiKey: String): Boolean =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
            val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
            prefs.edit().putString(payloadKey(siteId), payload).apply()
            true
        } catch (e: Exception) {
            false
        }

    fun save(apiKey: String): Boolean = save(DEFAULT_SITE_ID, apiKey)

    fun load(siteId: String): String? {
        return try {
            var payload = prefs.getString(payloadKey(siteId), null)
            // Backwards compatibility migration: if loading default site and per-site key is not yet set,
            // check legacy PAYLOAD_KEY and migrate.
            if (payload == null && siteId == DEFAULT_SITE_ID) {
                payload = prefs.getString(PAYLOAD_KEY, null)
                if (payload != null) {
                    prefs.edit()
                        .putString(payloadKey(siteId), payload)
                        .remove(PAYLOAD_KEY)
                        .apply()
                }
            }
            if (payload == null) return null
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            if (bytes.size <= IV_SIZE) return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE),
            )
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun load(): String? = load(DEFAULT_SITE_ID)

    fun clear(siteId: String) {
        val editor = prefs.edit().remove(payloadKey(siteId))
        if (siteId == DEFAULT_SITE_ID) {
            editor.remove(PAYLOAD_KEY)
        }
        editor.apply()
    }

    fun clear() {
        clear(DEFAULT_SITE_ID)
    }

    /** Safe display form: never shows the secret half of the key. */
    fun masked(siteId: String): String? {
        val key = load(siteId) ?: return null
        val id = key.substringBefore('.')
        return if (id.length <= 3) "$id••••" else "${id.take(4)}••••"
    }

    fun masked(): String? = masked(DEFAULT_SITE_ID)

    fun hasKey(siteId: String): Boolean = load(siteId)?.isNotBlank() == true

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hudhud_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES_NAME = "hudhud_secure"
        const val PAYLOAD_KEY = "api_key_payload"
        const val DEFAULT_SITE_ID = "default_staging"
        const val IV_SIZE = 12
        const val TAG_BITS = 128

        fun payloadKey(siteId: String): String = "api_key_payload_${siteId.trim().lowercase()}"
    }
}
