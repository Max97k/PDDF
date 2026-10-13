package com.max97k.pddf

import android.app.Application
import androidx.room.Room
import com.max97k.pddf.data.AppDatabase
import com.max97k.pddf.data.PasswordRepository
import com.max97k.pddf.data.SecurityPreferences
import com.max97k.pddf.data.ThemePreferences
import com.max97k.pddf.data.repository.PdfRepositoryImpl
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.domain.usecase.AutoUnlockUseCase
import com.max97k.pddf.domain.usecase.BatchProcessUseCase
import com.max97k.pddf.domain.usecase.DecryptPdfUseCase
import com.max97k.pddf.domain.usecase.PasswordVaultUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

fun createTestMainViewModel(
    application: Application,
    repository: PasswordRepository = PasswordRepository(
        Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .passwordDao()
    ),
    themePreferences: ThemePreferences = ThemePreferences(application),
    securityPreferences: SecurityPreferences = SecurityPreferences(application),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    pdfRepository: PdfRepository = PdfRepositoryImpl(application, ioDispatcher),
    decryptPdfUseCase: DecryptPdfUseCase = DecryptPdfUseCase(pdfRepository, ioDispatcher),
    passwordVaultUseCase: PasswordVaultUseCase = PasswordVaultUseCase(repository),
    autoUnlockUseCase: AutoUnlockUseCase = AutoUnlockUseCase(pdfRepository, decryptPdfUseCase, passwordVaultUseCase, ioDispatcher),
    batchProcessUseCase: BatchProcessUseCase = BatchProcessUseCase(pdfRepository, decryptPdfUseCase, ioDispatcher)
): MainViewModel {
    return MainViewModel(
        application = application,
        repository = repository,
        themePreferences = themePreferences,
        securityPreferences = securityPreferences,
        ioDispatcher = ioDispatcher,
        decryptPdfUseCase = decryptPdfUseCase,
        passwordVaultUseCase = passwordVaultUseCase,
        autoUnlockUseCase = autoUnlockUseCase,
        batchProcessUseCase = batchProcessUseCase
    )
}
