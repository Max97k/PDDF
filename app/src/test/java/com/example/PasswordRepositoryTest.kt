package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PasswordRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PasswordRepository

    @Before
    fun setUp() {
        val keyGen = javax.crypto.KeyGenerator.getInstance("AES")
        keyGen.init(128)
        com.example.util.CryptoManager.testKeyOverride = keyGen.generateKey()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PasswordRepository(database.passwordDao(), com.example.util.CryptoManager())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetAllPasswords() = runTest {
        val initialResult = repository.allPasswords.first()
        assertTrue(initialResult is com.example.util.Result.Success && initialResult.data.isEmpty())

        val pass1 = PasswordEntity(id = 1, name = "Bank", passwordValue = "123456", timestamp = 1000L)
        val pass2 = PasswordEntity(id = 2, name = "PDF Pass", passwordValue = "secret", timestamp = 2000L)

        repository.insert(pass1)
        repository.insert(pass2)

        val updatedResult = repository.allPasswords.first()
        assertTrue(updatedResult is com.example.util.Result.Success)
        val updatedPasswords = (updatedResult as com.example.util.Result.Success).data
        assertEquals(2, updatedPasswords.size)
        // Ordered by timestamp desc
        assertEquals("PDF Pass", updatedPasswords[0].name)
        assertEquals("Bank", updatedPasswords[1].name)
    }

    @Test
    fun deletePasswordById() = runTest {
        val pass = PasswordEntity(id = 10, name = "Work", passwordValue = "work123")
        repository.insert(pass)

        var result = repository.allPasswords.first()
        assertTrue(result is com.example.util.Result.Success && (result as com.example.util.Result.Success).data.size == 1)

        repository.deleteById(10)

        result = repository.allPasswords.first()
        assertTrue(result is com.example.util.Result.Success && (result as com.example.util.Result.Success).data.isEmpty())
    }
}
