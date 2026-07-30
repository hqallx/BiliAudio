package com.biliaudio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (videos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (query.isEmpty()) "收藏夹为空" else "无匹配结果",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
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
                                val track = favoriteViewModel.videoToLazyTrack(video)
                                val currentPlaylist = playerViewModel.playlist.value
                                val index = currentPlaylist.indexOfFirst { it.id == track.id }
                                if (index >= 0) {
                                    playerViewModel.playAt(index)
                                } else {
                                    playerViewModel.addToPlaylist(track)
                                    playerViewModel.playAt(playerViewModel.playlist.value.size - 1)
                                }
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
            videos.forEach { video ->
                VideoCard(
                    title = video.title,
                    artist = video.upper?.name ?: "Unknown",
                    coverUrl = video.cover,
                    duration = formatDurationMinSec(video.duration),
                    onClick = {
                        // 懒解析：瞬时创建 Track 并播放，音频地址在播放时按需解析。
                        val track = favoriteViewModel.videoToLazyTrack(video)
                        playerViewModel.addToPlaylist(track)
                        playerViewModel.playAt(playerViewModel.playlist.value.size - 1)
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
