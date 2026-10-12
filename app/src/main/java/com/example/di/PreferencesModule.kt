package com.example.di

import android.content.Context
import com.example.data.SecurityPreferences
import com.example.data.ThemePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class PreferencesModule {

    @Provides
    @Singleton
    fun provideThemePreferences(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): ThemePreferences {
        return ThemePreferences(context = context, ioDispatcher = ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideSecurityPreferences(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): SecurityPreferences {
        return SecurityPreferences(context = context, ioDispatcher = ioDispatcher)
    }
}
