package com.max97k.pddf.util

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager(private val context: Context? = null) {
    private val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    private val KEY_ALIAS = "pdf_password_alias"
    private val PREFIX = "ENC_"

    companion object {
        // For testing ONLY across all instances
        var testKeyOverride: SecretKey? = null

        fun isStrongBoxSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        }
    }

    private fun getSecretKey(): SecretKey {
        if (testKeyOverride != null) return testKeyOverride!!

        val ks = try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        } catch (e: Exception) {
            throw RuntimeException("AndroidKeyStore unavailable", e)
        }

        return try {
            if (ks.containsAlias(KEY_ALIAS)) {
                (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()
            } else {
                generateKey()
            }
        } catch (e: Exception) {
            generateKey()
        }
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
        val hasStrongBox = context?.let { isStrongBoxSupported(it) } ?: false

        if (hasStrongBox) {
            try {
                val strongBoxSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setRandomizedEncryptionRequired(true)
                    .setIsStrongBoxBacked(true)
                    .build()

                keyGenerator.init(strongBoxSpec)
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                // Graceful fallback to standard TEE if StrongBox initialization fails
            }
        }

        // Standard TEE KeyStore spec
        val teeSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(teeSpec)
        return keyGenerator.generateKey()
    }

    fun initCipherForBiometric(mode: Int, iv: ByteArray? = null): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getSecretKey()
        if (mode == Cipher.DECRYPT_MODE && iv != null) {
            val spec = GCMParameterSpec(128, iv)
            cipher.init(mode, secretKey, spec)
        } else {
            cipher.init(mode, secretKey)
        }
        return cipher
    }

    fun encrypt(plainChars: CharArray): String {
        var plainBytes: ByteArray? = null
        var byteBuffer: ByteBuffer? = null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv

            val charBuffer = CharBuffer.wrap(plainChars)
            val encoder = StandardCharsets.UTF_8.newEncoder()
            byteBuffer = encoder.encode(charBuffer)
            plainBytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(plainBytes)

            val encryptedBytes = cipher.doFinal(plainBytes)
            val combined = iv + encryptedBytes
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to encrypt text safely", e)
        } finally {
            plainBytes?.let { MemoryUtils.wipe(it) }
            byteBuffer?.let {
                if (it.hasArray()) MemoryUtils.wipe(it.array())
            }
        }
    }

    fun encrypt(plainText: String): String {
        val chars = plainText.toCharArray()
        return try {
            encrypt(chars)
        } finally {
            MemoryUtils.wipe(chars)
        }
    }

    fun decryptToChars(encryptedText: String): CharArray {
        if (!encryptedText.startsWith(PREFIX)) {
            // Not encrypted, return as plaintext (for legacy database entries before migration)
            return encryptedText.toCharArray()
        }
        var decryptedBytes: ByteArray? = null
        var charBuffer: CharBuffer? = null
        return try {
            val actualEncrypted = encryptedText.removePrefix(PREFIX)
            val combined = Base64.decode(actualEncrypted, Base64.NO_WRAP)

            if (combined.size < 12) {
                throw IllegalArgumentException("Invalid encrypted payload size")
            }

            val iv = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)

            val cipher = initCipherForBiometric(Cipher.DECRYPT_MODE, iv)
            decryptedBytes = cipher.doFinal(encryptedBytes)

            val decoder = StandardCharsets.UTF_8.newDecoder()
            charBuffer = decoder.decode(ByteBuffer.wrap(decryptedBytes))
            val result = CharArray(charBuffer.remaining())
            charBuffer.get(result)
            result
        } catch (e: Exception) {
            throw IllegalStateException("Failed to decrypt text safely", e)
        } finally {
            decryptedBytes?.let { MemoryUtils.wipe(it) }
            charBuffer?.let {
                if (it.hasArray()) MemoryUtils.wipe(it.array())
            }
        }
    }

    fun decrypt(encryptedText: String): String {
        val chars = decryptToChars(encryptedText)
        val str = String(chars)
        MemoryUtils.wipe(chars)
        return str
    }
}
