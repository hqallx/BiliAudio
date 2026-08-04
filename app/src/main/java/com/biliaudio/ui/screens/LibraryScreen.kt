package com.biliaudio.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.biliaudio.ui.components.FolderCard
import com.biliaudio.ui.components.ListEmptyState
import com.biliaudio.ui.components.ListErrorState
import com.biliaudio.ui.components.ListLoadingState
import com.biliaudio.ui.components.VideoCard
import com.biliaudio.ui.components.formatDurationMinSec
import com.biliaudio.ui.theme.Motion
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.FavoriteViewModel
import com.biliaudio.ui.viewmodel.PlayerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibraryTab(val label: String) {
    Playlist("播放列表"),
    Favorites("收藏夹"),
    Seasons("合集"),
    History("播放历史")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    favoriteViewModel: FavoriteViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onFolderClick: (Long, String) -> Unit = { _, _ -> },
    onSeasonClick: (Long, String, Boolean) -> Unit = { _, _, _ -> },
    onLoginClick: () -> Unit = {}
) {
    val folders by favoriteViewModel.folders.collectAsState()
    val isLoadingFolders by favoriteViewModel.isLoadingFolders.collectAsState()
    val foldersError by favoriteViewModel.foldersError.collectAsState()
    val seasons by favoriteViewModel.seasons.collectAsState()
    val isLoadingSeasons by favoriteViewModel.isLoadingSeasons.collectAsState()
    val seasonsError by favoriteViewModel.seasonsError.collectAsState()
    val seasonsHasMore by favoriteViewModel.seasonsHasMore.collectAsState()
    val isLoadingMoreSeasons by favoriteViewModel.isLoadingMoreSeasons.collectAsState()
    val history by favoriteViewModel.history.collectAsState()
    val isLoadingHistory by favoriteViewModel.isLoadingHistory.collectAsState()
    val historyError by favoriteViewModel.historyError.collectAsState()
    val historyHasMore by favoriteViewModel.historyHasMore.collectAsState()
    val isLoadingMoreHistory by favoriteViewModel.isLoadingMoreHistory.collectAsState()
    val userInfo by authViewModel.userInfo.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    // 播放列表数据
    val playlist by playerViewModel.playlist.collectAsState()
    val currentIndex by playerViewModel.currentIndex.collectAsState()
    val currentTrack by playerViewModel.currentTrack.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.Playlist) }

    // 首次使用或登录成功后，FavoriteViewModel.init 可能因登录态尚未就绪而拿不到 mid，
    // 导致收藏夹/合集为空、需用户手动刷新。这里监听登录状态变化，登录后自动加载。
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            favoriteViewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "库",
                            style = MaterialTheme.typography.titleLarge
                        )
                        userInfo?.name?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    userInfo?.face?.let { avatarUrl ->
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { favoriteViewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!isLoggedIn) {
            // 未登录：所有 Tab 共用同一个登录提示页
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "登录后查看库内容",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(text = "去登录", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(paddingValues)) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            // Tab 内容切换：Crossfade 淡入淡出，避免瞬时替换的生硬感
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingStandard),
                label = "libraryTab"
            ) { tab ->
                when (tab) {
                    LibraryTab.Favorites -> FavoritesTab(
                        folders = folders,
                        isLoading = isLoadingFolders,
                        error = foldersError,
                        onRetry = { favoriteViewModel.retryFolders() },
                        onFolderClick = onFolderClick
                    )
                    LibraryTab.Seasons -> SeasonsTab(
                        seasons = seasons,
                        isLoading = isLoadingSeasons,
                        error = seasonsError,
                        onRetry = { favoriteViewModel.retrySeasons() },
                        hasMore = seasonsHasMore,
                        isLoadingMore = isLoadingMoreSeasons,
                        onLoadMore = { favoriteViewModel.loadMoreSeasons() },
                        onSeasonClick = onSeasonClick
                    )
                    LibraryTab.History -> HistoryTab(
                        history = history,
                        isLoading = isLoadingHistory,
                        error = historyError,
                        onRetry = { favoriteViewModel.retryHistory() },
                        hasMore = historyHasMore,
                        isLoadingMore = isLoadingMoreHistory,
                        onLoadMore = { favoriteViewModel.loadMoreHistory() },
                        onPlay = { item ->
                            // 懒解析：瞬时创建 Track 并播放，音频地址在播放时按需解析。
                            // playOrAdd 统一去重：与收藏夹/合集列表点击行为一致，避免重复条目。
                            val track = favoriteViewModel.historyItemToLazyTrack(item)
                            if (track != null) {
                                playerViewModel.playOrAdd(track)
                            }
                        }
                    )
                    LibraryTab.Playlist -> PlaylistTab(
                        playlist = playlist,
                        currentTrack = currentTrack,
                        currentIndex = currentIndex,
                        onPlayAt = { index -> playerViewModel.playAt(index) },
                        onRemove = { index -> playerViewModel.removeFromPlaylist(index) },
                        onClear = { playerViewModel.clearPlaylist() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    folders: List<com.biliaudio.data.model.FavoriteFolder>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onFolderClick: (Long, String) -> Unit
) {
    if (isLoading && folders.isEmpty()) {
        ListLoadingState()
        return
    }
    if (error != null && folders.isEmpty()) {
        ListErrorState(message = error, onRetry = onRetry)
        return
    }
    if (folders.isEmpty()) {
        ListEmptyState(icon = Icons.Default.Folder, text = "暂无收藏夹")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(folders) { folder ->
            FolderCard(
                title = folder.title,
                count = folder.mediaCount,
                coverUrl = folder.cover,
                onClick = { onFolderClick(folder.id, folder.title) }
            )
        }
    }
}

@Composable
private fun SeasonsTab(
    seasons: List<com.biliaudio.data.model.SeasonMeta>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onSeasonClick: (Long, String, Boolean) -> Unit
) {
    if (isLoading && seasons.isEmpty()) {
        ListLoadingState()
        return
    }
    if (error != null && seasons.isEmpty()) {
        ListErrorState(message = error, onRetry = onRetry)
        return
    }
    if (seasons.isEmpty()) {
        ListEmptyState(icon = Icons.Default.LibraryBooks, text = "暂无合集")
        return
    }
    val gridState = rememberLazyGridState()
    // 滑到接近底部时自动加载下一页
    LaunchedEffect(gridState, seasons, hasMore, isLoadingMore) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = gridState.layoutInfo.totalItemsCount
            lastVisible >= 0 && total > 0 && lastVisible >= total - 4
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) {
                onLoadMore()
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(seasons) { season ->
            FolderCard(
                title = season.name,
                count = season.total,
                coverUrl = season.cover,
                onClick = { onSeasonClick(season.businessId, season.name, season.isSeries) }
            )
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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

@Composable
private fun HistoryTab(
    history: List<com.biliaudio.data.model.HistoryItem>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onPlay: (com.biliaudio.data.model.HistoryItem) -> Unit
) {
    if (isLoading && history.isEmpty()) {
        ListLoadingState()
        return
    }
    if (error != null && history.isEmpty()) {
        ListErrorState(message = error, onRetry = onRetry)
        return
    }
    if (history.isEmpty()) {
        ListEmptyState(icon = Icons.Default.History, text = "暂无播放历史")
        return
    }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val listState = rememberLazyListState()
    // 滑到接近底部时自动加载下一页
    LaunchedEffect(listState, history, hasMore, isLoadingMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= 0 && total > 0 && lastVisible >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) {
                onLoadMore()
            }
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(history) { item ->
            val viewTime = remember(item.view_at) {
                dateFormat.format(Date(item.view_at * 1000))
            }
            VideoCard(
                title = item.title,
                artist = viewTime,
                coverUrl = item.cover,
                duration = formatDurationMinSec(item.duration),
                onClick = { onPlay(item) }
            )
        }
        if (isLoadingMore) {
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

@Composable
private fun PlaylistTab(
    playlist: List<com.biliaudio.data.model.Track>,
    currentTrack: com.biliaudio.data.model.Track?,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit
) {
    if (playlist.isEmpty()) {
        ListEmptyState(icon = Icons.Default.QueueMusic, text = "播放列表为空")
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${playlist.size} 首",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "清空播放列表",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(playlist) { index, track ->
                com.biliaudio.ui.components.VideoCard(
                    title = track.title,
                    artist = track.artist,
                    coverUrl = track.coverUrl,
                    duration = com.biliaudio.ui.components.formatDurationMinSec(
                        (track.duration / 1000).toInt()
                    ),
                    onClick = { onPlayAt(index) }
                )
            }
        }
    }
}
