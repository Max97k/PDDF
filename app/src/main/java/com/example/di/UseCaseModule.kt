package com.example.di

import com.example.data.PasswordRepository
import com.example.domain.usecase.AutoUnlockUseCase
import com.example.domain.usecase.BatchProcessUseCase
import com.example.domain.usecase.DecryptPdfUseCase
import com.example.domain.usecase.PasswordVaultUseCase
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
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DecryptPdfUseCase {
        return DecryptPdfUseCase(ioDispatcher)
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
        decryptPdfUseCase: DecryptPdfUseCase,
        passwordVaultUseCase: PasswordVaultUseCase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): AutoUnlockUseCase {
        return AutoUnlockUseCase(decryptPdfUseCase, passwordVaultUseCase, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideBatchProcessUseCase(
        decryptPdfUseCase: DecryptPdfUseCase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): BatchProcessUseCase {
        return BatchProcessUseCase(decryptPdfUseCase, ioDispatcher)
    }
}
