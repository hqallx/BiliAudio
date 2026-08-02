package com.biliaudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.biliaudio.player.spectrum.SpectrumManager
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 频谱可视化组件。
 * 参考 BBPlayer SpectrumVisualizer：圆形频谱条围绕封面显示。
 */
@Composable
fun SpectrumVisualizer(
    isPlaying: Boolean,
    audioSessionId: Int,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    barCount: Int = 60,
    maxBarHeightDp: Float = 36f,
    sizeDp: Float = 200f
) {
    val spectrumManager = remember { SpectrumManager() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val maxBarHeight = maxBarHeightDp * density
    val size = sizeDp * density
    val gap = 4f * density

    var frequencyData by remember { mutableStateOf(FloatArray(barCount) { 0f }) }
    var hasSignal by remember { mutableStateOf(false) }

    DisposableEffect(audioSessionId) {
        spectrumManager.start(audioSessionId)
        onDispose { spectrumManager.stop() }
    }

    LaunchedEffect(isPlaying) {
        val smoothFactor = 0.7f
        val decayFactor = 0.92f
        val prevData = FloatArray(barCount)

        while (true) {
            val rawData = FloatArray(512)
            spectrumManager.getSpectrumData(rawData)
            val newData = FloatArray(barCount)

            if (isPlaying) {
                val halfCount = barCount / 2
                for (i in 0 until halfCount) {
                    val t = i.toFloat() / (halfCount - 1)
                    val startBin = (t * t * (rawData.size - 1)).toInt()
                    val tNext = (i + 1).toFloat() / (halfCount - 1)
                    val endBin = (tNext * tNext * (rawData.size - 1)).toInt()
                    val actualEnd = maxOf(endBin, startBin + 1)

                    var sum = 0f
                    var count = 0
                    var j = startBin
                    while (j < actualEnd && j < rawData.size) {
                        sum += rawData[j]
                        count++
                        j++
                    }
                    var v = 0f
                    if (count > 0) {
                        val magnitude = sum / count
                        val db = 20 * kotlin.math.log10(magnitude + 0.0001)
                        v = ((db + 60) / 60).coerceIn(0f, 1f)
                    }
                    val mirrorIdx = barCount - 1 - i
                    val smoothL = prevData[i] * smoothFactor + v * (1 - smoothFactor)
                    prevData[i] = smoothL
                    newData[i] = smoothL
                    val smoothR = prevData[mirrorIdx] * smoothFactor + v * (1 - smoothFactor)
                    prevData[mirrorIdx] = smoothR
                    newData[mirrorIdx] = smoothR
                }
                hasSignal = newData.any { it > 0.001f }
            } else {
                for (i in 0 until barCount) {
                    val decayed = prevData[i] * decayFactor
                    if (decayed > 0.001f) {
                        prevData[i] = decayed
                        newData[i] = decayed
                        hasSignal = true
                    } else {
                        prevData[i] = 0f
                        newData[i] = 0f
                    }
                }
            }

            frequencyData = newData
            if (!isPlaying && !hasSignal) break
            kotlinx.coroutines.delay(33L) // ~30fps
        }
    }

    val containerSize = size + maxBarHeight * 2

    Canvas(modifier = modifier) {
        val center = size / 2 + maxBarHeight
        val radius = size / 2 + gap

        for (i in 0 until barCount) {
            val angle = (i.toFloat() / barCount) * 2 * Math.PI - Math.PI / 2
            val px = (center + radius * cos(angle)).toFloat()
            val py = (center + radius * sin(angle)).toFloat()
            val nx = cos(angle).toFloat()
            val ny = sin(angle).toFloat()
            val v = frequencyData.getOrElse(i) { 0f }
            val barHeight = min(v * maxBarHeight, maxBarHeight).coerceAtLeast(4f)

            drawLine(
                color = color,
                start = Offset(px, py),
                end = Offset(px + nx * barHeight, py + ny * barHeight),
                strokeWidth = 3f
            )
        }
    }
}
