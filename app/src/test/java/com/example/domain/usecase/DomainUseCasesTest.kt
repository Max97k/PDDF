package com.example.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.DecryptStatus
import com.example.data.PasswordDao
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DomainUseCasesTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    class FakePasswordDao : PasswordDao {
        private val list = mutableListOf<PasswordEntity>()

        override fun getAllPasswords(): Flow<List<PasswordEntity>> = flowOf(list.toList())

        override suspend fun getAllPasswordsList(): List<PasswordEntity> = list.toList()

        override suspend fun insertPassword(password: PasswordEntity) {
            list.removeAll { it.id == password.id && password.id != 0 }
            list.add(password)
        }

        override suspend fun deletePasswordById(id: Int) {
            list.removeAll { it.id == id }
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPasswordVaultUseCase_insertAndDelete() = runTest(testDispatcher) {
        val fakeDao = FakePasswordDao()
        val repository = PasswordRepository(fakeDao)
        val vaultUseCase = PasswordVaultUseCase(repository)

        vaultUseCase.insertPassword("Test Bank", "secret123")
        val saved = vaultUseCase.getAllDecryptedPasswords()
        assertEquals(1, saved.size)
        assertEquals("Test Bank", saved[0].name)
        assertEquals("secret123", saved[0].passwordValue)
    }

    @Test
    fun testDecryptPdfUseCase_nonExistentFile_returnsError() = runTest(testDispatcher) {
        val decryptPdfUseCase = DecryptPdfUseCase(testDispatcher)
        val dummyInput = Uri.fromFile(File(context.cacheDir, "non_existent_input.pdf"))
        val dummyOutput = Uri.fromFile(File(context.cacheDir, "non_existent_output.pdf"))

        val status = decryptPdfUseCase.decrypt(context, dummyInput, dummyOutput, "somepass")
        assertEquals(DecryptStatus.ERROR, status)
    }
}
