package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.FavoriteFolder
import com.biliaudio.data.model.HistoryItem
import com.biliaudio.data.model.SeasonMeta
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

    // ============ 合集 ============

    private val _seasons = MutableStateFlow<List<SeasonMeta>>(emptyList())
    val seasons: StateFlow<List<SeasonMeta>> = _seasons.asStateFlow()

    private val _isLoadingSeasons = MutableStateFlow(false)
    val isLoadingSeasons: StateFlow<Boolean> = _isLoadingSeasons.asStateFlow()

    // ============ 播放历史 ============

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    loadFolders(userId)
                    loadSeasons(userId)
                } else {
                    // 回退：从 preferences 读取
                    val idStr = preferencesManager.userId.first()
                    if (idStr.isNotEmpty()) {
                        val mid = idStr.toLong()
                        loadFolders(mid)
                        loadSeasons(mid)
                    }
                }
                loadHistory()
            } catch (e: Exception) {
                // DataStore 读取或 Cookie 访问失败不应导致应用崩溃。
                // 用户可手动下拉刷新重新加载。
                e.printStackTrace()
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
        try {
            val mid = authRepository.getCurrentUserId()
            if (mid != null) {
                loadFolders(mid)
                loadSeasons(mid)
            }
            loadHistory()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ============ 合集 ============

    fun loadSeasons(mid: Long) {
        viewModelScope.launch {
            _isLoadingSeasons.value = true
            try {
                when (val result = favoriteRepository.getSeasonsSeries(mid)) {
                    is Result.Success -> {
                        val response = result.data
                        if (response.code == 0 && response.data != null) {
                            _seasons.value = response.data.items
                                .mapNotNull { it.meta }
                                .filter { it.season_id != 0L }
                        } else {
                            _toast.value = response.message
                        }
                    }
                    is Result.Error -> {
                        _toast.value = "加载合集失败: ${result.message}"
                    }
                    Result.Loading -> {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoadingSeasons.value = false
        }
    }

    /**
     * 加载指定合集内的视频列表，结果写入 [videos]/[filteredVideos]，
     * 供 VideoListScreen 复用展示与播放。
     */
    fun loadSeasonVideos(mid: Long, seasonId: Long) {
        viewModelScope.launch {
            _isLoadingVideos.value = true
            try {
                when (val result = favoriteRepository.getSeasonArchives(mid, seasonId)) {
                    is Result.Success -> {
                        val response = result.data
                        if (response.code == 0 && response.data != null) {
                            _videos.value = response.data.archives.map { it.toVideoItem() }
                            applySearchFilter()
                        } else {
                            _toast.value = response.message
                        }
                    }
                    is Result.Error -> {
                        _toast.value = "加载合集视频失败: ${result.message}"
                    }
                    Result.Loading -> {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoadingVideos.value = false
        }
    }

    /**
     * 自动获取当前用户 mid 后加载合集视频，供 UI 层无需关心 mid 时调用。
     */
    fun loadSeasonVideosAuto(seasonId: Long) {
        viewModelScope.launch {
            try {
                val mid = authRepository.getCurrentUserId()
                if (mid != null) {
                    loadSeasonVideos(mid, seasonId)
                } else {
                    _toast.value = "未登录，无法加载合集"
                    _isLoadingVideos.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoadingVideos.value = false
            }
        }
    }

    // ============ 播放历史 ============

    fun loadHistory() {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                when (val result = favoriteRepository.getHistory()) {
                    is Result.Success -> {
                        val response = result.data
                        if (response.code == 0 && response.data != null) {
                            // 仅保留可转为 VideoItem 的稿件历史
                            _history.value = response.data.list
                                .filter { it.history?.business == "archive" }
                        } else {
                            _toast.value = response.message
                        }
                    }
                    is Result.Error -> {
                        _toast.value = "加载历史记录失败: ${result.message}"
                    }
                    Result.Loading -> {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoadingHistory.value = false
        }
    }

    /** 将历史记录项转为 VideoItem，用于播放。 */
    suspend fun historyItemToTrack(item: HistoryItem): com.biliaudio.data.model.Track? {
        val video = item.toVideoItem() ?: return null
        return videoToTrack(video)
    }

    fun consumeToast() {
        _toast.value = null
    }
}
