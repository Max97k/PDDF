package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            } else {
                false
            }
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

        if (hasStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedBytes
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(encryptedText: String): String {
        if (!encryptedText.startsWith(PREFIX)) {
            // Not encrypted, return as plaintext (for legacy database entries before migration)
            return encryptedText
        }
        return try {
            val actualEncrypted = encryptedText.removePrefix(PREFIX)
            val combined = Base64.decode(actualEncrypted, Base64.NO_WRAP)

            if (combined.size < 12) {
                return encryptedText
            }

            val iv = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)

            val cipher = initCipherForBiometric(Cipher.DECRYPT_MODE, iv)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            encryptedText
        }
    }
}
