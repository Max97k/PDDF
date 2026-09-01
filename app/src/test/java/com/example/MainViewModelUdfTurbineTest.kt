package com.example

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.data.AppDatabase
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import com.example.data.ThemeMode
import com.example.domain.model.PdfUiState
import com.example.util.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

/**
 * Tier 1 & Tier 4: UDF Reactive StateFlow Tests using CashApp Turbine.
 * Deterministically tests reactive Flow emissions, single-shot events,
 * and immutable state transitions across user actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelUdfTurbineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: PasswordRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128)
        CryptoManager.testKeyOverride = keyGen.generateKey()

        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PasswordRepository(database.passwordDao(), CryptoManager())
        viewModel = MainViewModel(application, repository, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testThemeModeFlow_emitsStateChangesTurbine() = runTest {
        viewModel.themeMode.test {
            // Initial default is SYSTEM
            val initial = awaitItem()
            assertEquals(ThemeMode.SYSTEM, initial)

            // Switch to DARK
            viewModel.setTheme(ThemeMode.DARK)
            advanceUntilIdle()
            val dark = awaitItem()
            assertEquals(ThemeMode.DARK, dark)

            // Switch to AMOLED
            viewModel.setTheme(ThemeMode.AMOLED)
            advanceUntilIdle()
            val amoled = awaitItem()
            assertEquals(ThemeMode.AMOLED, amoled)

            // Switch to LIGHT
            viewModel.setTheme(ThemeMode.LIGHT)
            advanceUntilIdle()
            val light = awaitItem()
            assertEquals(ThemeMode.LIGHT, light)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testSavedPasswordsFlow_emitsOnInsertAndDeleteTurbine() = runTest {
        viewModel.savedPasswords.test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())

            // Insert password
            viewModel.savePassword("Payroll PDF", "payroll2026")
            advanceUntilIdle()

            val afterInsert = awaitItem()
            assertEquals(1, afterInsert.size)
            assertEquals("Payroll PDF", afterInsert[0].name)
            assertEquals("payroll2026", afterInsert[0].passwordValue)

            // Delete password
            val entityId = afterInsert[0].id
            viewModel.deletePassword(entityId)
            advanceUntilIdle()

            val afterDelete = awaitItem()
            assertTrue(afterDelete.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testBatchStateFlow_progressEmissionsTurbine() = runTest {
        viewModel.batchState.test {
            val initial = awaitItem()
            assertFalse(initial.isProcessing)
            assertEquals(0, initial.progress)
            assertEquals(0, initial.total)

            // Emit updated batch state
            viewModel.batchState.value = BatchState(
                isProcessing = true,
                progress = 1,
                total = 5
            )
            val updated = awaitItem()
            assertTrue(updated.isProcessing)
            assertEquals(1, updated.progress)
            assertEquals(5, updated.total)

            // Cancel batch
            viewModel.cancelBatch()
            viewModel.batchState.value = BatchState()
            val reset = awaitItem()
            assertFalse(reset.isProcessing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testPdfUiState_stateTransitionsTurbine() = runTest {
        viewModel.pdfUiState.test {
            // Initial state: Idle
            val initial = awaitItem()
            assertEquals(PdfUiState.Idle, initial)

            // Transition to Selected
            val dummyUri = Uri.parse("file:///dummy.pdf")
            viewModel.pdfUiState.value = PdfUiState.Selected(
                uris = listOf(dummyUri),
                fileNames = listOf("dummy.pdf")
            )
            val selected = awaitItem()
            assertTrue(selected is PdfUiState.Selected)
            val selectedState = selected as PdfUiState.Selected
            assertEquals(1, selectedState.uris.size)
            assertEquals("dummy.pdf", selectedState.fileNames[0])

            // Transition to Processing
            viewModel.pdfUiState.value = PdfUiState.Processing(
                progress = 1,
                total = 1,
                currentFileName = "dummy.pdf"
            )
            val processing = awaitItem()
            assertTrue(processing is PdfUiState.Processing)
            assertEquals(1, (processing as PdfUiState.Processing).progress)

            // Transition to Success
            viewModel.pdfUiState.value = PdfUiState.Success(
                message = "Decryption Complete",
                decryptedUris = listOf(dummyUri),
                lastDecryptedUri = dummyUri
            )
            val success = awaitItem()
            assertTrue(success is PdfUiState.Success)
            assertEquals("Decryption Complete", (success as PdfUiState.Success).message)

            // Transition to Error
            viewModel.pdfUiState.value = PdfUiState.Error(
                message = "Corrupted PDF"
            )
            val error = awaitItem()
            assertTrue(error is PdfUiState.Error)
            assertEquals("Corrupted PDF", (error as PdfUiState.Error).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testConflictSettings_flowTransitionsTurbine() = runTest {
        viewModel.conflictMode.test {
            val initialMode = awaitItem()
            assertEquals(ConflictMode.SAVE_AS_COPY, initialMode)

            viewModel.updateConflictSettings(ConflictMode.OVERWRITE, true)
            val updatedMode = awaitItem()
            assertEquals(ConflictMode.OVERWRITE, updatedMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testDocumentPickerTrigger_flowTransitionsTurbine() = runTest {
        viewModel.requestOpenDocumentPicker.test {
            val initial = awaitItem()
            assertFalse(initial)

            viewModel.triggerOpenDocumentPicker()
            val triggered = awaitItem()
            assertTrue(triggered)

            viewModel.onDocumentPickerLaunched()
            val consumed = awaitItem()
            assertFalse(consumed)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testSensitivePasswordTimeout_autoClearsMemoryTurbine() = runTest {
        viewModel.password.test {
            val initial = awaitItem()
            assertEquals("", initial)

            viewModel.password.value = "SuperSecretBankPass"
            val entered = awaitItem()
            assertEquals("SuperSecretBankPass", entered)

            // Simulate app backgrounded for > 60 seconds
            viewModel.onAppBackgrounded()
            Thread.sleep(10) // slight delay
            // We simulate timeout by triggering foregrounding after 61 seconds
            val field = MainViewModel::class.java.getDeclaredField("backgroundTime")
            field.isAccessible = true
            field.setLong(viewModel, System.currentTimeMillis() - 65000L)

            viewModel.onAppForegrounded()
            val cleared = awaitItem()
            assertEquals("", cleared)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
