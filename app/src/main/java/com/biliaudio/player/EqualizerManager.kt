package com.biliaudio.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer

/**
 * 均衡器管理器。
 * 参考 bilimusic EqualizerModels。
 */
class EqualizerManager(context: Context, audioSessionId: Int) {
    private val equalizer: Equalizer = Equalizer(0, audioSessionId)
    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)

    data class Band(val index: Short, val frequency: Int, val levelRange: IntRange)

    val bands: List<Band>
        get() = (0 until equalizer.numberOfBands).map { i ->
            val s = i.toShort()
            Band(
                index = s,
                frequency = equalizer.getCenterFreq(s) / 1000,
                levelRange = equalizer.getBandLevelRange()?.let { it[0]..it[1] } ?: -1500..1500
            )
        }

    fun getBandLevel(band: Short): Int = equalizer.getBandLevel(band)

    fun setBandLevel(band: Short, level: Int) {
        equalizer.setBandLevel(band, level.toShort())
        saveToPrefs(band, level)
    }

    fun enable() { equalizer.enabled = true }
    fun disable() { equalizer.enabled = false }

    private fun saveToPrefs(band: Short, level: Int) {
        prefs.edit().putInt("band_$band", level).apply()
    }

    fun loadPreset() {
        bands.forEach { b ->
            val level = prefs.getInt("band_${b.index}", 0)
            equalizer.setBandLevel(b.index, level.toShort())
        }
    }

    fun release() { equalizer.release() }
}
