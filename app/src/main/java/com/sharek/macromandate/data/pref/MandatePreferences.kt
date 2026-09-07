package com.sharek.macromandate.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sharek.macromandate.ui.theme.TerminalTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mandate_prefs")

class MandatePreferences(private val context: Context) {

    companion object {
        val DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        val ENFORCEMENT_ENABLED = booleanPreferencesKey("enforcement_enabled")
        val LOCATION_TRACKING_ENABLED = booleanPreferencesKey("location_tracking_enabled")
        val INCLUDE_LOCATION_IN_AI = booleanPreferencesKey("include_location_in_ai")
        val LOCATION_DISCLOSURE_ACKNOWLEDGED = booleanPreferencesKey("location_disclosure_acknowledged")
        val API_KEY = stringPreferencesKey("api_key")
        val TERMINAL_THEME = stringPreferencesKey("terminal_theme")
        val REDUCE_VISUAL_EFFECTS = booleanPreferencesKey("reduce_visual_effects")
    }

    /**
     * Catches DataStore IOExceptions on disk read and falls back to emptyPreferences()
     * so that transient file issues do not crash the UI StateFlow collection.
     */
    private val safePreferencesFlow: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    /**
     * The analysis credential, entered by the user in Settings.
     *
     * Stored in app-private DataStore. This is **not encryption**: the file is
     * plaintext on disk and the protection is the app sandbox, which holds on a
     * non-rooted device and does not hold against physical access with an
     * unlocked bootloader or a rooted OS. `android:allowBackup="false"` keeps it
     * out of cloud backups, and it is excluded from every export.
     */
    val apiKeyFlow: Flow<String> = safePreferencesFlow.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    /**
     * Opt-in, and deliberately defaulted to false.
     *
     * Enables saving local GPS coordinates to meal records.
     */
    val locationTrackingEnabledFlow: Flow<Boolean> = safePreferencesFlow.map { preferences ->
        preferences[LOCATION_TRACKING_ENABLED] ?: false
    }

    /**
     * Separate explicit opt-in: include location coordinates in the image sent to
     * the AI provider. Defaults to false.
     */
    val includeLocationInAiFlow: Flow<Boolean> = safePreferencesFlow.map { preferences ->
        preferences[INCLUDE_LOCATION_IN_AI] ?: false
    }

    val locationDisclosureAcknowledgedFlow: Flow<Boolean> = safePreferencesFlow.map { preferences ->
        preferences[LOCATION_DISCLOSURE_ACKNOWLEDGED] ?: false
    }

    val calorieTargetFlow: Flow<Int> = safePreferencesFlow.map { preferences ->
        preferences[DAILY_CALORIE_TARGET] ?: 2500
    }

    /**
     * Reminders are genuinely optional and default to false on new installs.
     */
    val enforcementEnabledFlow: Flow<Boolean> = safePreferencesFlow.map { preferences ->
        preferences[ENFORCEMENT_ENABLED] ?: false
    }

    val terminalThemeFlow: Flow<TerminalTheme> = safePreferencesFlow.map { preferences ->
        TerminalTheme.fromId(preferences[TERMINAL_THEME])
    }

    /**
     * Off by default: provides a toggle for reduced animation and visual overlays.
     */
    val reduceVisualEffectsFlow: Flow<Boolean> = safePreferencesFlow.map { preferences ->
        preferences[REDUCE_VISUAL_EFFECTS] ?: false
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

    suspend fun updateIncludeLocationInAi(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INCLUDE_LOCATION_IN_AI] = enabled
        }
    }

    suspend fun updateLocationDisclosureAcknowledged(acknowledged: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOCATION_DISCLOSURE_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun updateApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key.trim()
        }
    }

    suspend fun updateReduceVisualEffects(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REDUCE_VISUAL_EFFECTS] = enabled
        }
    }
}
