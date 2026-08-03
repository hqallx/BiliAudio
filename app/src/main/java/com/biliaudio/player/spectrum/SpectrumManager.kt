package com.biliaudio.player.spectrum

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.hypot

/**
 * 频谱可视化管理器。
 * 参考 BBPlayer SpectrumManager：封装 Android Visualizer API，
 * 提供 FFT 频谱数据供 UI 层绘制。
 */
class SpectrumManager {
    private var visualizer: Visualizer? = null
    private var isEnabled = false
    private val fftSize = Visualizer.getCaptureSizeRange().let {
        if (it.isNotEmpty()) it[1] else 1024
    }
    private var fftBytes = ByteArray(fftSize)

    fun start(audioSessionId: Int) {
        if (visualizer != null) stop()
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = fftSize
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) { /* Not used */ }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) { /* Polling mode, listener required but not used */ }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            isEnabled = true
        } catch (e: Exception) {
            Log.e("SpectrumManager", "Failed to init Visualizer: ${e.message}")
            isEnabled = false
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            visualizer = null
            isEnabled = false
        }
    }

    /**
     * 填充归一化频谱数据 (0.0 - 1.0)。
     * 数组大小建议为 fftSize / 2。
     */
    fun getSpectrumData(destination: FloatArray) {
        if (!isEnabled || visualizer == null) {
            destination.fill(0f)
            return
        }
        try {
            visualizer?.getFft(fftBytes)
            val n = fftBytes.size
            val outputSize = minOf(destination.size, n / 2)
            for (i in 0 until outputSize) {
                if (i == 0) {
                    val real = fftBytes[0].toFloat()
                    val imag = fftBytes[1].toFloat()
                    destination[0] = hypot(real, imag) / 128.0f
                } else {
                    val k = i * 2
                    if (k + 1 < n) {
                        val real = fftBytes[k].toFloat()
                        val imag = fftBytes[k + 1].toFloat()
                        destination[i] = hypot(real, imag) / 128.0f
                    }
                }
            }
        } catch (e: Exception) {
            destination.fill(0f)
        }
    }

    fun isEnabled(): Boolean = isEnabled
}
