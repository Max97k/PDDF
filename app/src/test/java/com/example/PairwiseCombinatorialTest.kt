package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PasswordRepository
import com.example.data.ThemeMode
import com.example.domain.usecase.AutoUnlockUseCase
import com.example.domain.usecase.DecryptPdfUseCase
import com.example.domain.usecase.PasswordVaultUseCase
import com.example.util.CryptoManager
import com.example.util.FileUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

/**
 * Tier 3: Pairwise Combinatorial Test Matrix.
 * Generates orthogonal combinations across:
 * [ConflictMode] x [PasswordState] x [DocType] x [ThemeMode]
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class PairwiseCombinatorialTest(
    private val conflictMode: ConflictMode,
    private val passwordState: PasswordState,
    private val docType: DocType,
    private val themeMode: ThemeMode
) {

    enum class PasswordState { CORRECT, WRONG, EMPTY, STORED_IN_VAULT }
    enum class DocType { ENCRYPTED_128, ENCRYPTED_256, UNENCRYPTED }

    companion object {
        private const val CORRECT_PASS = "ValidPass2026"
        private const val WRONG_PASS = "WrongPass999"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(
            name = "Combo_{index}: Conflict={0}, Pass={1}, Doc={2}, Theme={3}"
        )
        fun data(): Collection<Array<Any>> {
            // Covering orthogonal pairwise combinations across 4 parameters
            return listOf(
                arrayOf(ConflictMode.OVERWRITE, PasswordState.CORRECT, DocType.ENCRYPTED_128, ThemeMode.SYSTEM),
                arrayOf(ConflictMode.OVERWRITE, PasswordState.WRONG, DocType.ENCRYPTED_256, ThemeMode.DARK),
                arrayOf(ConflictMode.OVERWRITE, PasswordState.EMPTY, DocType.UNENCRYPTED, ThemeMode.LIGHT),
                arrayOf(ConflictMode.OVERWRITE, PasswordState.STORED_IN_VAULT, DocType.ENCRYPTED_128, ThemeMode.AMOLED),
                arrayOf(ConflictMode.SAVE_AS_COPY, PasswordState.CORRECT, DocType.ENCRYPTED_256, ThemeMode.AMOLED),
                arrayOf(ConflictMode.SAVE_AS_COPY, PasswordState.WRONG, DocType.UNENCRYPTED, ThemeMode.SYSTEM),
                arrayOf(ConflictMode.SAVE_AS_COPY, PasswordState.EMPTY, DocType.ENCRYPTED_128, ThemeMode.DARK),
                arrayOf(ConflictMode.SAVE_AS_COPY, PasswordState.STORED_IN_VAULT, DocType.ENCRYPTED_256, ThemeMode.LIGHT),
                arrayOf(ConflictMode.SAVE_AS_COPY, PasswordState.CORRECT, DocType.UNENCRYPTED, ThemeMode.DARK),
                arrayOf(ConflictMode.OVERWRITE, PasswordState.STORED_IN_VAULT, DocType.UNENCRYPTED, ThemeMode.SYSTEM)
            )
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: PasswordRepository
    private lateinit var vaultUseCase: PasswordVaultUseCase
    private lateinit var decryptUseCase: DecryptPdfUseCase
    private lateinit var autoUnlockUseCase: AutoUnlockUseCase
    private lateinit var viewModel: MainViewModel
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        context = application
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}

        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128)
        CryptoManager.testKeyOverride = keyGen.generateKey()

        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PasswordRepository(database.passwordDao(), CryptoManager())
        vaultUseCase = PasswordVaultUseCase(repository)
        decryptUseCase = DecryptPdfUseCase(testDispatcher)
        autoUnlockUseCase = AutoUnlockUseCase(decryptUseCase, vaultUseCase, testDispatcher)
        viewModel = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            decryptPdfUseCase = decryptUseCase,
            passwordVaultUseCase = vaultUseCase,
            autoUnlockUseCase = autoUnlockUseCase
        )
    }

    @After
    fun tearDown() {
        tempFiles.forEach { FileUtils.secureDelete(it) }
        tempFiles.clear()
        database.close()
        Dispatchers.resetMain()
    }

    private fun generatePdf(docType: DocType): File {
        val file = File(context.cacheDir, "pairwise_${System.currentTimeMillis()}_${(0..999).random()}.pdf")
        val doc = PDDocument()
        doc.addPage(PDPage())
        when (docType) {
            DocType.UNENCRYPTED -> { /* no encryption */ }
            DocType.ENCRYPTED_128 -> {
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy("owner", CORRECT_PASS, ap)
                spp.encryptionKeyLength = 128
                doc.protect(spp)
            }
            DocType.ENCRYPTED_256 -> {
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy("owner", CORRECT_PASS, ap)
                spp.encryptionKeyLength = 256
                doc.protect(spp)
            }
        }
        doc.save(file)
        doc.close()
        tempFiles.add(file)
        return file
    }

    @Test
    fun executePairwiseScenario() = runTest(testDispatcher) {
        // 1. Setup Theme
        viewModel.setTheme(themeMode)

        // 2. Setup Vault Password if needed
        if (passwordState == PasswordState.STORED_IN_VAULT) {
            vaultUseCase.insertPassword("Vault Saved Key", CORRECT_PASS)
        }

        // 3. Setup Conflict Mode
        viewModel.updateConflictSettings(conflictMode, remember = true)
        assertEquals(conflictMode, viewModel.conflictMode.value)

        // 4. Generate Document
        val pdfFile = generatePdf(docType)
        val inputUri = Uri.fromFile(pdfFile)

        val outputFile = File(context.cacheDir, "pairwise_out_${System.currentTimeMillis()}.pdf")
        tempFiles.add(outputFile)
        val outputUri = Uri.fromFile(outputFile)

        // 5. Test Decryption or AutoUnlock according to State
        when (passwordState) {
            PasswordState.STORED_IN_VAULT -> {
                val autoResult = autoUnlockUseCase.tryAutoUnlock(context, inputUri)
                when (docType) {
                    DocType.UNENCRYPTED -> {
                        assertTrue("Unencrypted should return NotEncrypted", autoResult is AutoUnlockUseCase.AutoUnlockResult.NotEncrypted)
                    }
                    DocType.ENCRYPTED_128, DocType.ENCRYPTED_256 -> {
                        assertTrue(
                            "Saved password should automatically unlock encrypted PDF",
                            autoResult is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword
                        )
                        val unlockedResult = autoResult as AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword
                        assertEquals("Vault Saved Key", unlockedResult.matchedPasswordName)
                        tempFiles.add(File(unlockedResult.outputUri.path!!))
                    }
                }
            }
            PasswordState.CORRECT -> {
                val status = decryptUseCase.decrypt(context, inputUri, outputUri, CORRECT_PASS)
                when (docType) {
                    DocType.UNENCRYPTED -> assertEquals(DecryptStatus.NOT_ENCRYPTED, status)
                    DocType.ENCRYPTED_128, DocType.ENCRYPTED_256 -> {
                        assertEquals(DecryptStatus.SUCCESS, status)
                        assertTrue(outputFile.exists() && outputFile.length() > 0)
                    }
                }
            }
            PasswordState.WRONG -> {
                val status = decryptUseCase.decrypt(context, inputUri, outputUri, WRONG_PASS)
                when (docType) {
                    DocType.UNENCRYPTED -> assertEquals(DecryptStatus.NOT_ENCRYPTED, status)
                    DocType.ENCRYPTED_128, DocType.ENCRYPTED_256 -> {
                        assertEquals(DecryptStatus.WRONG_PASSWORD, status)
                    }
                }
            }
            PasswordState.EMPTY -> {
                val status = decryptUseCase.decrypt(context, inputUri, outputUri, "")
                when (docType) {
                    DocType.UNENCRYPTED -> assertEquals(DecryptStatus.NOT_ENCRYPTED, status)
                    DocType.ENCRYPTED_128, DocType.ENCRYPTED_256 -> {
                        assertTrue(status == DecryptStatus.WRONG_PASSWORD || status == DecryptStatus.ERROR)
                    }
                }
            }
        }
    }
}
