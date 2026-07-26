package com.biliaudio.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biliaudio.data.model.Track
import com.biliaudio.data.model.VideoItem
import com.biliaudio.ui.components.VideoCard
import com.biliaudio.ui.components.formatDurationMinSec
import com.biliaudio.ui.viewmodel.FavoriteViewModel
import com.biliaudio.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    folderId: Long,
    folderName: String,
    favoriteViewModel: FavoriteViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val videos by favoriteViewModel.videos.collectAsState()
    val isLoading by favoriteViewModel.isLoadingVideos.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isLoadingAudio by remember { mutableStateOf(false) }

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
                }
            )
        },
        floatingActionButton = {
            if (videos.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoadingAudio = true
                            val tracks = mutableListOf<Track>()
                            for (video in videos) {
                                val audioUrl = favoriteViewModel.getAudioUrl(video)
                                if (audioUrl != null) {
                                    tracks.add(favoriteViewModel.videoToTrack(video, audioUrl))
                                }
                            }
                            if (tracks.isNotEmpty()) {
                                playerViewModel.setPlaylist(tracks)
                            }
                            isLoadingAudio = false
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
                    androidx.compose.material3.CircularProgressIndicator()
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
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "收藏夹为空",
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
                                coroutineScope.launch {
                                    val audioUrl = favoriteViewModel.getAudioUrl(video)
                                    if (audioUrl != null) {
                                        val track = favoriteViewModel.videoToTrack(video, audioUrl)
                                        val currentPlaylist = playerViewModel.playlist.value
                                        val index = currentPlaylist.indexOfFirst { it.id == track.id }
                                        if (index >= 0) {
                                            playerViewModel.playAt(index)
                                        } else {
                                            playerViewModel.addToPlaylist(track)
                                            playerViewModel.playAt(playerViewModel.playlist.value.size - 1)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (isLoadingAudio) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在获取音频地址...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
