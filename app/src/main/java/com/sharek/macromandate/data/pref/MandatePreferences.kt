package com.sharek.macromandate.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mandate_prefs")

class MandatePreferences(private val context: Context) {

    companion object {
        val DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        val ENFORCEMENT_ENABLED = booleanPreferencesKey("enforcement_enabled")
        val IS_PERMANENTLY_LOCKED = booleanPreferencesKey("is_permanently_locked")
        val LOCATION_TRACKING_ENABLED = booleanPreferencesKey("location_tracking_enabled")
        val API_KEY = stringPreferencesKey("api_key")
    }

    /**
     * The analysis credential, entered by the user in Settings.
     *
     * This lives in app-private storage rather than being compiled into the APK,
     * so it belongs to the person who typed it and cannot be lifted out of a
     * distributed build. It is not encrypted at rest: on a non-rooted device the
     * app sandbox is the protection.
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

    val isPermanentlyLockedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_PERMANENTLY_LOCKED] ?: false
    }

    suspend fun updateCalorieTarget(target: Int) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_CALORIE_TARGET] = target
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

    suspend fun setPermanentLockdown(locked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_PERMANENTLY_LOCKED] = locked
        }
    }
}
