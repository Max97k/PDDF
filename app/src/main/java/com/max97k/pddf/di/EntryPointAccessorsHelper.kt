package com.max97k.pddf.di

import com.max97k.pddf.data.SecurityPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun securityPreferences(): SecurityPreferences
}
