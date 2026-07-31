package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.model.Track
import com.biliaudio.data.repository.VideoRepository
import com.biliaudio.player.PlaybackManager
import com.biliaudio.player.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    val playbackManager: PlaybackManager
) : ViewModel() {

    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentTrack: StateFlow<Track?> = playbackManager.currentTrack
    val currentPosition: StateFlow<Long> = playbackManager.currentPosition
    val duration: StateFlow<Long> = playbackManager.duration
    val playlist: StateFlow<List<Track>> = playbackManager.playlist
    val currentIndex: StateFlow<Int> = playbackManager.currentIndex
    val repeatMode: StateFlow<RepeatMode> = playbackManager.repeatMode
    val isShuffle: StateFlow<Boolean> = playbackManager.isShuffle
    val playbackSpeed: StateFlow<Float> = playbackManager.playbackSpeed
    val sleepTimerMinutes: StateFlow<Int> = playbackManager.sleepTimerMinutes
    val isLoading: StateFlow<Boolean> = playbackManager.isLoading
    val playbackError: StateFlow<String?> = playbackManager.playbackError

    private var progressJob: Job? = null

    fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            var saveCounter = 0
            while (true) {
                playbackManager.updateProgress()
                // 每 ~5 秒持久化一次播放位置（PROGRESS_UPDATE_INTERVAL_MS=500ms × 10）
                // 避免每 500ms 写盘，同时保证进程被杀时最多丢失 5 秒进度
                if (++saveCounter >= 10) {
                    saveCounter = 0
                    playbackManager.savePlaybackState()
                }
                delay(BiliConstants.PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    fun play() = playbackManager.play()
    fun pause() = playbackManager.pause()
    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun seekTo(position: Long) = playbackManager.seekTo(position)
    fun next() = playbackManager.next()
    fun previous() = playbackManager.previous()
    fun setPlaylist(tracks: List<Track>, startIndex: Int = 0) =
        playbackManager.setPlaylist(tracks, startIndex)
    fun addToPlaylist(track: Track) = playbackManager.addToPlaylist(track)
    fun addNext(track: Track) = playbackManager.addNext(track)
    fun removeFromPlaylist(index: Int) = playbackManager.removeFromPlaylist(index)
    fun clearPlaylist() = playbackManager.clearPlaylist()
    fun playAt(index: Int) = playbackManager.playAt(index)

    /**
     * 播放指定曲目：若已在播放列表中则直接定位播放，否则追加到末尾并播放。
     * 统一各列表（收藏夹/合集/历史/搜索结果）的点击行为，避免重复条目。
     */
    fun playOrAdd(track: Track) {
        val currentPlaylist = playlist.value
        val index = currentPlaylist.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            playAt(index)
        } else {
            addToPlaylist(track)
            playAt(playlist.value.size - 1)
        }
    }
    fun toggleRepeatMode() = playbackManager.toggleRepeatMode()
    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun setPlaybackSpeed(speed: Float) = playbackManager.setPlaybackSpeed(speed)
    fun startSleepTimer(minutes: Int) = playbackManager.startSleepTimer(minutes)
    fun cancelSleepTimer() = playbackManager.cancelSleepTimer()
    fun retry() = playbackManager.retry()

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        playbackManager.releaseController()
    }
}
