package com.biliaudio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliaudio.data.model.SubtitleLine

/**
 * 歌词显示组件。
 * 参考 BBPlayer PlayerLyrics：居中大字体滚动歌词。
 */
@Composable
fun LyricsView(
    lyrics: List<SubtitleLine>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val currentIndex = lyrics.indexOfLast { it.from * 1000 <= currentPositionMs }

    Box(modifier = modifier) {
        if (lyrics.isEmpty()) {
            Text(
                text = "暂无歌词",
                color = Color(0xFFAAAAAA),
                modifier = Modifier.fillMaxSize().let {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    )
                },
                textAlign = TextAlign.Center
            )
            return
        }

        // 双语歌词：上面日文，下面中文（如果有多行）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // 当前行上方留白
            Spacer(modifier = Modifier.weight(0.35f))

            // 当前歌词
            if (currentIndex >= 0 && currentIndex < lyrics.size) {
                val current = lyrics[currentIndex]
                val currentColor by animateColorAsState(
                    targetValue = Color.White,
                    label = "lyricColor"
                )
                // 主歌词行
                Text(
                    text = current.content,
                    color = currentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 下一句预览
            if (currentIndex + 1 < lyrics.size) {
                val next = lyrics[currentIndex + 1]
                Text(
                    text = next.content,
                    color = Color(0xFF666666),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
