package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.FavoriteFolder
import com.biliaudio.data.model.VideoItem
import com.biliaudio.data.preferences.PreferencesManager
import com.biliaudio.data.repository.AuthRepository
import com.biliaudio.data.repository.FavoriteRepository
import com.biliaudio.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val videoRepository: VideoRepository,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _folders = MutableStateFlow<List<FavoriteFolder>>(emptyList())
    val folders: StateFlow<List<FavoriteFolder>> = _folders.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _filteredVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val filteredVideos: StateFlow<List<VideoItem>> = _filteredVideos.asStateFlow()

    private val _isLoadingFolders = MutableStateFlow(false)
    val isLoadingFolders: StateFlow<Boolean> = _isLoadingFolders.asStateFlow()

    private val _isLoadingVideos = MutableStateFlow(false)
    val isLoadingVideos: StateFlow<Boolean> = _isLoadingVideos.asStateFlow()

    private val _selectedFolder = MutableStateFlow<FavoriteFolder?>(null)
    val selectedFolder: StateFlow<FavoriteFolder?> = _selectedFolder.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _searchKeyword = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId != null) {
                loadFolders(userId)
            } else {
                // 回退：从 preferences 读取
                val idStr = preferencesManager.userId.first()
                if (idStr.isNotEmpty()) {
                    loadFolders(idStr.toLong())
                }
            }
        }
    }

    fun loadFolders(mid: Long) {
        viewModelScope.launch {
            _isLoadingFolders.value = true
            when (val result = favoriteRepository.getFavoriteFolders(mid)) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        _folders.value = response.data.list
                    } else {
                        _toast.value = response.message
                    }
                }
                is Result.Error -> {
                    _toast.value = "加载收藏夹失败: ${result.message}"
                }
                Result.Loading -> {}
            }
            _isLoadingFolders.value = false
        }
    }

    fun selectFolder(folder: FavoriteFolder) {
        _selectedFolder.value = folder
        loadVideos(folder.id)
    }

    fun loadVideos(mediaId: Long, page: Int = 1) {
        viewModelScope.launch {
            _isLoadingVideos.value = true
            when (val result = favoriteRepository.getFavoriteResources(mediaId, page)) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        _videos.value = response.data.medias
                        applySearchFilter()
                    } else {
                        _toast.value = response.message
                    }
                }
                is Result.Error -> {
                    _toast.value = "加载视频失败: ${result.message}"
                }
                Result.Loading -> {}
            }
            _isLoadingVideos.value = false
        }
    }

    /**
     * 设置搜索关键词并过滤当前视频列表。
     */
    fun search(keyword: String) {
        _searchKeyword.value = keyword
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val keyword = _searchKeyword.value.trim()
        _filteredVideos.value = if (keyword.isEmpty()) {
            _videos.value
        } else {
            _videos.value.filter { video ->
                video.title.contains(keyword, ignoreCase = true) ||
                (video.upper?.name?.contains(keyword, ignoreCase = true) == true)
            }
        }
    }

    suspend fun videoToTrack(video: VideoItem): com.biliaudio.data.model.Track? {
        return when (val r = videoRepository.videoToTrack(video)) {
            is Result.Success -> r.data
            else -> null
        }
    }

    suspend fun videosToTracks(videos: List<VideoItem>): List<com.biliaudio.data.model.Track> {
        return when (val r = videoRepository.videosToTracks(videos)) {
            is Result.Success -> r.data
            else -> emptyList()
        }
    }

    fun refresh() {
        val mid = authRepository.getCurrentUserId()
        if (mid != null) {
            loadFolders(mid)
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
