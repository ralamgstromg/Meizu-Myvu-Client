package com.myvu.client.crypto

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

class EcKeyPair private constructor(private val keyPair: KeyPair) {

    fun publicSpkiDer(): ByteArray = keyPair.public.encoded

    fun privateKey(): PrivateKey = keyPair.private

    @Throws(GeneralSecurityException::class)
    fun sharedSecret(peerPubSpkiDer: ByteArray): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(keyPair.private)
        ka.doPhase(parsePublicSpkiDer(peerPubSpkiDer), true)
        return ka.generateSecret()
    }

    companion object {
        @JvmStatic
        @Throws(GeneralSecurityException::class)
        fun generate(): EcKeyPair {
            val g = KeyPairGenerator.getInstance("EC")
            g.initialize(ECGenParameterSpec("secp256r1"))
            return EcKeyPair(g.generateKeyPair())
        }

        @JvmStatic
        @Throws(GeneralSecurityException::class)
        fun parsePublicSpkiDer(spkiDer: ByteArray): PublicKey {
            return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiDer))
        }
    }
}
