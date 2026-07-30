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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.draw.blur
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

// ====== 深色播放器色板（参考网易云/B站暗色播放器） ======
private val DarkBg = Color(0xFF2C2C2C)
private val DarkSurface = Color(0xFF3D3D3D)
private val PlayerTextPrimary = Color(0xFFFFFFFF)
private val PlayerTextSecondary = Color(0xFFAAAAAA)
private val PlayerTextMuted = Color(0xFF888888)
private val PlayerAccent = Color(0xFFFA7299)      // B站粉（点赞高亮）
private val PlayerTrackBg = Color(0xFF555555)     // 未播放灰条
private val PlayerTrackPlayed = Color(0xFFFFFFFF) // 已播放白条

/** 格式化大数字：>1万显示 x.x万 */
private fun formatCount(count: Long): String = when {
    count >= 100_000 -> String.format("%.0f万", count / 10_000.0)
    count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
    count > 0 -> count.toString()
    else -> ""
}

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
    playbackSpeed: Float,
    sleepTimerMinutes: Int,
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
    onSetPlaybackSpeed: (Float) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveFromPlaylist: (Int) -> Unit,
    onClearPlaylist: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // 背景：视频封面模糊填充（铺满整屏）
        if (!track?.coverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = track?.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // 半透明黑色遮罩：让前景文字清晰可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // ===== 顶栏：收起 + 标题 =====
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
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 封面大图 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = track?.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                if (isLoading && playbackError == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在加载...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
                if (playbackError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x88000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                    containerColor = PlayerAccent
                                )
                            ) {
                                Text(text = "重试", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ===== 标题行 + 点赞/评论/更多 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "未播放",
                    style = MaterialTheme.typography.titleLarge,
                    color = PlayerTextPrimary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlayerTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 点赞
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "点赞",
                    tint = PlayerTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = formatCount(track?.likeCount ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerTextMuted
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 评论
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ModeComment,
                    contentDescription = "评论",
                    tint = PlayerTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = formatCount(track?.commentCount ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerTextMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 更多
            IconButton(onClick = { /* 更多菜单待实现 */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = PlayerTextMuted
                )
            }
        }

        // ===== 倍速 / 睡眠定时器 快捷入口 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { showSpeedSheet = true },
                label = { Text("${playbackSpeed}x", color = PlayerTextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PlayerTextSecondary
                    )
                },
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = DarkSurface
                )
            )
            AssistChip(
                onClick = { showSleepSheet = true },
                label = {
                    Text(
                        if (sleepTimerMinutes > 0) "定时 ${sleepTimerMinutes}min"
                        else "定时关闭",
                        color = PlayerTextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PlayerTextSecondary
                    )
                },
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = DarkSurface
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // ===== 进度条 + 时间 + 音质 =====
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
                color = PlayerTextMuted
            )
            Text(
                text = "极高音质",
                style = MaterialTheme.typography.bodySmall,
                color = PlayerTextMuted
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = PlayerTextMuted
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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

            // 大圆形播放/暂停按钮
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PlayerTextPrimary)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = DarkBg,
                    modifier = Modifier.size(36.dp)
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
                    tint = if (repeatMode != RepeatMode.NONE) PlayerAccent else PlayerTextMuted
                )
            }
            IconButton(onClick = { onToggleShuffle() }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "随机播放",
                    tint = if (isShuffle) PlayerAccent else PlayerTextMuted
                )
            }
            IconButton(onClick = { showPlaylistSheet = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = "播放列表",
                    tint = PlayerTextMuted
                )
            }
        }
    }
    } // Box end

    // ===== 播放列表 BottomSheet =====
    if (showPlaylistSheet) {
        PlaylistBottomSheet(
            playlist = playlist,
            currentIndex = currentIndex,
            onPlayAt = { index -> onPlayAt(index) },
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onClearPlaylist = onClearPlaylist,
            onDismiss = { showPlaylistSheet = false }
        )
    }

    // ===== 倍速选择 BottomSheet =====
    if (showSpeedSheet) {
        SpeedPickerSheet(
            currentSpeed = playbackSpeed,
            onPick = { speed ->
                onSetPlaybackSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }

    // ===== 睡眠定时器 BottomSheet =====
    if (showSleepSheet) {
        SleepTimerSheet(
            currentMinutes = sleepTimerMinutes,
            onPick = { minutes ->
                if (minutes <= 0) onCancelSleepTimer() else onStartSleepTimer(minutes)
                showSleepSheet = false
            },
            onDismiss = { showSleepSheet = false }
        )
    }
}

/**
 * 播放页内嵌的播放列表底部弹层。
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
 * - 已播放部分：白色圆角线
 * - 未播放部分：深灰圆角线
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

        drawRoundRect(
            color = PlayerTrackBg,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size(w, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight)
        )
        if (playedEnd > 0f) {
            drawRoundRect(
                color = PlayerTrackPlayed,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(playedEnd, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight)
            )
        }
        drawCircle(
            color = PlayerTrackPlayed,
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

/**
 * 倍速选择底部弹层。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SpeedPickerSheet(
    currentSpeed: Float,
    onPick: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = "播放倍速",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speeds.forEach { speed ->
                val selected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                androidx.compose.material3.FilterChip(
                    selected = selected,
                    onClick = { onPick(speed) },
                    label = { Text("${speed}x") },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PlayerAccent,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

/**
 * 睡眠定时器底部弹层。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    currentMinutes: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0, 15, 30, 45, 60, 90)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = "睡眠定时",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (currentMinutes > 0) {
            Text(
                text = "当前剩余约 ${currentMinutes} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { minutes ->
                val selected = currentMinutes == minutes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) PlayerAccent.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onPick(minutes) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (minutes == 0) "关闭定时" else "${minutes} 分钟后停止",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) PlayerAccent
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = PlayerAccent
                        )
                    }
                }
            }
        }
    }
}
