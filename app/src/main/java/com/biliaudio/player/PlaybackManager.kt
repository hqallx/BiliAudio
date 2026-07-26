package com.biliaudio.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.biliaudio.data.model.Track
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaybackManager(private val context: Context) {

    private var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist: StateFlow<List<Track>> = _playlist

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            val index = mediaController?.currentMediaItemIndex ?: -1
            _currentIndex.value = index
            if (index >= 0 && index < _playlist.value.size) {
                _currentTrack.value = _playlist.value[index]
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                _currentPosition.value = player.currentPosition
                _duration.value = player.duration.coerceAtLeast(0L)
            }
        }
    }

    fun connectController(sessionToken: SessionToken) {
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            mediaController = future.get()
            mediaController?.addListener(playerListener)
        }, MoreExecutors.directExecutor())
    }

    fun releaseController() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        if (mediaController?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _currentPosition.value = position
    }

    fun next() {
        mediaController?.seekToNextMediaItem()
    }

    fun previous() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun setPlaylist(tracks: List<Track>, startIndex: Int = 0) {
        _playlist.value = tracks
        _currentIndex.value = startIndex
        _currentTrack.value = tracks.getOrNull(startIndex)

        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setUri(Uri.parse(track.audioUrl))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(Uri.parse(track.coverUrl))
                        .build()
                )
                .build()
        }

        mediaController?.setMediaItems(mediaItems, startIndex, 0L)
        mediaController?.prepare()
        mediaController?.play()
    }

    fun addToPlaylist(track: Track) {
        val currentList = _playlist.value.toMutableList()
        currentList.add(track)
        _playlist.value = currentList

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(track.audioUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(Uri.parse(track.coverUrl))
                    .build()
            )
            .build()

        mediaController?.addMediaItem(mediaItem)
    }

    fun removeFromPlaylist(index: Int) {
        val currentList = _playlist.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _playlist.value = currentList
            mediaController?.removeMediaItem(index)
        }
    }

    fun playAt(index: Int) {
        if (index in _playlist.value.indices) {
            mediaController?.seekToDefaultPosition(index)
            mediaController?.play()
        }
    }

    fun toggleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        _repeatMode.value = nextMode
        mediaController?.repeatMode = when (nextMode) {
            RepeatMode.NONE -> androidx.media3.common.Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
        }
    }

    fun updateProgress() {
        mediaController?.let {
            _currentPosition.value = it.currentPosition
            _duration.value = it.duration.coerceAtLeast(0L)
        }
    }

    /**
     * 返回当前播放状态快照，用于持久化。
     */
    fun snapshotPlaybackState(): PlaybackSnapshot {
        return PlaybackSnapshot(
            playlist = _playlist.value,
            currentIndex = _currentIndex.value,
            position = _currentPosition.value
        )
    }
}

data class PlaybackSnapshot(
    val playlist: List<Track>,
    val currentIndex: Int,
    val position: Long
)

enum class RepeatMode {
    NONE,
    ALL,
    ONE
}
