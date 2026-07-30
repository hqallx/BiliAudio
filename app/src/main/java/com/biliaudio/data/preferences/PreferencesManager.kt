package com.biliaudio.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    // 调试日志开关
    private val debugEnabledKey = booleanPreferencesKey("debug_enabled")

    private val json = Json { ignoreUnknownKeys = true }

    val cookies: Flow<String> = context.dataStore.data.map { it[cookiesKey] ?: "" }

    val userId: Flow<String> = context.dataStore.data.map { it[userIdKey] ?: "" }

    val userName: Flow<String> = context.dataStore.data.map { it[userNameKey] ?: "" }

    val userAvatar: Flow<String> = context.dataStore.data.map { it[userAvatarKey] ?: "" }

    /** 用户偏好的音频质量 id，0 表示使用默认（192K AAC）。 */
    val audioQuality: Flow<Int> = context.dataStore.data.map { it[audioQualityKey] ?: 0 }

    /** 调试日志开关，默认关闭。 */
    val debugEnabled: Flow<Boolean> = context.dataStore.data.map { it[debugEnabledKey] ?: false }

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

    /** 保存调试日志开关。 */
    suspend fun saveDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { it[debugEnabledKey] = enabled }
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

    /**
     * 一次性同步读取已持久化的用户信息（挂起）。
     * 用于进程重启后立即恢复头像/名称，避免等待 nav 接口。
     */
    suspend fun getCachedUserInfo(): CachedUserInfo {
        val id = userId.first()
        val name = userName.first()
        val avatar = userAvatar.first()
        return CachedUserInfo(id, name, avatar)
    }
}

data class CachedUserInfo(
    val id: String,
    val name: String,
    val avatar: String
) {
    val hasCached: Boolean get() = name.isNotEmpty()
}
