package com.biliaudio.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.biliaudio.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPlaylistManager @Inject constructor(context: Context) {
    private val prefs = context.getSharedPreferences("local_playlists", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllPlaylists(): List<LocalPlaylistMeta> {
        val metaJson = prefs.getString("meta", "[]") ?: "[]"
        return try { json.decodeFromString(metaJson) } catch (e: Exception) { emptyList() }
    }

    fun createPlaylist(name: String): String {
        val id = "local_${System.currentTimeMillis()}"
        val metas = getAllPlaylists().toMutableList()
        metas.add(LocalPlaylistMeta(id = id, name = name))
        prefs.edit().putString("meta", json.encodeToString(metas)).putString(id, "[]").apply()
        return id
    }

    fun getPlaylistTracks(playlistId: String): List<Track> {
        val raw = prefs.getString(playlistId, "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }

    fun addTrack(playlistId: String, track: Track) {
        val tracks = getPlaylistTracks(playlistId).toMutableList()
        if (tracks.none { it.id == track.id }) {
            tracks.add(track)
            prefs.edit().putString(playlistId, json.encodeToString(tracks)).apply()
        }
    }

    fun removeTrack(playlistId: String, trackId: String) {
        val tracks = getPlaylistTracks(playlistId).toMutableList()
        tracks.removeAll { it.id == trackId }
        prefs.edit().putString(playlistId, json.encodeToString(tracks)).apply()
    }

    fun deletePlaylist(playlistId: String) {
        val metas = getAllPlaylists().toMutableList()
        metas.removeAll { it.id == playlistId }
        prefs.edit().putString("meta", json.encodeToString(metas)).remove(playlistId).apply()
    }
}

@Serializable
data class LocalPlaylistMeta(val id: String, val name: String)
