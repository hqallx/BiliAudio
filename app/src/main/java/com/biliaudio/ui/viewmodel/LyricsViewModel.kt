package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.model.SubtitleLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val api: BiliApi
) : ViewModel() {

    private val _lyrics = MutableStateFlow<List<SubtitleLine>>(emptyList())
    val lyrics: StateFlow<List<SubtitleLine>> = _lyrics

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val json = Json { ignoreUnknownKeys = true }

    fun loadLyrics(bvid: String, cid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val playerInfo = api.getWebPlayerInfo(bvid = bvid, cid = cid)
                val subtitleUrl = playerInfo.data?.subtitle?.subtitles?.firstOrNull()?.subtitleUrl
                if (!subtitleUrl.isNullOrEmpty()) {
                    val fullUrl = if (subtitleUrl.startsWith("http")) subtitleUrl
                    else "https:$subtitleUrl"
                    val jsonElement = api.getSubtitleJson(fullUrl)
                    val body = json.decodeFromString(
                        com.biliaudio.data.model.SubtitleBody.serializer(),
                        jsonElement.toString()
                    )
                    _lyrics.value = body.body
                } else {
                    _lyrics.value = emptyList()
                }
            } catch (e: Exception) {
                _lyrics.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearLyrics() {
        _lyrics.value = emptyList()
    }
}
