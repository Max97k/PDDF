package com.max97k.pddf.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.max97k.pddf.data.repository.AppUpdateRepositoryImpl
import com.max97k.pddf.domain.repository.AppUpdateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateProviderModule {

    @Provides
    @Singleton
    fun provideAppUpdateManager(
        @ApplicationContext context: Context
    ): AppUpdateManager {
        return AppUpdateManagerFactory.create(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateBindingModule {

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        impl: AppUpdateRepositoryImpl
    ): AppUpdateRepository
}
