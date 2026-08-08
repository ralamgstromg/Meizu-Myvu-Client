package com.myvu.client.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Secure KeyStore-backed wrapper for API keys and secrets stored in SharedPreferences.
 * Uses native Android KeyStore AES/GCM/NoPadding encryption.
 */
public final class SecurePrefs {
    private static final String PREFS_NAME = "myvu_secure_prefs";
    private static final String KEY_ALIAS = "myvu_master_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    private SecurePrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static synchronized SecretKey getOrCreateSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
                if (entry != null) {
                    return entry.getSecretKey();
                }
            }

            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            LogBus.error("Failed to get or create KeyStore key", e);
            return null;
        }
    }

    public static String getSecret(Context context, String key, String defaultValue) {
        String encryptedValue = prefs(context).getString(key, null);
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return defaultValue;
        }
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            if (secretKey == null) return defaultValue;

            String[] parts = encryptedValue.split(":");
            if (parts.length != 2) return defaultValue;

            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] cipherText = Base64.decode(parts[1], Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LogBus.error("Failed to decrypt secret for key: " + key, e);
            return defaultValue;
        }
    }

    public static void setSecret(Context context, String key, String value) {
        if (value == null || value.isEmpty()) {
            removeSecret(context, key);
            return;
        }
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            if (secretKey == null) {
                // Fallback to Base64 obscured if KeyStore fails
                prefs(context).edit().putString(key, "raw:" + Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP)).apply();
                return;
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            String formatted = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipherText, Base64.NO_WRAP);
            prefs(context).edit().putString(key, formatted).apply();
        } catch (Exception e) {
            LogBus.error("Failed to encrypt secret for key: " + key, e);
        }
    }

    public static void removeSecret(Context context, String key) {
        prefs(context).edit().remove(key).apply();
    }
}
