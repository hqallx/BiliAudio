package com.biliaudio.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局调试日志器。
 *
 * - 当 [enabled] 为 true 时，所有日志同时输出到 logcat（tag=BiliAudio）和内存环形缓冲区。
 * - UI 可订阅 [logs] 在设置页查看实时日志，用于排查「划掉后台登录失效」等问题。
 * - 默认关闭，仅当用户在设置页打开 Debug 开关时启用。
 */
object DebugLogger {

    private const val TAG = "BiliAudio"
    private const val MAX_LOGS = 500

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (value) {
            d("DebugLogger", "调试日志已启用")
        }
    }

    fun d(tag: String, msg: String) {
        if (!_enabled.value) return
        val line = "${timeFmt.format(Date())} D/$tag: $msg"
        Log.d(TAG, "[$tag] $msg")
        append(line)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (!_enabled.value) return
        val line = "${timeFmt.format(Date())} W/$tag: $msg"
        if (t != null) Log.w(TAG, "[$tag] $msg", t) else Log.w(TAG, "[$tag] $msg")
        append(line + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (!_enabled.value) return
        val line = "${timeFmt.format(Date())} E/$tag: $msg"
        if (t != null) Log.e(TAG, "[$tag] $msg", t) else Log.e(TAG, "[$tag] $msg")
        append(line + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    private fun append(line: String) {
        val current = _logs.value.toMutableList()
        current.add(line)
        while (current.size > MAX_LOGS) current.removeAt(0)
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
