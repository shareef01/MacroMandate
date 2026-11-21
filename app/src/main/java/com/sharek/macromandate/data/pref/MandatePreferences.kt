package com.sharek.macromandate.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sharek.macromandate.network.ApiConfig
import com.sharek.macromandate.ui.theme.TerminalTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mandate_prefs")

class MandatePreferences(private val context: Context) {

    companion object {
        val DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        val ENFORCEMENT_ENABLED = booleanPreferencesKey("enforcement_enabled")
        val IS_PERMANENTLY_LOCKED = booleanPreferencesKey("is_permanently_locked")
        val LOCATION_TRACKING_ENABLED = booleanPreferencesKey("location_tracking_enabled")
        val SHARE_LOCATION_WITH_ANALYSIS = booleanPreferencesKey("share_location_with_analysis")
        val TERMINAL_THEME = stringPreferencesKey("terminal_theme")
        val CRT_EFFECTS_ENABLED = booleanPreferencesKey("crt_effects_enabled")
    }

    private val apiKey = stringPreferencesKey(ApiConfig.credentialPreferenceName)

    /**
     * The analysis credential, entered by the user in Settings.
     *
     * Stored encrypted at rest using Android KeyStore (AES-256 GCM)
     * to protect credentials against unauthorized offline extraction.
     */
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val raw = preferences[apiKey] ?: ""
        com.sharek.macromandate.util.KeyStoreCrypto.decrypt(raw)
            .takeIf { ApiConfig.acceptsKey(it) }.orEmpty()
    }.flowOn(Dispatchers.IO)

    val locationTrackingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCATION_TRACKING_ENABLED] ?: false
    }

    val shareLocationWithAnalysisFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCATION_TRACKING_ENABLED] == true && preferences[SHARE_LOCATION_WITH_ANALYSIS] == true
    }

    val calorieTargetFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DAILY_CALORIE_TARGET] ?: 2500
    }

    val enforcementEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENFORCEMENT_ENABLED] ?: false
    }

    val isPermanentlyLockedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_PERMANENTLY_LOCKED] ?: false
    }

    val terminalThemeFlow: Flow<TerminalTheme> = context.dataStore.data.map { preferences ->
        TerminalTheme.fromId(preferences[TERMINAL_THEME])
    }

    val crtEffectsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CRT_EFFECTS_ENABLED] ?: true
    }

    suspend fun updateCalorieTarget(target: Int) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_CALORIE_TARGET] = target
        }
    }

    suspend fun updateTerminalTheme(theme: TerminalTheme) {
        context.dataStore.edit { preferences ->
            preferences[TERMINAL_THEME] = theme.id
        }
    }

    suspend fun updateCrtEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CRT_EFFECTS_ENABLED] = enabled
        }
    }

    suspend fun updateEnforcementEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENFORCEMENT_ENABLED] = enabled
        }
    }

    suspend fun updateLocationTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOCATION_TRACKING_ENABLED] = enabled
            if (!enabled) preferences.remove(SHARE_LOCATION_WITH_ANALYSIS)
        }
    }

    suspend fun updateShareLocationWithAnalysis(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHARE_LOCATION_WITH_ANALYSIS] = enabled && preferences[LOCATION_TRACKING_ENABLED] == true
        }
    }

    suspend fun updateApiKey(key: String) {
        val trimmed = key.trim()
        require(ApiConfig.acceptsKey(trimmed)) { "Use an API key for the configured provider." }
        val encrypted = if (trimmed.isBlank()) "" else com.sharek.macromandate.util.KeyStoreCrypto.encrypt(trimmed)
        context.dataStore.edit { preferences ->
            preferences[apiKey] = encrypted
        }
    }

    suspend fun setPermanentLockdown(locked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_PERMANENTLY_LOCKED] = locked
        }
    }
}
