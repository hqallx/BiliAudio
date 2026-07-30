package com.biliaudio.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import com.biliaudio.data.model.Track

private val Context.dataStore by preferencesDataStore(name = "bili_audio_prefs")

class PreferencesManager(private val context: Context) {

    private val cookiesKey = stringPreferencesKey("cookies")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userNameKey = stringPreferencesKey("user_name")
    private val userAvatarKey = stringPreferencesKey("user_avatar")
    private val playlistKey = stringPreferencesKey("playlist")
    private val currentIndexKey = intPreferencesKey("current_index")
    private val positionKey = longPreferencesKey("position")
    // 音质偏好：对应 BiliConstants.AudioQuality 的 id（30280/30232/30216）
    private val audioQualityKey = intPreferencesKey("audio_quality")

    private val json = Json { ignoreUnknownKeys = true }

    val cookies: Flow<String> = context.dataStore.data.map { it[cookiesKey] ?: "" }

    val userId: Flow<String> = context.dataStore.data.map { it[userIdKey] ?: "" }

    val userName: Flow<String> = context.dataStore.data.map { it[userNameKey] ?: "" }

    val userAvatar: Flow<String> = context.dataStore.data.map { it[userAvatarKey] ?: "" }

    /** 用户偏好的音频质量 id，0 表示使用默认（192K AAC）。 */
    val audioQuality: Flow<Int> = context.dataStore.data.map { it[audioQualityKey] ?: 0 }

    val savedPlaylist: Flow<List<Track>> = context.dataStore.data.map { data ->
        val str = data[playlistKey] ?: ""
        if (str.isEmpty()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(Track.serializer()), str) }
            .getOrDefault(emptyList())
    }

    val savedIndex: Flow<Int> = context.dataStore.data.map { it[currentIndexKey] ?: -1 }

    val savedPosition: Flow<Long> = context.dataStore.data.map { it[positionKey] ?: 0L }

    suspend fun saveCookies(cookies: String) {
        context.dataStore.edit { it[cookiesKey] = cookies }
    }

    suspend fun saveUserInfo(id: String, name: String, avatar: String) {
        context.dataStore.edit { preferences ->
            preferences[userIdKey] = id
            preferences[userNameKey] = name
            preferences[userAvatarKey] = avatar
        }
    }

    suspend fun savePlaybackState(playlist: List<Track>, index: Int, position: Long) {
        val str = json.encodeToString(ListSerializer(Track.serializer()), playlist)
        context.dataStore.edit {
            it[playlistKey] = str
            it[currentIndexKey] = index
            it[positionKey] = position
        }
    }

    /** 保存音频质量偏好。quality 为 BiliConstants.AudioQuality 的 id。 */
    suspend fun saveAudioQuality(quality: Int) {
        context.dataStore.edit { it[audioQualityKey] = quality }
    }

    suspend fun clearPlaybackState() {
        context.dataStore.edit {
            it.remove(playlistKey)
            it.remove(currentIndexKey)
            it.remove(positionKey)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
