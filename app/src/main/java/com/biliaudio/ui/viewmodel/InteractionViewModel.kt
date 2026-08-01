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
            // 1. 获取统计信息
            val statData = videoRepository.fetchVideoStat(bvid)
            _stat.value = statData

            // 2. 获取评论列表
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
            when (val result = videoRepository.likeVideo(currentAid, likeAction)) {
                is Result.Success -> {
                    if (result.data.code == 0) {
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
