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

    // ============ 懒解析（参考 BBPlayer 占位 URI 方案） ============
    // 以下方法创建的 Track.audioUrl 为占位 URI，不发起任何网络请求，
    // 真实音频地址在 ExoPlayer 加载时由 PlaybackService 的 ResolvingDataSource 解析。
    // 这使「播放全部」「单击播放」瞬时响应，长列表不再卡顿。

    /**
     * 创建懒解析 Track（瞬时，无网络请求）。
     */
    fun videoToLazyTrack(video: VideoItem): com.biliaudio.data.model.Track =
        videoRepository.videoToLazyTrack(video)

    /**
     * 批量创建懒解析 Track（瞬时，无网络请求）。
     * 「播放全部」长列表的关键：创建播放列表不再串行预请求 playurl。
     */
    fun videosToLazyTracks(videos: List<VideoItem>): List<com.biliaudio.data.model.Track> =
        videoRepository.videosToLazyTracks(videos)

    /**
     * 历史记录项转懒解析 Track。
     */
    fun historyItemToLazyTrack(item: HistoryItem): com.biliaudio.data.model.Track? {
        val video = item.toVideoItem() ?: return null
        return videoToLazyTrack(video)
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                var mid = authRepository.getCurrentUserId()
                // Cookie 中暂时拿不到 mid 时，回退到 DataStore 持久化的 user_id
                if (mid == null || mid <= 0) {
                    val idStr = preferencesManager.userId.first()
                    if (idStr.isNotEmpty()) {
                        mid = idStr.toLongOrNull()
                    }
                }
                com.biliaudio.util.DebugLogger.d("FavVM", "refresh: mid=$mid, isLoggedIn=${authRepository.isLoggedIn()}")
                if (mid != null && mid > 0) {
                    loadFolders(mid)
                    loadSeasons(mid)
                }
                loadHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ============ 合集 ============

    fun loadSeasons(mid: Long) {
        viewModelScope.launch {
            _isLoadingSeasons.value = true
            try {
                when (val result = favoriteRepository.getCollectedSeasons(mid)) {
                    is Result.Success -> {
                        val response = result.data
                        if (response.code == 0 && response.data != null) {
                            // 仅保留追更视频合集(attr==0)，过滤订阅的他人收藏夹(attr=22)与已失效项。
                            _seasons.value = response.data.list
                                .filter { it.isSeason && !it.isInvalid && it.id != 0L }
                                .map { it.toSeasonMeta() }
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
    fun loadSeasonVideos(seasonId: Long) {
        viewModelScope.launch {
            _isLoadingVideos.value = true
            try {
                when (val result = favoriteRepository.getSeasonVideos(seasonId)) {
                    is Result.Success -> {
                        val response = result.data
                        if (response.code == 0 && response.data != null) {
                            _videos.value = (response.data.medias ?: emptyList()).map { it.toVideoItem() }
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
     * 加载合集视频的便捷入口（新方案下无需 mid）。
     */
    fun loadSeasonOrSeriesVideosAuto(businessId: Long, isSeries: Boolean) {
        loadSeasonVideos(businessId)
    }

    // ============ 播放历史 ============

    fun loadHistory() {
        // 历史接口基于 Cookie 认证，未登录时直接清空并跳过请求，
        // 避免 init/refresh 在未登录态下触发失败请求。
        if (!authRepository.isLoggedIn()) {
            _history.value = emptyList()
            _isLoadingHistory.value = false
            return
        }
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
