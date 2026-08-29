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
    }

    val calorieTargetFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DAILY_CALORIE_TARGET] ?: 2500
    }

    val enforcementEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENFORCEMENT_ENABLED] ?: true
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
}
