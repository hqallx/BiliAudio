package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.FavoriteFolder
import com.biliaudio.data.model.HistoryCursor
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

    // ============ 视频列表分页 ============
    // 收藏夹 / 合集视频复用同一组 _videos 状态，分页游标用 _videosPage/_videosHasMore。
    // _videosLoader 保存「加载第 N 页」的闭包，使 loadMoreVideos 无需区分数据来源。
    private val _videosPage = MutableStateFlow(1)
    private val _videosHasMore = MutableStateFlow(false)
    val videosHasMore: StateFlow<Boolean> = _videosHasMore.asStateFlow()

    private val _isLoadingMoreVideos = MutableStateFlow(false)
    val isLoadingMoreVideos: StateFlow<Boolean> = _isLoadingMoreVideos.asStateFlow()

    private var videosLoader: (suspend (Int) -> Unit)? = null

    private val _isLoadingFolders = MutableStateFlow(false)
    val isLoadingFolders: StateFlow<Boolean> = _isLoadingFolders.asStateFlow()

    /** 收藏夹列表加载错误信息，null 表示无错误。UI 据此展示重试入口。 */
    private val _foldersError = MutableStateFlow<String?>(null)
    val foldersError: StateFlow<String?> = _foldersError.asStateFlow()

    private val _isLoadingVideos = MutableStateFlow(false)
    val isLoadingVideos: StateFlow<Boolean> = _isLoadingVideos.asStateFlow()

    /** 视频列表加载错误信息，null 表示无错误。UI 据此展示重试入口。 */
    private val _videosError = MutableStateFlow<String?>(null)
    val videosError: StateFlow<String?> = _videosError.asStateFlow()

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

    /** 合集列表加载错误信息，null 表示无错误。UI 据此展示重试入口。 */
    private val _seasonsError = MutableStateFlow<String?>(null)
    val seasonsError: StateFlow<String?> = _seasonsError.asStateFlow()

    // ============ 合集列表分页 ============
    private val _seasonsPage = MutableStateFlow(1)
    private val _seasonsHasMore = MutableStateFlow(false)
    val seasonsHasMore: StateFlow<Boolean> = _seasonsHasMore.asStateFlow()

    private val _isLoadingMoreSeasons = MutableStateFlow(false)
    val isLoadingMoreSeasons: StateFlow<Boolean> = _isLoadingMoreSeasons.asStateFlow()

    /** 当前已加载的合集 mid，供 loadMoreSeasons 复用。 */
    private var seasonsMid: Long = 0

    // ============ 播放历史 ============

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    /** 历史记录加载错误信息，null 表示无错误。UI 据此展示重试入口。 */
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    // ============ 播放历史游标分页 ============
    // 历史接口为游标分页：每次返回 cursor.max / cursor.view_at，下一页用其作为请求参数。
    // 当返回空列表或 cursor 无推进时认为已到末页。
    private val _historyHasMore = MutableStateFlow(false)
    val historyHasMore: StateFlow<Boolean> = _historyHasMore.asStateFlow()

    private val _isLoadingMoreHistory = MutableStateFlow(false)
    val isLoadingMoreHistory: StateFlow<Boolean> = _isLoadingMoreHistory.asStateFlow()

    private var historyCursor: HistoryCursor = HistoryCursor()

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
            _foldersError.value = null
            when (val result = favoriteRepository.getFavoriteFolders(mid)) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        _folders.value = response.data.list
                    } else {
                        _foldersError.value = response.message.ifEmpty { "加载收藏夹失败" }
                        _toast.value = _foldersError.value
                    }
                }
                is Result.Error -> {
                    _foldersError.value = "加载收藏夹失败: ${result.message}"
                    _toast.value = _foldersError.value
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
        currentVideoMediaId = mediaId
        videosLoader = { p -> loadVideosPage(mediaId, p) }
        viewModelScope.launch { loadVideosPage(mediaId, page) }
    }

    /** 当前视频列表所属的收藏夹 mediaId，删除视频时需要。 */
    private var currentVideoMediaId: Long = 0L

    private suspend fun loadVideosPage(mediaId: Long, page: Int) {
        val isFirstPage = page == 1
        if (isFirstPage) {
            _isLoadingVideos.value = true
            _videosError.value = null
        } else {
            _isLoadingMoreVideos.value = true
        }
        when (val result = favoriteRepository.getFavoriteResources(mediaId, page)) {
            is Result.Success -> {
                val response = result.data
                if (response.code == 0 && response.data != null) {
                    val newVideos = response.data.medias
                    _videos.value = if (isFirstPage) newVideos else _videos.value + newVideos
                    _videosPage.value = page
                    _videosHasMore.value = response.data.has_more
                    applySearchFilter()
                } else {
                    if (isFirstPage) {
                        _videosError.value = response.message.ifEmpty { "加载视频失败" }
                    } else {
                        _toast.value = response.message.ifEmpty { "加载更多失败" }
                    }
                }
            }
            is Result.Error -> {
                if (isFirstPage) {
                    _videosError.value = "加载视频失败: ${result.message}"
                } else {
                    _toast.value = "加载更多失败: ${result.message}"
                }
            }
            Result.Loading -> {}
        }
        _isLoadingVideos.value = false
        _isLoadingMoreVideos.value = false
    }

    /**
     * 加载下一页视频（收藏夹或合集，由 [videosLoader] 决定具体接口）。
     * 已在加载中或无更多数据时直接返回，避免重复请求。
     */
    fun loadMoreVideos() {
        if (_isLoadingMoreVideos.value || !_videosHasMore.value) return
        val loader = videosLoader ?: return
        viewModelScope.launch { loader(_videosPage.value + 1) }
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
                val mid = resolveMid()
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
        seasonsMid = mid
        viewModelScope.launch { loadSeasonsPage(mid, 1) }
    }

    private suspend fun loadSeasonsPage(mid: Long, page: Int) {
        val isFirstPage = page == 1
        if (isFirstPage) {
            _isLoadingSeasons.value = true
            _seasonsError.value = null
        } else {
            _isLoadingMoreSeasons.value = true
        }
        try {
            when (val result = favoriteRepository.getCollectedSeasons(mid, page)) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        // 仅保留追更视频合集(attr==0)，过滤订阅的他人收藏夹(attr=22)与已失效项。
                        val newSeasons = response.data.list
                            .filter { it.isSeason && !it.isInvalid && it.id != 0L }
                            .map { it.toSeasonMeta() }
                        _seasons.value = if (isFirstPage) newSeasons else _seasons.value + newSeasons
                        _seasonsPage.value = page
                        _seasonsHasMore.value = response.data.has_more
                    } else {
                        val msg = response.message.ifEmpty { "加载合集失败" }
                        if (isFirstPage) {
                            _seasonsError.value = msg
                            _toast.value = msg
                        } else {
                            _toast.value = msg
                        }
                    }
                }
                is Result.Error -> {
                    val msg = "加载合集失败: ${result.message}"
                    if (isFirstPage) {
                        _seasonsError.value = msg
                        _toast.value = msg
                    } else {
                        _toast.value = "加载更多失败: ${result.message}"
                    }
                }
                Result.Loading -> {}
            }
        } catch (e: Exception) {
            val msg = "加载合集失败: ${e.message ?: "未知错误"}"
            if (isFirstPage) {
                _seasonsError.value = msg
                _toast.value = msg
            } else {
                _toast.value = "加载更多失败: ${e.message ?: "未知错误"}"
            }
            e.printStackTrace()
        }
        _isLoadingSeasons.value = false
        _isLoadingMoreSeasons.value = false
    }

    /**
     * 加载下一页合集。已在加载中或无更多数据时直接返回。
     */
    fun loadMoreSeasons() {
        if (_isLoadingMoreSeasons.value || !_seasonsHasMore.value || seasonsMid <= 0) return
        viewModelScope.launch { loadSeasonsPage(seasonsMid, _seasonsPage.value + 1) }
    }

    /**
     * 加载指定合集内的视频列表，结果写入 [videos]/[filteredVideos]，
     * 供 VideoListScreen 复用展示与播放。
     */
    fun loadSeasonVideos(seasonId: Long, page: Int = 1) {
        videosLoader = { p -> loadSeasonVideosPage(seasonId, p) }
        viewModelScope.launch { loadSeasonVideosPage(seasonId, page) }
    }

    private suspend fun loadSeasonVideosPage(seasonId: Long, page: Int) {
        val isFirstPage = page == 1
        if (isFirstPage) {
            _isLoadingVideos.value = true
            _videosError.value = null
        } else {
            _isLoadingMoreVideos.value = true
        }
        try {
            when (val result = favoriteRepository.getSeasonVideos(seasonId, page)) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        val newVideos = (response.data.medias ?: emptyList()).map { it.toVideoItem() }
                        _videos.value = if (isFirstPage) newVideos else _videos.value + newVideos
                        _videosPage.value = page
                        // season/list 接口未返回 has_more，按「本页满 pageSize 即视为可能有更多」启发式判断。
                        _videosHasMore.value = newVideos.size >= BiliConstants.DEFAULT_PAGE_SIZE
                        applySearchFilter()
                    } else {
                        if (isFirstPage) {
                            _videosError.value = response.message.ifEmpty { "加载合集视频失败" }
                        } else {
                            _toast.value = response.message.ifEmpty { "加载更多失败" }
                        }
                    }
                }
                is Result.Error -> {
                    if (isFirstPage) {
                        _videosError.value = "加载合集视频失败: ${result.message}"
                    } else {
                        _toast.value = "加载更多失败: ${result.message}"
                    }
                }
                Result.Loading -> {}
            }
        } catch (e: Exception) {
            if (isFirstPage) {
                _videosError.value = "加载合集视频失败: ${e.message ?: "未知错误"}"
            } else {
                _toast.value = "加载更多失败: ${e.message ?: "未知错误"}"
            }
            e.printStackTrace()
        }
        _isLoadingVideos.value = false
        _isLoadingMoreVideos.value = false
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
            _historyError.value = null
            _historyHasMore.value = false
            _isLoadingHistory.value = false
            return
        }
        viewModelScope.launch { loadHistoryPage(isFirstPage = true) }
    }

    private suspend fun loadHistoryPage(isFirstPage: Boolean) {
        if (isFirstPage) {
            _isLoadingHistory.value = true
            _historyError.value = null
            // 首页游标重置
            historyCursor = HistoryCursor()
        } else {
            _isLoadingMoreHistory.value = true
        }
        try {
            when (val result = favoriteRepository.getHistory(
                max = historyCursor.max,
                viewAt = historyCursor.view_at
            )) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        // 仅保留可转为 VideoItem 的稿件历史
                        val newItems = response.data.list
                            .filter { it.history?.business == "archive" }
                        _history.value = if (isFirstPage) newItems else _history.value + newItems
                        historyCursor = response.data.cursor
                        // 游标无推进或本页为空 → 已到末页
                        _historyHasMore.value = newItems.isNotEmpty() &&
                            (response.data.cursor.max > 0 || response.data.cursor.view_at > 0)
                    } else {
                        val msg = response.message.ifEmpty { "加载历史记录失败" }
                        if (isFirstPage) {
                            _historyError.value = msg
                            _toast.value = msg
                        } else {
                            _toast.value = msg
                        }
                    }
                }
                is Result.Error -> {
                    val msg = "加载历史记录失败: ${result.message}"
                    if (isFirstPage) {
                        _historyError.value = msg
                        _toast.value = msg
                    } else {
                        _toast.value = "加载更多失败: ${result.message}"
                    }
                }
                Result.Loading -> {}
            }
        } catch (e: Exception) {
            val msg = "加载历史记录失败: ${e.message ?: "未知错误"}"
            if (isFirstPage) {
                _historyError.value = msg
                _toast.value = msg
            } else {
                _toast.value = "加载更多失败: ${e.message ?: "未知错误"}"
            }
            e.printStackTrace()
        }
        _isLoadingHistory.value = false
        _isLoadingMoreHistory.value = false
    }

    /**
     * 加载下一页历史记录。已在加载中或无更多数据时直接返回。
     */
    fun loadMoreHistory() {
        if (_isLoadingMoreHistory.value || !_historyHasMore.value) return
        viewModelScope.launch { loadHistoryPage(isFirstPage = false) }
    }

    /** 将历史记录项转为 VideoItem，用于播放。 */
    suspend fun historyItemToTrack(item: HistoryItem): com.biliaudio.data.model.Track? {
        val video = item.toVideoItem() ?: return null
        return videoToTrack(video)
    }

    fun consumeToast() {
        _toast.value = null
    }

    // ============ 重试入口 ============
    // 各 Tab 加载失败时由 UI 调用，重新解析 mid 并触发对应加载。
    // 历史 tab 无需 mid，直接 reload。

    /** 重试加载收藏夹列表。 */
    fun retryFolders() {
        viewModelScope.launch {
            val mid = resolveMid()
            if (mid != null && mid > 0) loadFolders(mid)
        }
    }

    /** 重试加载合集列表。 */
    fun retrySeasons() {
        viewModelScope.launch {
            val mid = resolveMid()
            if (mid != null && mid > 0) loadSeasons(mid)
        }
    }

    /** 重试加载播放历史。 */
    fun retryHistory() {
        loadHistory()
    }

    /**
     * 解析当前用户 mid：优先从 Cookie，回退到 DataStore 持久化的 user_id。
     */
    private suspend fun resolveMid(): Long? {
        var mid = authRepository.getCurrentUserId()
        if (mid == null || mid <= 0) {
            val idStr = preferencesManager.userId.first()
            if (idStr.isNotEmpty()) {
                mid = idStr.toLongOrNull()
            }
        }
        return mid
    }

    // ============ 删除操作 ============

    /**
     * 删除收藏夹：乐观更新（先从列表移除），失败回滚并提示。
     */
    fun deleteFolder(folder: FavoriteFolder) {
        val original = _folders.value.toList()
        _folders.value = _folders.value.filter { it.id != folder.id }
        viewModelScope.launch {
            when (val result = favoriteRepository.deleteFolder(folder.id)) {
                is Result.Success -> {
                    if (result.data.code == 0) {
                        _toast.value = "已删除「${folder.title}」"
                    } else {
                        _folders.value = original
                        _toast.value = result.data.message.ifEmpty { "删除失败" }
                    }
                }
                is Result.Error -> {
                    _folders.value = original
                    _toast.value = result.message
                }
                Result.Loading -> {}
            }
        }
    }

    /**
     * 删除收藏夹内单个视频：乐观更新，失败回滚。
     * @param video 要删除的视频（VideoItem.id 即为 avid）
     */
    fun deleteVideo(video: VideoItem) {
        val mediaId = currentVideoMediaId
        if (mediaId <= 0) {
            _toast.value = "无法删除：未知收藏夹"
            return
        }
        val original = _videos.value.toList()
        val originalFiltered = _filteredVideos.value.toList()
        _videos.value = _videos.value.filter { it.id != video.id }
        _filteredVideos.value = _filteredVideos.value.filter { it.id != video.id }
        viewModelScope.launch {
            when (val result = favoriteRepository.deleteResource(mediaId, video.id)) {
                is Result.Success -> {
                    if (result.data.code == 0) {
                        _toast.value = "已移除「${video.title}」"
                    } else {
                        _videos.value = original
                        _filteredVideos.value = originalFiltered
                        _toast.value = result.data.message.ifEmpty { "删除失败" }
                    }
                }
                is Result.Error -> {
                    _videos.value = original
                    _filteredVideos.value = originalFiltered
                    _toast.value = result.message
                }
                Result.Loading -> {}
            }
        }
    }

    /**
     * 取消追更合集：乐观更新（先从列表移除），失败回滚。
     * @param seasonMeta 合集元数据（season_id 为字符串，转为 Long 调用接口）
     */
    fun unfollowSeason(seasonMeta: SeasonMeta) {
        val seasonId = seasonMeta.season_id.toLongOrNull() ?: 0L
        if (seasonId <= 0) {
            _toast.value = "无法取消追更：无效的合集 id"
            return
        }
        val original = _seasons.value.toList()
        _seasons.value = _seasons.value.filter { it.season_id != seasonMeta.season_id }
        viewModelScope.launch {
            when (val result = favoriteRepository.unfollowSeason(seasonId)) {
                is Result.Success -> {
                    if (result.data.code == 0) {
                        _toast.value = "已取消追更「${seasonMeta.name}」"
                    } else {
                        _seasons.value = original
                        _toast.value = result.data.message.ifEmpty { "取消追更失败" }
                    }
                }
                is Result.Error -> {
                    _seasons.value = original
                    _toast.value = result.message
                }
                Result.Loading -> {}
            }
        }
    }
}
