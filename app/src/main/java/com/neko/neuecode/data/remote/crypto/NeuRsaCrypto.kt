package com.neko.neuecode.data.remote.crypto

import android.util.Base64
import com.neko.neuecode.data.remote.config.ProtocolConfig
import com.neko.neuecode.data.remote.config.RemoteProtocolConfigRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import timber.log.Timber
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RSA helper for NEU mobile-style encrypted requests.
 *
 * The public GitHub tree does not include protocol RSA material.  Distributed
 * APKs fetch the current protocol config from the app helper backend and cache it
 * in Android Keystore-backed storage. This keeps classmates' installs usable
 * while allowing key rotation and future update metadata from the same backend.
 */
@Singleton
class NeuRsaCrypto @Inject constructor(
    private val remoteConfigRepository: RemoteProtocolConfigRepository
) {

    companion object {
        private const val CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val RSA_1024_CIPHER_BLOCK_SIZE = 128
        private const val RSA_1024_PKCS1_MAX_PLAINTEXT = 117
    }

    @Volatile
    private var runtimeConfig: ProtocolConfig? = null

    /**
     * Encrypt login request content.
     *
     * JSON format:
     * {
     *   "username": "<username>",
     *   "password": "<password>",
     *   "imei": "<stable-device-id>",
     *   "mobile_type": "android"
     * }
     */
    fun encryptLoginContent(username: String, password: String, imei: String = stableImei()): String {
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("imei", imei)
            put("mobile_type", "android")
        }

        val plaintext = json.toString()
        Timber.d("Login content plaintext length: ${plaintext.length}")

        return encryptWithPublicKey(plaintext, loadPublicKey(currentConfig().rsaPublicKeyPem()))
    }

    fun encryptContentMap(values: Map<String, String>): String {
        val json = JSONObject().apply {
            values.forEach { (key, value) -> put(key, value) }
        }
        val plaintext = json.toString()
        Timber.d("Content map plaintext length: ${plaintext.length}")
        return encryptWithPublicKey(plaintext, loadPublicKey(currentConfig().rsaPublicKeyPem()))
    }

    /**
     * Encrypt authorization-str header.
     *
     * JSON format:
     * {
     *   "timestamp": "<seconds>",
     *   "ticket": "<login_ticket or empty>"
     * }
     */
    fun encryptAuthorizationStr(ticket: String = ""): String {
        val timestampSeconds = (System.currentTimeMillis() / 1000L).toString()
        val json = JSONObject().apply {
            put("timestamp", timestampSeconds)
            put("ticket", ticket)
        }

        val plaintext = json.toString()
            .replace("\n", "")
            .replace(" ", "")
        Timber.d("Authorization-str plaintext length: ${plaintext.length}")

        return encryptWithPublicKey(plaintext, loadPublicKey(currentConfig().rsaPublicKeyPem()))
    }

    /**
     * Stable app-side device identifier placeholder.
     *
     * Production forks should replace this with an install-scoped value stored in
     * app-private storage. It is deliberately non-identifying in the public tree.
     */
    fun stableImei(): String = "00000000000000000000000000000000"

    suspend fun warmUpRemoteConfig() {
        val config = remoteConfigRepository.getProtocolConfig(forceRefresh = false)
        runtimeConfig = config
        Timber.i("Protocol config warmed: keyVersion=${config.keyVersion}")
    }

    private fun currentConfig(): ProtocolConfig {
        runtimeConfig?.takeIf { it.isFresh() }?.let { return it }
        return runBlocking {
            remoteConfigRepository.getProtocolConfig(forceRefresh = false)
                .also { runtimeConfig = it }
        }
    }

    private fun encryptWithPublicKey(plaintext: String, publicKey: PublicKey): String {
        try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val encryptedChunks = mutableListOf<ByteArray>()

            var offset = 0
            while (offset < plaintextBytes.size) {
                val end = minOf(offset + RSA_1024_PKCS1_MAX_PLAINTEXT, plaintextBytes.size)
                val chunk = plaintextBytes.copyOfRange(offset, end)
                encryptedChunks += cipher.doFinal(chunk)
                offset = end
            }

            val ciphertext = encryptedChunks.flatMap { it.toList() }.toByteArray()
            Timber.d("RSA encryption: ${plaintextBytes.size} bytes -> ${ciphertext.size} bytes in ${encryptedChunks.size} block(s)")

            if (ciphertext.size % RSA_1024_CIPHER_BLOCK_SIZE != 0) {
                Timber.w("Unexpected ciphertext size: ${ciphertext.size}")
            }

            return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "RSA encryption failed")
            throw e
        }
    }

    private fun loadPublicKey(pemKey: String): PublicKey {
        val publicKeyPEM = pemKey
            .replace("-----BEGIN " + "PUBLIC KEY-----", "")
            .replace("-----END " + "PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val decoded = Base64.decode(publicKeyPEM, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance("RSA")

        return keyFactory.generatePublic(spec)
    }

    /**
     * Decrypt encrypted response payloads with the remotely fetched key set.
     */
    fun decryptResponse(encryptedBase64: String): String? {
        try {
            val config = currentConfig()
            val privateKeyPems = config.rsaPrivateKeysBase64.mapNotNull { it.decodePemOrNull() }
            if (privateKeyPems.isEmpty()) {
                Timber.w("No response private key configured; cannot decrypt response")
                return null
            }

            val encryptedData = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            Timber.d("Encrypted response: ${encryptedData.size} bytes")

            val numBlocks = encryptedData.size / RSA_1024_CIPHER_BLOCK_SIZE
            if (encryptedData.size % RSA_1024_CIPHER_BLOCK_SIZE != 0) {
                Timber.w("Encrypted data size ${encryptedData.size} is not block-aligned")
            }

            Timber.i("Response has $numBlocks RSA blocks")

            for ((index, privateKeyPem) in privateKeyPems.withIndex()) {
                try {
                    Timber.d("Trying configured private key #${index + 1}...")
                    val privateKey = loadPrivateKey(privateKeyPem)

                    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, privateKey)

                    val decryptedBlocks = mutableListOf<ByteArray>()
                    for (i in 0 until numBlocks) {
                        val offset = i * RSA_1024_CIPHER_BLOCK_SIZE
                        val block = encryptedData.copyOfRange(offset, offset + RSA_1024_CIPHER_BLOCK_SIZE)

                        try {
                            decryptedBlocks.add(cipher.doFinal(block))
                        } catch (e: Exception) {
                            Timber.w("Failed to decrypt block $i with configured key #${index + 1}: ${e.message}")
                            break
                        }
                    }

                    if (decryptedBlocks.size == numBlocks) {
                        val result = decryptedBlocks.flatMap { it.toList() }.toByteArray()
                        val decryptedText = String(result, Charsets.UTF_8)
                        Timber.i("Successfully decrypted with configured private key #${index + 1}")
                        Timber.d("Decrypted text length: ${decryptedText.length}")
                        return decryptedText
                    }
                } catch (e: Exception) {
                    Timber.w("Configured private key #${index + 1} failed: ${e.message}")
                }
            }

            Timber.e("All configured private keys failed to decrypt response")
            return null
        } catch (e: Exception) {
            Timber.e(e, "Response decryption failed")
            return null
        }
    }

    private fun loadPrivateKey(pemKey: String): java.security.PrivateKey {
        val privateKeyPEM = pemKey
            .replace("-----BEGIN.*PRIVATE KEY-----".toRegex(), "")
            .replace("-----END.*PRIVATE KEY-----".toRegex(), "")
            .replace("\\s".toRegex(), "")

        val decoded = Base64.decode(privateKeyPEM, Base64.DEFAULT)
        val spec = java.security.spec.PKCS8EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance("RSA")

        return keyFactory.generatePrivate(spec)
    }

    private fun ProtocolConfig.rsaPublicKeyPem(): String {
        return rsaPublicKeyBase64.decodePemOrNull()
            ?: error("Invalid remote RSA public key")
    }

    private fun String.decodePemOrNull(): String? {
        val clean = trim()
        if (clean.isBlank()) return null
        return try {
            String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode remote RSA PEM")
            null
        }
    }
}
