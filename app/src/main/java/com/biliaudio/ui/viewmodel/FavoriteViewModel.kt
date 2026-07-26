package com.biliaudio.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.model.FavoriteFolder
import com.biliaudio.data.model.Track
import com.biliaudio.data.model.VideoItem
import com.biliaudio.data.model.VideoStreamResponse
import com.biliaudio.data.preferences.PreferencesManager
import com.biliaudio.data.repository.BiliRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoriteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BiliRepository()
    private val preferencesManager = PreferencesManager(application)

    private val _folders = MutableStateFlow<List<FavoriteFolder>>(emptyList())
    val folders: StateFlow<List<FavoriteFolder>> = _folders.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoadingFolders = MutableStateFlow(false)
    val isLoadingFolders: StateFlow<Boolean> = _isLoadingFolders.asStateFlow()

    private val _isLoadingVideos = MutableStateFlow(false)
    val isLoadingVideos: StateFlow<Boolean> = _isLoadingVideos.asStateFlow()

    private val _selectedFolder = MutableStateFlow<FavoriteFolder?>(null)
    val selectedFolder: StateFlow<FavoriteFolder?> = _selectedFolder.asStateFlow()

    private val _userId = MutableStateFlow<Long>(0L)
    val userId: StateFlow<Long> = _userId.asStateFlow()

    private val _audioUrl = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            preferencesManager.userId.collect { id ->
                if (id.isNotEmpty()) {
                    _userId.value = id.toLong()
                    loadFolders(id.toLong())
                }
            }
        }
    }

    fun loadFolders(mid: Long) {
        viewModelScope.launch {
            _isLoadingFolders.value = true
            try {
                val response = repository.getFavoriteFolders(mid)
                if (response.code == 0 && response.data != null) {
                    _folders.value = response.data.list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingFolders.value = false
            }
        }
    }

    fun selectFolder(folder: FavoriteFolder) {
        _selectedFolder.value = folder
        loadVideos(folder.id)
    }

    fun loadVideos(mediaId: Long, page: Int = 1) {
        viewModelScope.launch {
            _isLoadingVideos.value = true
            try {
                val response = repository.getFavoriteResources(mediaId, page)
                if (response.code == 0 && response.data != null) {
                    _videos.value = response.data.medias
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingVideos.value = false
            }
        }
    }

    suspend fun getAudioUrl(video: VideoItem): String? {
        return try {
            val bvid = video.bvid.ifEmpty { "" }
            val aid = video.aid
            val response = repository.getVideoStream(bvid = bvid, aid = aid)
            if (response.code == 0 && response.data != null) {
                val audioItem = response.data.dash?.audio?.firstOrNull()
                audioItem?.url
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun videoToTrack(video: VideoItem, audioUrl: String): Track {
        return Track(
            id = video.bvid.ifEmpty { video.aid.toString() },
            title = video.title,
            artist = video.upper?.name ?: "Unknown",
            coverUrl = video.cover,
            audioUrl = audioUrl,
            duration = video.duration.toLong() * 1000,
            bvid = video.bvid,
            aid = video.aid
        )
    }

    fun refresh() {
        val mid = _userId.value
        if (mid > 0) {
            loadFolders(mid)
        }
    }
}
