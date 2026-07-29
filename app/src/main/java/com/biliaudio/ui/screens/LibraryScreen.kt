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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.biliaudio.ui.components.FolderCard
import com.biliaudio.ui.components.VideoCard
import com.biliaudio.ui.components.formatDurationMinSec
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.FavoriteViewModel
import com.biliaudio.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibraryTab(val label: String) {
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
    val seasons by favoriteViewModel.seasons.collectAsState()
    val isLoadingSeasons by favoriteViewModel.isLoadingSeasons.collectAsState()
    val history by favoriteViewModel.history.collectAsState()
    val isLoadingHistory by favoriteViewModel.isLoadingHistory.collectAsState()
    val userInfo by authViewModel.userInfo.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.Favorites) }
    val coroutineScope = rememberCoroutineScope()

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

            when (selectedTab) {
                LibraryTab.Favorites -> FavoritesTab(
                    folders = folders,
                    isLoading = isLoadingFolders,
                    onFolderClick = onFolderClick
                )
                LibraryTab.Seasons -> SeasonsTab(
                    seasons = seasons,
                    isLoading = isLoadingSeasons,
                    onSeasonClick = onSeasonClick
                )
                LibraryTab.History -> HistoryTab(
                    history = history,
                    isLoading = isLoadingHistory,
                    onPlay = { item ->
                        coroutineScope.launch {
                            val track = favoriteViewModel.historyItemToTrack(item)
                            if (track != null) {
                                playerViewModel.addToPlaylist(track)
                                playerViewModel.playAt(playerViewModel.playlist.value.size - 1)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    folders: List<com.biliaudio.data.model.FavoriteFolder>,
    isLoading: Boolean,
    onFolderClick: (Long, String) -> Unit
) {
    if (isLoading && folders.isEmpty()) {
        LoadingBox()
        return
    }
    if (folders.isEmpty()) {
        EmptyState(icon = Icons.Default.Folder, text = "暂无收藏夹")
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
    onSeasonClick: (Long, String, Boolean) -> Unit
) {
    if (isLoading && seasons.isEmpty()) {
        LoadingBox()
        return
    }
    if (seasons.isEmpty()) {
        EmptyState(icon = Icons.Default.LibraryBooks, text = "暂无合集")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
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
    }
}

@Composable
private fun HistoryTab(
    history: List<com.biliaudio.data.model.HistoryItem>,
    isLoading: Boolean,
    onPlay: (com.biliaudio.data.model.HistoryItem) -> Unit
) {
    if (isLoading && history.isEmpty()) {
        LoadingBox()
        return
    }
    if (history.isEmpty()) {
        EmptyState(icon = Icons.Default.History, text = "暂无播放历史")
        return
    }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    LazyColumn(
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
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
