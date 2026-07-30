package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.preferences.PreferencesManager
import com.biliaudio.data.repository.VideoRepository
import com.biliaudio.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel。
 * 承载音质偏好持久化、调试开关与缓存清理逻辑。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val videoRepository: VideoRepository
) : ViewModel() {

    /** 当前音质偏好 id，0 表示默认（192K AAC）。 */
    val audioQuality: StateFlow<Int> = preferencesManager.audioQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 调试日志开关，持久化到 DataStore。 */
    val debugEnabled: StateFlow<Boolean> = preferencesManager.debugEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    /** 可选音质列表（id -> 展示名）。 */
    val audioQualityOptions: List<Pair<Int, String>> = listOf(
        BiliConstants.AudioQuality.AAC_192K to "192K（推荐）",
        BiliConstants.AudioQuality.AAC_132K to "132K（省流）",
        BiliConstants.AudioQuality.AAC_64K to "64K（最低）"
    )

    init {
        // 启动时根据持久化偏好同步 DebugLogger 开关状态，
        // 保证冷启动后调试日志能立即生效（用于排查登录失效等问题）。
        viewModelScope.launch {
            preferencesManager.debugEnabled.collect { enabled ->
                DebugLogger.setEnabled(enabled)
            }
        }
    }

    /** 保存音质偏好。 */
    fun setAudioQuality(quality: Int) {
        viewModelScope.launch {
            preferencesManager.saveAudioQuality(quality)
            videoRepository.clearCache() // 切换音质后清 URL 缓存，避免命中旧地址
            _toast.value = "音质已更新"
        }
    }

    /** 切换调试日志开关，并立即应用到 DebugLogger。 */
    fun setDebugEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveDebugEnabled(enabled)
            DebugLogger.setEnabled(enabled)
            _toast.value = if (enabled) "调试日志已启用" else "调试日志已关闭"
        }
    }

    /** 清理音频地址缓存。 */
    fun clearCache() {
        viewModelScope.launch {
            videoRepository.clearCache()
            _toast.value = "缓存已清理"
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
