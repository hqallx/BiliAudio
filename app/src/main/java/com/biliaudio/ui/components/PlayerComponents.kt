package com.biliaudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.biliaudio.data.model.Track
import com.biliaudio.player.RepeatMode
import java.util.concurrent.TimeUnit

// ====== 设计色板（参考 B站/网易云薄荷绿播放器） ======
private val MintBackground = Color(0xFFE3F5EE)   // 整页薄荷绿底
private val MintAccent = Color(0xFF66E0C2)       // 封面下方高亮条
private val PlayerBlue = Color(0xFF1E88E5)        // 主控制/进度条 已播放部分
private val PlayerTextPrimary = Color(0xFF1F1F1F)
private val PlayerTextSecondary = Color(0xFF6F7A82)
private val PlayerTrackBg = Color(0xFFE6E9EC)     // 未播放灰条

@Composable
fun MiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track?.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onExpand() }
            ) {
                Text(
                    text = track?.title ?: "未播放",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = track?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { onPlayPause() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    track: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: RepeatMode,
    isShuffle: Boolean,
    isLoading: Boolean,
    playbackError: String?,
    playlist: List<Track>,
    currentIndex: Int,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveFromPlaylist: (Int) -> Unit,
    onClearPlaylist: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPlaylistSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // ===== 顶栏：收起 + 标题 =====
        // 移除原「更多」按钮：其依赖的菜单项（定时器/倍速等）尚未实现，
        // 保留空点击会误导用户，待相关功能落地后再恢复入口。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onDismiss() }) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "收起",
                    tint = PlayerTextPrimary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.titleMedium,
                color = PlayerTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            // 占位，保持标题视觉居中
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 封面 + 底部薄荷绿高亮条 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    AsyncImage(
                        model = track?.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 加载态：懒解析音频地址 / 网络缓冲时显示。
                    // 半透明遮罩 + 进度指示，避免长时间无反馈。
                    if (isLoading && playbackError == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x66000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "正在加载...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    // 错误态：展示错误信息与重试按钮。
                    if (playbackError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x88000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = playbackError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.Button(
                                    onClick = onRetry,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = PlayerBlue
                                    )
                                ) {
                                    Text(text = "重试", color = Color.White)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MintAccent)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ===== 标题 =====
        // 移除原「收藏」按钮：收藏需调用B站收藏夹 deal 接口 + 收藏夹选择 UI，
        // 属未实现功能，保留空点击会误导用户，待收藏功能落地后再恢复入口。
        Text(
            text = track?.title ?: "未播放",
            style = MaterialTheme.typography.titleLarge,
            color = PlayerTextPrimary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        // ===== 副标题（UP主/作者） =====
        Text(
            text = track?.artist ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = PlayerTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // ===== 进度条 + 时间 =====
        LinearProgressBar(
            currentPosition = currentPosition,
            duration = duration,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = PlayerTextSecondary
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = PlayerTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ===== 主控制：上一首 / 播放暂停 / 下一首 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onPrevious() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    tint = PlayerTextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // 蓝色描边圆形 + 内嵌白色暂停 / 播放
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .clickable { onPlayPause() }
                    .pointerInput(Unit) {
                        // 仅用于触发点击，避免 IconButton 双层点击
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PlayerBlue)
                )
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = { onNext() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    tint = PlayerTextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 底部副控制：循环 / 随机 / 列表 =====
        // 移除原「评论」按钮：评论需调用B站评论接口，属未实现功能，
        // 保留空点击会误导用户，待评论功能落地后再恢复入口。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onToggleRepeat() }) {
                Icon(
                    imageVector = if (repeatMode == RepeatMode.ONE)
                        Icons.Default.RepeatOne
                    else
                        Icons.Default.Repeat,
                    contentDescription = "循环模式",
                    tint = if (repeatMode != RepeatMode.NONE) PlayerBlue else PlayerTextSecondary
                )
            }
            IconButton(onClick = { onToggleShuffle() }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "随机播放",
                    tint = if (isShuffle) PlayerBlue else PlayerTextSecondary
                )
            }
            IconButton(onClick = { showPlaylistSheet = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = "播放列表",
                    tint = PlayerTextSecondary
                )
            }
        }
    }

    // ===== 当前播放列表 BottomSheet =====
    if (showPlaylistSheet) {
        PlaylistBottomSheet(
            playlist = playlist,
            currentIndex = currentIndex,
            onPlayAt = { index ->
                onPlayAt(index)
            },
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onClearPlaylist = onClearPlaylist,
            onDismiss = { showPlaylistSheet = false }
        )
    }
}

/**
 * 播放页内嵌的播放列表底部弹层。
 * 支持点击播放、删除单项、清空全部，复用 [PlaylistScreen] 的列表项样式。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistBottomSheet(
    playlist: List<Track>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemoveFromPlaylist: (Int) -> Unit,
    onClearPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "播放列表",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${playlist.size} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (playlist.isNotEmpty()) {
                IconButton(onClick = {
                    onClearPlaylist()
                    onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空播放列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (playlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "播放列表为空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(playlist) { track ->
                    val index = playlist.indexOf(track)
                    PlaylistSheetItem(
                        track = track,
                        isPlaying = index == currentIndex,
                        onPlayClick = { onPlayAt(index) },
                        onRemoveClick = { onRemoveFromPlaylist(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSheetItem(
    track: Track,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlayClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemoveClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 普通线性进度条。
 * - 已播放部分：蓝色圆角线
 * - 未播放部分：浅灰圆角线
 * - 末端圆形滑块
 * - 支持点击 / 拖动跳转到指定位置
 */
@Composable
private fun LinearProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(0) }
    var draggingValue by remember { mutableStateOf<Float?>(null) }

    val safeDuration = duration.coerceAtLeast(1L)
    val progressRaw = if (draggingValue != null) {
        draggingValue!!
    } else {
        (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val progress = progressRaw.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .onSizeChanged { width = it.width }
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    if (width > 0) {
                        val newProgress = (offset.x / width).coerceIn(0f, 1f)
                        onSeek((newProgress * safeDuration).toLong())
                    }
                }
            }
            .pointerInput(safeDuration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (width > 0) {
                            draggingValue = (offset.x / width).coerceIn(0f, 1f)
                        }
                    },
                    onDrag = { change, _ ->
                        if (width > 0) {
                            change.consume()
                            draggingValue = (change.position.x / width).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        draggingValue?.let {
                            onSeek((it * safeDuration).toLong())
                        }
                        draggingValue = null
                    },
                    onDragCancel = { draggingValue = null }
                )
            }
    ) {
        val w = size.width
        val centerY = size.height / 2f
        val trackHeight = (size.height * 0.16f).coerceAtLeast(6f)
        val playedEnd = w * progress
        val thumbRadius = trackHeight * 1.4f

        // —— 未播放部分：浅灰圆角条 ——
        drawRoundRect(
            color = PlayerTrackBg,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size(w, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight)
        )

        // —— 已播放部分：蓝色圆角条 ——
        if (playedEnd > 0f) {
            drawRoundRect(
                color = PlayerBlue,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(playedEnd, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight)
            )
        }

        // —— 末端圆形滑块 ——
        drawCircle(
            color = PlayerBlue,
            radius = thumbRadius,
            center = Offset(playedEnd, centerY)
        )
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}

fun formatDurationMinSec(durationSec: Int): String {
    if (durationSec <= 0) return "0:00"
    val minutes = durationSec / 60
    val remainingSeconds = durationSec % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
