package com.nearchat.app.core

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** ECDH (P-256) key agreement + AES-256-GCM for every frame. Keys live only in memory, per session. */
object Crypto {
    private val rnd = SecureRandom()

    fun newKeyPair(): KeyPair {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"))
        return g.generateKeyPair()
    }

    fun deriveKey(priv: PrivateKey, peerPublic: ByteArray): SecretKeySpec {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublic))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        val k = MessageDigest.getInstance("SHA-256").digest(ka.generateSecret())
        return SecretKeySpec(k, "AES")
    }

    /** 6-digit code both phones show. If the codes differ, someone is in the middle. */
    fun securityCode(key: SecretKeySpec): String {
        val h = MessageDigest.getInstance("SHA-256").digest(key.encoded + "nearchat-code".toByteArray())
        val n = (((h[0].toInt() and 0xff) shl 16) or ((h[1].toInt() and 0xff) shl 8) or (h[2].toInt() and 0xff)) % 1_000_000
        return n.toString().padStart(6, '0')
    }

    fun encrypt(key: SecretKeySpec, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    fun decrypt(key: SecretKeySpec, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }
}
