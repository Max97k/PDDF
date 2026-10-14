package com.max97k.pddf.di

import android.content.Context
import androidx.room.Room
import com.max97k.pddf.data.AppDatabase
import com.max97k.pddf.data.PasswordDao
import com.max97k.pddf.data.PasswordRepository
import com.max97k.pddf.util.CryptoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pdf-decryptor-db"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .build()
    }

    @Provides
    @Singleton
    fun providePasswordDao(database: AppDatabase): PasswordDao {
        return database.passwordDao()
    }

    @Provides
    @Singleton
    fun provideCryptoManager(
        @ApplicationContext context: Context
    ): CryptoManager {
        return CryptoManager(context)
    }

    @Provides
    @Singleton
    fun providePasswordRepository(
        passwordDao: PasswordDao,
        cryptoManager: CryptoManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): PasswordRepository {
        return PasswordRepository(passwordDao, cryptoManager, ioDispatcher)
    }
}
