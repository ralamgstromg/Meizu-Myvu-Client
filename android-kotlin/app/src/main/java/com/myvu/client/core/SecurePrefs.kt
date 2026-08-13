package com.myvu.client.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure KeyStore-backed wrapper for API keys and secrets stored in SharedPreferences.
 * Uses native Android KeyStore AES/GCM/NoPadding encryption.
 */
object SecurePrefs {
    private const val PREFS_NAME = "myvu_secure_prefs"
    private const val KEY_ALIAS = "myvu_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            LogBus.error("Failed to get or create KeyStore key", e)
            null
        }
    }

    @JvmStatic
    fun getSecret(context: Context, key: String, defaultValue: String): String {
        val encryptedValue = prefs(context).getString(key, null)
        if (encryptedValue.isNullOrEmpty()) {
            return defaultValue
        }
        return try {
            val secretKey = getOrCreateSecretKey() ?: return defaultValue

            val parts = encryptedValue.split(":")
            if (parts.size != 2) return defaultValue

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainText = cipher.doFinal(cipherText)
            String(plainText, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            LogBus.error("Failed to decrypt secret for key: $key", e)
            defaultValue
        }
    }

    @JvmStatic
    fun setSecret(context: Context, key: String, value: String?) {
        if (value.isNullOrEmpty()) {
            removeSecret(context, key)
            return
        }
        try {
            val secretKey = getOrCreateSecretKey()
            if (secretKey == null) {
                // Fallback to Base64 obscured if KeyStore fails
                val encoded = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                prefs(context).edit().putString(key, "raw:$encoded").apply()
                return
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))

            val formatted = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipherText, Base64.NO_WRAP)
            prefs(context).edit().putString(key, formatted).apply()
        } catch (e: Exception) {
            LogBus.error("Failed to encrypt secret for key: $key", e)
        }
    }

    @JvmStatic
    fun removeSecret(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }
}
