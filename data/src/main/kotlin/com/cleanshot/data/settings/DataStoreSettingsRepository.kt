package com.cleanshot.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.repository.ThemeMode
import com.cleanshot.domain.repository.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) : SettingsRepository {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val targets = stringSetPreferencesKey("targets")
        val suffix = stringPreferencesKey("suffix")
        val keepHistory = booleanPreferencesKey("keep_history")
    }

    override val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            defaultOptions = p[Keys.targets]?.let { set -> CleaningOptions(set.mapNotNull { runCatching { CleaningTarget.valueOf(it) }.getOrNull() }.toSet()) }
                ?: CleaningOptions.DEFAULT,
            fileNameSuffix = p[Keys.suffix] ?: "_clean",
            keepHistory = p[Keys.keepHistory] ?: true,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) { context.dataStore.edit { it[Keys.theme] = mode.name } }
    override suspend fun setDefaultOptions(options: CleaningOptions) { context.dataStore.edit { it[Keys.targets] = options.targets.map { t -> t.name }.toSet() } }
    override suspend fun setFileNameSuffix(suffix: String) { context.dataStore.edit { it[Keys.suffix] = suffix.take(32) } }
    override suspend fun setKeepHistory(keep: Boolean) { context.dataStore.edit { it[Keys.keepHistory] = keep } }
}
