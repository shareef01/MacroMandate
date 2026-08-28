package com.sharek.macromandate.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sharek.macromandate.ui.theme.TerminalTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mandate_prefs")

class MandatePreferences(private val context: Context) {

    companion object {
        val DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        val ENFORCEMENT_ENABLED = booleanPreferencesKey("enforcement_enabled")
        val LOCATION_TRACKING_ENABLED = booleanPreferencesKey("location_tracking_enabled")
        val API_KEY = stringPreferencesKey("api_key")
        val TERMINAL_THEME = stringPreferencesKey("terminal_theme")
    }

    /**
     * The analysis credential, entered by the user in Settings.
     *
     * Stored in app-private DataStore. This is **not encryption**: the file is
     * plaintext on disk and the protection is the app sandbox, which holds on a
     * non-rooted device and does not hold against physical access with an
     * unlocked bootloader or a rooted OS. `android:allowBackup="false"` keeps it
     * out of cloud backups, and it is excluded from every export.
     *
     * Documenting this accurately matters more than hardening it further: a
     * Keystore-wrapped value would still be readable by the same process, so it
     * would raise the effort for an attacker only marginally while inviting the
     * claim that the token is "encrypted".
     */
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    /**
     * Opt-in, and deliberately defaulted to false.
     *
     * Enabling this sends precise coordinates off-device: they are stored on the
     * meal record, burned into the evidence image as a visible watermark, and that
     * watermarked image is then uploaded for analysis. Nobody gets that by default.
     */
    val locationTrackingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCATION_TRACKING_ENABLED] ?: false
    }

    val calorieTargetFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DAILY_CALORIE_TARGET] ?: 2500
    }

    val enforcementEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENFORCEMENT_ENABLED] ?: true
    }

    val terminalThemeFlow: Flow<TerminalTheme> = context.dataStore.data.map { preferences ->
        TerminalTheme.fromId(preferences[TERMINAL_THEME])
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

    suspend fun updateEnforcementEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENFORCEMENT_ENABLED] = enabled
        }
    }

    suspend fun updateLocationTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOCATION_TRACKING_ENABLED] = enabled
        }
    }

    suspend fun updateApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key.trim()
        }
    }
}
