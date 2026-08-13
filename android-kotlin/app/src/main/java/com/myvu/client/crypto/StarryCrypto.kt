package com.myvu.client.crypto

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object StarryCrypto {
    const val SYMMETRIC_V1_CBC = 1
    const val SYMMETRIC_V2_CTR = 2
    const val SYMMETRIC_V3_GCM = 3

    @JvmStatic
    fun generateIv(): ByteArray {
        val hex = UUID.randomUUID().toString().replace("-", "")
        return hex.substring(0, 16).toByteArray(StandardCharsets.US_ASCII)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray, mode: Int): ByteArray {
        return cipher(Cipher.ENCRYPT_MODE, plaintext, key, iv, mode)
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray, mode: Int): ByteArray {
        return cipher(Cipher.DECRYPT_MODE, ciphertext, key, iv, mode)
    }

    private fun cipher(opmode: Int, input: ByteArray, key: ByteArray, iv: ByteArray, mode: Int): ByteArray {
        val keySpec = SecretKeySpec(key, "AES")
        val c = when (mode) {
            SYMMETRIC_V1_CBC -> {
                Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(opmode, keySpec, IvParameterSpec(iv))
                }
            }
            SYMMETRIC_V2_CTR -> {
                Cipher.getInstance("AES/CTR/NoPadding").apply {
                    init(opmode, keySpec, IvParameterSpec(iv))
                }
            }
            else -> {
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(opmode, keySpec, GCMParameterSpec(128, iv))
                }
            }
        }
        return c.doFinal(input)
    }
}
