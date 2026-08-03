package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasswordRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PasswordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PasswordRepository(database.passwordDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetAllPasswords() = runBlocking {
        val initialPasswords = repository.allPasswords.first()
        assertTrue(initialPasswords.isEmpty())

        val pass1 = PasswordEntity(id = 1, name = "Bank", passwordValue = "123456", timestamp = 1000L)
        val pass2 = PasswordEntity(id = 2, name = "PDF Pass", passwordValue = "secret", timestamp = 2000L)

        repository.insert(pass1)
        repository.insert(pass2)

        val updatedPasswords = repository.allPasswords.first()
        assertEquals(2, updatedPasswords.size)
        // Ordered by timestamp desc
        assertEquals("PDF Pass", updatedPasswords[0].name)
        assertEquals("Bank", updatedPasswords[1].name)
    }

    @Test
    fun deletePasswordById() = runBlocking {
        val pass = PasswordEntity(id = 10, name = "Work", passwordValue = "work123")
        repository.insert(pass)

        var list = repository.allPasswords.first()
        assertEquals(1, list.size)

        repository.deleteById(10)

        list = repository.allPasswords.first()
        assertTrue(list.isEmpty())
    }
}
