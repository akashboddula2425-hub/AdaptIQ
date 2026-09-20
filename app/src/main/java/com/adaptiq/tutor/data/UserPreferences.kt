package com.adaptiq.tutor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "adaptiq_prefs")

/**
 * Jetpack DataStore wrapper for user preferences and persisted learner profile.
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_LEARNER_PROFILE = stringPreferencesKey("learner_profile_json")
        private val KEY_MODEL_PATH = stringPreferencesKey("model_config_path")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        private val KEY_VOICE_INPUT_ENABLED = booleanPreferencesKey("voice_input_enabled")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val learnerProfile: Flow<LearnerProfile?> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            prefs[KEY_LEARNER_PROFILE]?.let {
                json.decodeFromString<LearnerProfile>(it)
            }
        }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETE] ?: false }

    val modelPath: Flow<String?> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs[KEY_MODEL_PATH] }

    val isTtsEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_TTS_ENABLED] ?: true }

    val isVoiceInputEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_VOICE_INPUT_ENABLED] ?: true }

    suspend fun saveLearnerProfile(profile: LearnerProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LEARNER_PROFILE] = json.encodeToString(profile)
        }
    }

    suspend fun setModelPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MODEL_PATH] = path
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TTS_ENABLED] = enabled
        }
    }

    suspend fun setVoiceInputEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOICE_INPUT_ENABLED] = enabled
        }
    }
}
