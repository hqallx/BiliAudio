package com.biliaudio.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局调试日志器。
 *
 * - 当 [enabled] 为 true 时，所有日志同时输出到 logcat（tag=BiliAudio）和内存环形缓冲区。
 * - UI 可订阅 [logs] 在设置页查看实时日志，用于排查「划掉后台登录失效」等问题。
 * - 默认关闭，仅当用户在设置页打开 Debug 开关时启用。
 * - 日志同时持久化到文件（[init] 时传入 context），应用被杀/划掉后台后日志不会丢失，
 *   下次启动自动恢复。
 */
object DebugLogger {

    private const val TAG = "BiliAudio"
    private const val MAX_LOGS = 500
    private const val LOG_FILE = "debug_logs.txt"
    private const val PREFS_NAME = "debug_logger_prefs"
    private const val KEY_ENABLED = "enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private var logFile: File? = null
    private var appContext: Context? = null

    /** 持久化写文件用的后台作用域，避免阻塞主线程做磁盘 I/O。 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 初始化日志持久化文件并恢复历史日志 + enabled 状态。
     * 应在 Application.onCreate 中调用，确保进程重启后日志与开关都不丢失。
     *
     * enabled 状态从单独的 SharedPreferences 同步读取（不走 DataStore，避免协程延迟），
     * 保证冷启动期的 Cookie 恢复、网络初始化等日志能被捕获——
     * 否则用户进设置页（SettingsViewModel 才创建）前日志全丢。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        logFile = File(context.filesDir, LOG_FILE)
        try {
            val file = logFile ?: return
            if (file.exists()) {
                val lines = file.readLines().filter { it.isNotEmpty() }
                val trimmed = if (lines.size > MAX_LOGS) lines.takeLast(MAX_LOGS) else lines
                _logs.value = trimmed
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复持久化日志失败", e)
        }
        // 同步恢复 enabled 状态，确保启动期日志立即生效
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        } catch (e: Exception) {
            Log.e(TAG, "恢复 enabled 状态失败", e)
        }
    }

    /**
     * 切换调试开关。
     * 持久化到 SharedPreferences（apply 异步），保证进程重启后 init 能恢复。
     */
    fun setEnabled(value: Boolean) {
        _enabled.value = value
        try {
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "持久化 enabled 失败", e)
        }
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
        var trimmed = false
        while (current.size > MAX_LOGS) {
            current.removeAt(0)
            trimmed = true
        }
        _logs.value = current
        persist(line, trimmed, current)
    }

    /**
     * 持久化到文件（在 IO 线程执行，避免阻塞主线程）。
     * - 正常追加：用 append 模式写一行，O(1)。
     * - 触发上限裁剪：重写整个文件，保证文件内容与内存一致。
     */
    private fun persist(line: String, trimmed: Boolean, all: List<String>) {
        val file = logFile ?: return
        ioScope.launch {
            try {
                if (trimmed) {
                    file.writeText(all.joinToString("\n") + "\n")
                } else {
                    FileOutputStream(file, true).bufferedWriter().use { it.appendLine(line) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "写日志文件失败", e)
            }
        }
    }

    fun clear() {
        _logs.value = emptyList()
        val file = logFile ?: return
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除日志文件失败", e)
        }
    }
}
