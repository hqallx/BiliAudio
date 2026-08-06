package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.Result
import com.biliaudio.data.model.ReplyItem
import com.biliaudio.data.model.VideoStat
import com.biliaudio.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InteractionViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _stat = MutableStateFlow<VideoStat?>(null)
    val stat: StateFlow<VideoStat?> = _stat

    private val _comments = MutableStateFlow<List<ReplyItem>>(emptyList())
    val comments: StateFlow<List<ReplyItem>> = _comments

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    private var currentAid: Long = 0
    private var currentBvid: String = ""

    fun loadVideoData(bvid: String, aid: Long) {
        currentBvid = bvid
        currentAid = aid
        viewModelScope.launch {
            // 1. 获取视频统计信息（点赞数、评论数等）
            val videoInfo = videoRepository.fetchVideoInfo(bvid)
            _stat.value = videoInfo?.stat

            // 2. 查询点赞状态：用独立的 has/like 接口（参考 BBPlayer），
            //    而非 view 的 req_user——后者在风控/未登录场景下可能缺失，
            //    导致点赞按钮始终灰色。
            val liked = videoRepository.checkLikeStatus(bvid)
            if (liked != null) {
                _isLiked.value = liked
            }

            // 3. 获取评论列表（mode=3 按热度，plat=1）
            when (val result = videoRepository.fetchComments(aid)) {
                is Result.Success -> {
                    _comments.value = result.data.data?.replies ?: emptyList()
                }
                else -> {
                    _toast.value = "评论加载失败"
                }
            }
        }
    }

    fun toggleLike() {
        viewModelScope.launch {
            val likeAction = if (_isLiked.value) 2 else 1
            when (val result = videoRepository.likeVideo(currentBvid, likeAction)) {
                is Result.Success -> {
                    // 参考 BBPlayer：code=0 成功；65006=重复点赞，也视为成功
                    if (result.data.code == 0 || result.data.code == 65006) {
                        _isLiked.value = !_isLiked.value
                        _toast.value = if (_isLiked.value) "点赞成功" else "已取消点赞"
                    } else {
                        _toast.value = result.data.message.ifEmpty { "操作失败" }
                    }
                }
                is Result.Error -> {
                    _toast.value = result.message
                }
                else -> {}
            }
        }
    }

    fun sendComment(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            when (val result = videoRepository.sendComment(currentAid, message)) {
                is Result.Success -> {
                    if (result.data.code == 0) {
                        _toast.value = "评论发送成功"
                        loadComments(currentAid)
                    } else {
                        _toast.value = result.data.message.ifEmpty { "发送失败" }
                    }
                }
                is Result.Error -> {
                    _toast.value = result.message
                }
                else -> {}
            }
        }
    }

    private fun loadComments(aid: Long) {
        viewModelScope.launch {
            when (val result = videoRepository.fetchComments(aid)) {
                is Result.Success -> {
                    _comments.value = result.data.data?.replies ?: emptyList()
                }
                else -> {}
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
