package com.example

import com.example.util.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CryptoManagerTest {

    @Before
    fun setup() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128)
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
}
