package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.Result
import com.biliaudio.data.model.SearchVideoItem
import com.biliaudio.data.model.SearchUserItem
import com.biliaudio.data.model.HotSearchItem
import com.biliaudio.data.network.BiliApi
import com.biliaudio.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: BiliApi
) : ViewModel() {

    private val _videos = MutableStateFlow<List<SearchVideoItem>>(emptyList())
    val videos: StateFlow<List<SearchVideoItem>> = _videos

    private val _users = MutableStateFlow<List<SearchUserItem>>(emptyList())
    val users: StateFlow<List<SearchUserItem>> = _users

    private val _hotSearches = MutableStateFlow<List<HotSearchItem>>(emptyList())
    val hotSearches: StateFlow<List<HotSearchItem>> = _hotSearches

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun searchVideos(keyword: String, page: Int = 1) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = api.searchVideos(keyword = keyword, page = page)
                _videos.value = resp.data?.result ?: emptyList()
            } catch (e: Exception) {
                DebugLogger.e("SearchVM", "search error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchUsers(keyword: String) {
        viewModelScope.launch {
            try {
                val resp = api.searchUsers(keyword = keyword)
                _users.value = resp.data?.result ?: emptyList()
            } catch (e: Exception) {
                DebugLogger.e("SearchVM", "user search error", e)
            }
        }
    }

    fun loadHotSearches() {
        viewModelScope.launch {
            try {
                val resp = api.getHotSearches()
                _hotSearches.value = resp.data?.trending?.list ?: emptyList()
            } catch (e: Exception) {
                DebugLogger.e("SearchVM", "hot search error", e)
            }
        }
    }
}
