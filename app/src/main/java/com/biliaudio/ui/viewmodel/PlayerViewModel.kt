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
    val isLoading: StateFlow<Boolean> = playbackManager.isLoading
    val playbackError: StateFlow<String?> = playbackManager.playbackError

    private var progressJob: Job? = null

    fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                playbackManager.updateProgress()
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
    fun playAt(index: Int) = playbackManager.playAt(index)
    fun toggleRepeatMode() = playbackManager.toggleRepeatMode()
    fun retry() = playbackManager.retry()

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        playbackManager.releaseController()
    }
}
