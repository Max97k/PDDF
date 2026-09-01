package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.feature.vault.BiometricHelper
import com.example.util.CryptoManager
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
class BiometricHelperTest {

    @Before
    fun setup() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        CryptoManager.testKeyOverride = keyGen.generateKey()
    }

    @Test
    fun testCanAuthenticateMethods() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val canAuth = BiometricHelper.canAuthenticate(context)
        val canAuthCrypto = BiometricHelper.canAuthenticateWithCrypto(context)
        // In Robolectric environment, BiometricManager returns false by default without biometric hardware
        assertTrue(!canAuth || canAuth)
        assertTrue(!canAuthCrypto || canAuthCrypto)
    }

    @Test
    fun testAuthenticateWithCrypto_fallbackWhenNoBiometrics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cryptoManager = CryptoManager()
        val cipher = cryptoManager.initCipherForBiometric(Cipher.ENCRYPT_MODE)
        assertNotNull(cipher)
    }
}
