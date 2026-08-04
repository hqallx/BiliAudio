package com.biliaudio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biliaudio.ui.components.ListEmptyState
import com.biliaudio.ui.components.ListErrorState
import com.biliaudio.ui.components.ListLoadingState
import com.biliaudio.ui.components.VideoCard
import com.biliaudio.ui.components.formatDurationMinSec
import com.biliaudio.ui.viewmodel.FavoriteViewModel
import com.biliaudio.ui.viewmodel.PlayerViewModel

/** 视频列表的数据来源，区分收藏夹与合集/系列。 */
enum class VideoListSource { FAVORITE, SEASON }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    folderId: Long,
    folderName: String,
    source: VideoListSource = VideoListSource.FAVORITE,
    isSeries: Boolean = false,
    favoriteViewModel: FavoriteViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val videos by favoriteViewModel.filteredVideos.collectAsState()
    val isLoading by favoriteViewModel.isLoadingVideos.collectAsState()
    val videosError by favoriteViewModel.videosError.collectAsState()
    val hasMore by favoriteViewModel.videosHasMore.collectAsState()
    val isLoadingMore by favoriteViewModel.isLoadingMoreVideos.collectAsState()
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

    // 列表滚动状态：滑到接近底部时自动加载下一页。
    // 仅在非搜索态（query 为空）触发，避免在过滤结果上分页。
    val listState = rememberLazyListState()
    LaunchedEffect(listState, videos, hasMore, isLoadingMore, query) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= 0 && total > 0 && lastVisible >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && query.isBlank() && hasMore && !isLoadingMore) {
                favoriteViewModel.loadMoreVideos()
            }
        }
    }

    // 进入页面时根据来源触发加载：
    // - FAVORITE: folderId 为收藏夹 mediaId
    // - SEASON: folderId 为合集 seasonId 或系列 seriesId，由 isSeries 决定走哪个接口（mid 由 ViewModel 自动获取）
    LaunchedEffect(folderId, source, isSeries) {
        when (source) {
            VideoListSource.FAVORITE -> favoriteViewModel.loadVideos(folderId)
            VideoListSource.SEASON -> favoriteViewModel.loadSeasonOrSeriesVideosAuto(folderId, isSeries)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBackClick() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { active = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (videos.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // 懒解析：瞬时创建播放列表，音频地址在播放时按需解析。
                        // 不再阻塞等待所有视频的 playurl，长列表「播放全部」秒响应。
                        val tracks = favoriteViewModel.videosToLazyTracks(videos)
                        if (tracks.isNotEmpty()) {
                            playerViewModel.setPlaylist(tracks)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    },
                    text = { Text("播放全部") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && videos.isEmpty()) {
                ListLoadingState()
            } else if (videosError != null && videos.isEmpty()) {
                // 错误状态：复用共享错误态封装（CloudOff 图标 + 文案 + 重试）
                ListErrorState(
                    message = videosError ?: "加载失败",
                    onRetry = {
                        when (source) {
                            VideoListSource.FAVORITE -> favoriteViewModel.loadVideos(folderId)
                            VideoListSource.SEASON -> favoriteViewModel.loadSeasonOrSeriesVideosAuto(folderId, isSeries)
                        }
                    }
                )
            } else if (videos.isEmpty()) {
                ListEmptyState(
                    icon = Icons.Default.PlayArrow,
                    text = if (query.isEmpty()) "收藏夹为空" else "无匹配结果"
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(videos) { video ->
                        VideoCard(
                            title = video.title,
                            artist = video.upper?.name ?: "Unknown",
                            coverUrl = video.cover,
                            duration = formatDurationMinSec(video.duration),
                            onClick = {
                                // 懒解析：瞬时创建 Track 并播放，音频地址在播放时按需解析。
                                // playOrAdd 统一去重：已存在则定位播放，否则追加并播放。
                                playerViewModel.playOrAdd(favoriteViewModel.videoToLazyTrack(video))
                            },
                            onAddNext = {
                                // 下一首播放：插队到当前曲目之后
                                playerViewModel.addNext(favoriteViewModel.videoToLazyTrack(video))
                            }
                        )
                    }
                    // 加载更多尾部：仅在非搜索态展示，避免在过滤结果上误导用户。
                    if (isLoadingMore && query.isBlank()) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // 搜索栏
    if (active) {
        SearchBar(
            query = query,
            onQueryChange = { newQuery ->
                query = newQuery
                favoriteViewModel.search(newQuery)
            },
            onSearch = { favoriteViewModel.search(it) },
            active = active,
            onActiveChange = { active = it },
            placeholder = { Text("搜索视频或UP主") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = {
                    if (query.isNotEmpty()) {
                        query = ""
                        favoriteViewModel.search("")
                    } else {
                        active = false
                    }
                }) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        ) {
            // 用 LazyColumn 而非 forEach，避免长搜索结果一次性组合造成卡顿/OOM
            LazyColumn {
                items(videos) { video ->
                    VideoCard(
                        title = video.title,
                        artist = video.upper?.name ?: "Unknown",
                        coverUrl = video.cover,
                        duration = formatDurationMinSec(video.duration),
                        onClick = {
                            // 懒解析：瞬时创建 Track 并播放，音频地址在播放时按需解析。
                            // playOrAdd 统一去重：与主列表点击行为一致，避免重复条目。
                            playerViewModel.playOrAdd(favoriteViewModel.videoToLazyTrack(video))
                        },
                        onAddNext = {
                            // 下一首播放：插队到当前曲目之后
                            playerViewModel.addNext(favoriteViewModel.videoToLazyTrack(video))
                        }
                    )
                }
            }
        }
    }
}
