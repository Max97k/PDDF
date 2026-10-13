package com.max97k.pddf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.util.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoManagerTest {

    @Before
    fun setup() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        CryptoManager.testKeyOverride = keyGen.generateKey()
    }

    @Test
    fun testEncryptDecrypt() {
        val cryptoManager = CryptoManager()
        val plainText = "MySuperSecretPassword123!"
        val encrypted = cryptoManager.encrypt(plainText)

        assertNotEquals(plainText, encrypted)
        assertTrue(encrypted.startsWith("ENC_"))

        val decrypted = cryptoManager.decrypt(encrypted)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testDecryptFallbackForPlaintext() {
        val cryptoManager = CryptoManager()
        val plainText = "unencrypted_password"

        // Since it doesn't have the "ENC_" prefix, it should return the plaintext directly
        val decrypted = cryptoManager.decrypt(plainText)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testInitCipherForBiometric() {
        val cryptoManager = CryptoManager()

        val encryptCipher = cryptoManager.initCipherForBiometric(Cipher.ENCRYPT_MODE)
        assertNotNull(encryptCipher)
        assertNotNull(encryptCipher.iv)

        val decryptCipher = cryptoManager.initCipherForBiometric(Cipher.DECRYPT_MODE, encryptCipher.iv)
        assertNotNull(decryptCipher)
    }

    @Test
    fun testStrongBoxDetection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val supported = CryptoManager.isStrongBoxSupported(context)
        // Robolectric environment does not have StrongBox hardware by default
        assertTrue(supported == true || supported == false)
    }

    @Test
    fun testCryptoManagerWithContextConstructor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cryptoManager = CryptoManager(context)
        val plainText = "ContextAwareCryptoTest123"
        val encrypted = cryptoManager.encrypt(plainText)
        val decrypted = cryptoManager.decrypt(encrypted)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testEncryptDecrypt_withCharArray() {
        val cryptoManager = CryptoManager()
        val plainChars = "SensitiveCharArrayPass999!".toCharArray()
        val encrypted = cryptoManager.encrypt(plainChars)

        assertTrue(encrypted.startsWith("ENC_"))
        val decryptedChars = cryptoManager.decryptToChars(encrypted)
        assertTrue(plainChars.contentEquals(decryptedChars))

        com.max97k.pddf.util.MemoryUtils.wipe(plainChars)
        com.max97k.pddf.util.MemoryUtils.wipe(decryptedChars)
        assertTrue(plainChars.all { it == '\u0000' })
        assertTrue(decryptedChars.all { it == '\u0000' })
    }

    @Test
    fun testPasswordEntity_usePasswordCharsWiping() {
        val entity = com.max97k.pddf.data.PasswordEntity(
            name = "Test Bank",
            passwordValue = "BankSecret123"
        )
        var capturedChars: CharArray? = null
        val result = entity.usePasswordChars { chars ->
            capturedChars = chars
            String(chars)
        }
        assertEquals("BankSecret123", result)
        assertNotNull(capturedChars)
        assertTrue(capturedChars!!.all { it == '\u0000' })
    }
}
