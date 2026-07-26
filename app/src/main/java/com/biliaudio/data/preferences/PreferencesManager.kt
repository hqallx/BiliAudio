package com.biliaudio.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bili_audio_prefs")

class PreferencesManager(private val context: Context) {

    private val cookiesKey = stringPreferencesKey("cookies")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userNameKey = stringPreferencesKey("user_name")
    private val userAvatarKey = stringPreferencesKey("user_avatar")

    val cookies: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[cookiesKey] ?: ""
    }

    val userId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[userIdKey] ?: ""
    }

    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[userNameKey] ?: ""
    }

    val userAvatar: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[userAvatarKey] ?: ""
    }

    suspend fun saveCookies(cookies: String) {
        context.dataStore.edit { preferences ->
            preferences[cookiesKey] = cookies
        }
    }

    suspend fun saveUserInfo(id: String, name: String, avatar: String) {
        context.dataStore.edit { preferences ->
            preferences[userIdKey] = id
            preferences[userNameKey] = name
            preferences[userAvatarKey] = avatar
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
