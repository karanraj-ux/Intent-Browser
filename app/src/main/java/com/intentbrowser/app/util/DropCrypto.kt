package com.intentbrowser.app.util

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Drop end-to-end encryption.
 *
 * - ECDH on P-256: the phone and the paired browser each generate an ephemeral
 *   keypair and exchange public keys over the PIN-gated session.
 * - The 32-byte shared secret goes through HKDF-SHA256 (salt "intent-beam-v1",
 *   info "beam-aes-gcm") to derive an AES-256-GCM key. The web client does the
 *   same derivation with WebCrypto, so both sides agree without ever sending
 *   the key.
 * - Every payload is AES-GCM with a fresh 12-byte IV: "base64(iv):base64(ct)".
 *
 * Threat model (honest version): the PIN (shown on the phone screen) stops an
 * active attacker from pairing; ECDH stops a passive LAN sniffer from reading
 * dropped content. The server only ever relays ciphertext.
 */
object DropCrypto {

    private const val HKDF_SALT = "intent-beam-v1"
    private const val HKDF_INFO = "beam-aes-gcm"

    /** ASN.1 prefix that turns a 65-byte raw P-256 key into an X.509 encoding. */
    private val X509_PREFIX = byteArrayOf(
        0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86,
        0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A,
        0x86, 0x48, 0xCE.toByte(), 0x3D, 0x03, 0x01, 0x07, 0x03,
        0x42, 0x00
    )

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        return kpg.generateKeyPair()
    }

    /** Raw uncompressed public key (65 bytes, 0x04 || x || y) as base64.
     *  Matches WebCrypto exportKey('raw'). */
    fun publicKeyToBase64(pair: KeyPair): String {
        val x509 = pair.public.encoded
        // For P-256 the raw key is the last 65 bytes of the X.509 encoding.
        val raw = x509.takeLast(65).toByteArray()
        require(raw.size == 65 && raw[0] == 0x04.toByte()) { "unexpected key encoding" }
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    fun deriveAesKey(ownPair: KeyPair, peerPublicB64: String): SecretKeySpec {
        val raw = Base64.decode(peerPublicB64.trim(), Base64.DEFAULT)
        require(raw.size == 65 && raw[0] == 0x04.toByte()) { "bad peer public key" }
        val peerKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(X509_PREFIX + raw))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ownPair.private)
        ka.doPhase(peerKey, true)
        val sharedSecret = ka.generateSecret()
        return SecretKeySpec(hkdfSha256(sharedSecret), "AES")
    }

    /** RFC 5869 HKDF, SHA-256, output length 32. */
    private fun hkdfSha256(ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HKDF_SALT.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(HKDF_INFO.toByteArray(Charsets.UTF_8))
        mac.update(0x01.toByte())
        return mac.doFinal().copyOf(32)
    }

    fun encrypt(key: SecretKeySpec, plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(key: SecretKeySpec, payload: String): String {
        val parts = payload.split(":")
        require(parts.size == 2) { "bad payload" }
        val iv = Base64.decode(parts[0], Base64.DEFAULT)
        val ct = Base64.decode(parts[1], Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
