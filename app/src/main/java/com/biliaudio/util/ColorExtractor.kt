package com.biliaudio.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * 封面主题色提取。
 * 参考 BBPlayer 的 ImageThemeColors：从封面图提取主色调，
 * 用于播放器背景渐变。
 */
object ColorExtractor {

    /**
     * 从 Bitmap 提取主色调。
     * 算法：缩取样→量化→频次统计→取最高频色→近似为主色调。
     */
    fun extractDominantColor(bitmap: Bitmap): Int {
        val scaledWidth = 20
        val scaledHeight = (bitmap.height.toFloat() / bitmap.width * scaledWidth).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val colorCounts = HashMap<Int, Int>()
        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                val pixel = scaled.getPixel(x, y)
                // 量化：右移4位降低精度，合并相近颜色
                val r = Color.red(pixel) shr 4
                val g = Color.green(pixel) shr 4
                val b = Color.blue(pixel) shr 4
                val quantized = Color.rgb(r, g, b)
                colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
            }
        }

        if (colorCounts.isEmpty()) return Color.rgb(40, 40, 40)

        // 过滤过暗/过亮的颜色
        val filtered = colorCounts.filter { (color, _) ->
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val brightness = (r + g + b) / 3
            brightness in 30..220
        }

        val dominant = (if (filtered.isNotEmpty()) filtered else colorCounts)
            .maxByOrNull { it.value }?.key ?: Color.rgb(40, 40, 40)

        return dominant
    }

    /**
     * 判断颜色是否偏暗。
     */
    fun isDarkColor(color: Int): Boolean {
        val brightness = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
        return brightness < 128
    }

    /**
     * 将颜色变暗，用于渐变背景顶部色。
     */
    fun darkenColor(color: Int, factor: Float = 0.6f): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
