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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun PlayerScreen(
    track: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .padding(horizontal = 24.dp),
    ) {
        // ===== 顶栏：收起 + 标题 + 更多 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
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
            IconButton(onClick = { /* TODO: 打开更多菜单 */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = PlayerTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 封面 + 底部薄荷绿高亮条 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = track?.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(28.dp))
                )
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

        // ===== 标题 + 收藏 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track?.title ?: "未播放",
                style = MaterialTheme.typography.titleLarge,
                color = PlayerTextPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = { /* TODO: 收藏切换 */ }) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = PlayerTextSecondary
                )
            }
        }

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
        WaveProgressBar(
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

        // ===== 底部副控制：顺序 / 随机 / 评论 / 列表 =====
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
            IconButton(onClick = { /* TODO: 切换随机播放 */ }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "随机播放",
                    tint = PlayerTextSecondary
                )
            }
            IconButton(onClick = { /* TODO: 打开评论 */ }) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "评论",
                    tint = PlayerTextSecondary
                )
            }
            IconButton(onClick = { /* TODO: 打开当前播放列表 */ }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = "播放列表",
                    tint = PlayerTextSecondary
                )
            }
        }
    }
}

/**
 * 波形进度条。
 * - 已播放部分：蓝色波形 + 当前播放竖线
 * - 未播放部分：浅灰条 + 末端圆点
 * - 支持点击 / 拖动跳转到指定位置
 */
@Composable
private fun WaveProgressBar(
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
        val h = size.height
        val centerY = h / 2f

        val playedEnd = w * progress
        val dotRadius = h * 0.18f

        // —— 未播放部分：浅灰条 ——
        drawLine(
            color = PlayerTrackBg,
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = h * 0.18f,
            cap = StrokeCap.Round
        )

        // —— 未播放末端圆点 ——
        drawCircle(
            color = PlayerTextSecondary,
            radius = dotRadius,
            center = Offset(w, centerY)
        )

        // —— 已播放部分：蓝色波形（正弦波） ——
        if (playedEnd > 0f) {
            val amplitude = h * 0.32f
            val wavelength = 22f
            val path = Path().apply {
                var x = 0f
                moveTo(0f, centerY)
                var phase = 0f
                while (x <= playedEnd) {
                    val y = centerY + kotlin.math.sin(phase) * amplitude
                    lineTo(x, y)
                    // 与步长对应推进相位（2π per wavelength）
                    val step = 1.5f
                    x += step
                    phase += (2f * Math.PI.toFloat()) * (step / wavelength)
                }
            }
            drawPath(
                path = path,
                color = PlayerBlue,
                style = Stroke(width = h * 0.16f, cap = StrokeCap.Round)
            )

            // —— 当前播放位置竖线 ——
            drawLine(
                color = PlayerBlue,
                start = Offset(playedEnd, centerY - h * 0.4f),
                end = Offset(playedEnd, centerY + h * 0.4f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun getRepeatIcon(repeatMode: RepeatMode): androidx.compose.ui.graphics.vector.ImageVector {
    return when (repeatMode) {
        RepeatMode.NONE -> Icons.Default.Repeat
        RepeatMode.ALL -> Icons.Default.Repeat
        RepeatMode.ONE -> Icons.Default.RepeatOne
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
