package com.max97k.pddf.di

import com.max97k.pddf.data.PasswordRepository
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.domain.usecase.AutoUnlockUseCase
import com.max97k.pddf.domain.usecase.BatchProcessUseCase
import com.max97k.pddf.domain.usecase.DecryptPdfUseCase
import com.max97k.pddf.domain.usecase.PasswordVaultUseCase
import com.max97k.pddf.domain.usecase.ReversePdfPagesUseCase
import com.max97k.pddf.domain.usecase.ExportPlainTextUseCase
import com.max97k.pddf.domain.usecase.ExportMetadataReportUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class UseCaseModule {

    @Provides
    @Singleton
    fun provideDecryptPdfUseCase(
        pdfRepository: PdfRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DecryptPdfUseCase {
        return DecryptPdfUseCase(pdfRepository, ioDispatcher)
    }

    @Provides
    @Singleton
    fun providePasswordVaultUseCase(
        repository: PasswordRepository
    ): PasswordVaultUseCase {
        return PasswordVaultUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideAutoUnlockUseCase(
        pdfRepository: PdfRepository,
        decryptPdfUseCase: DecryptPdfUseCase,
        passwordVaultUseCase: PasswordVaultUseCase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): AutoUnlockUseCase {
        return AutoUnlockUseCase(pdfRepository, decryptPdfUseCase, passwordVaultUseCase, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideBatchProcessUseCase(
        pdfRepository: PdfRepository,
        decryptPdfUseCase: DecryptPdfUseCase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): BatchProcessUseCase {
        return BatchProcessUseCase(pdfRepository, decryptPdfUseCase, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideReversePdfPagesUseCase(
        pdfRepository: PdfRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): ReversePdfPagesUseCase {
        return ReversePdfPagesUseCase(pdfRepository, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideStampPageNumbersUseCase(
        pdfRepository: PdfRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): com.max97k.pddf.domain.usecase.StampPageNumbersUseCase {
        return com.max97k.pddf.domain.usecase.StampPageNumbersUseCase(pdfRepository, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideExportPlainTextUseCase(
        pdfRepository: PdfRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): ExportPlainTextUseCase {
        return ExportPlainTextUseCase(pdfRepository, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideExportMetadataReportUseCase(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): ExportMetadataReportUseCase {
        return ExportMetadataReportUseCase(ioDispatcher)
    }
}
