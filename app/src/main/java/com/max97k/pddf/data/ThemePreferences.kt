package com.max97k.pddf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.max97k.pddf.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, AMOLED, OLED_BLACK
}

enum class DensityMode {
    COMPACT, STANDARD, COMFORTABLE
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

class ThemePreferences(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.dataStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val DENSITY_MODE_KEY = stringPreferencesKey("density_mode")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data
        .map { preferences ->
            val name = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(name)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        }
        .flowOn(ioDispatcher)

    val densityMode: Flow<DensityMode> = dataStore.data
        .map { preferences ->
            val name = preferences[DENSITY_MODE_KEY] ?: DensityMode.STANDARD.name
            try {
                DensityMode.valueOf(name)
            } catch (e: IllegalArgumentException) {
                DensityMode.STANDARD
            }
        }
        .flowOn(ioDispatcher)

    suspend fun saveThemeMode(mode: ThemeMode) = withContext(ioDispatcher) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun saveDensityMode(mode: DensityMode) = withContext(ioDispatcher) {
        dataStore.edit { preferences ->
            preferences[DENSITY_MODE_KEY] = mode.name
        }
    }
}
