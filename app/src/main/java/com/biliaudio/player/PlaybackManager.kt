package com.biliaudio.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.biliaudio.data.model.Track
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var mediaController: MediaController? = null

    // 睡眠定时器用的协程作用域，与 ViewModel 生命周期解耦，
    // 避免关闭播放页弹层后定时器被取消。
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sleepTimerJob: Job? = null

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

    /** 是否随机播放。ExoPlayer 原生支持 shuffleModeEnabled。 */
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle

    /** 当前播放倍速，1.0f 为正常速度。 */
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    /**
     * 睡眠定时器剩余分钟数，0 表示未启用。
     * UI 据此显示倒计时入口/剩余时间。到点自动暂停播放。
     */
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes

    /** 是否正在缓冲/加载（含懒解析音频地址阶段）。 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** 最近一次播放错误，null 表示无错误。供 UI 展示重试入口。 */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // STATE_BUFFERING 时认为正在加载（含懒解析阶段）；
            // 进入 READY/ENDED 时清除加载态与错误态。
            _isLoading.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                _playbackError.value = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackError.value = friendlyErrorMessage(error)
            _isLoading.value = false
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            val index = mediaController?.currentMediaItemIndex ?: -1
            _currentIndex.value = index
            if (index >= 0 && index < _playlist.value.size) {
                _currentTrack.value = _playlist.value[index]
            }
            // 切换曲目时清除上一首的错误态
            _playbackError.value = null
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

    private fun friendlyErrorMessage(error: PlaybackException): String {
        // 懒解析失败（IOException）映射为更友好的提示
        val cause = error.cause
        return when {
            error.message?.contains("解析音频地址") == true -> "音频地址解析失败，请重试"
            cause is java.io.IOException -> "网络加载失败，请检查网络后重试"
            else -> "播放失败，请重试"
        }
    }

    fun connectController(sessionToken: SessionToken) {
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                mediaController = future.get()
                mediaController?.addListener(playerListener)
                // 连接后同步随机/循环模式到 Player，避免重建 controller 后状态丢失
                mediaController?.shuffleModeEnabled = _isShuffle.value
                mediaController?.playbackSpeed = _playbackSpeed.value
                mediaController?.repeatMode = when (_repeatMode.value) {
                    RepeatMode.NONE -> androidx.media3.common.Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
                }
            } catch (e: Exception) {
                // MediaController 连接失败不应导致应用崩溃
                e.printStackTrace()
            }
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

    /**
     * 设置播放列表。
     *
     * Track.audioUrl 既可以是懒解析占位 URI（biliaudio://resolve?...），
     * 也可以是真实地址——ExoPlayer 配合 PlaybackService 的 ResolvingDataSource
     * 会自动处理，此处无需区分。这使得「播放全部」长列表瞬时完成。
     */
    fun setPlaylist(tracks: List<Track>, startIndex: Int = 0) {
        _playlist.value = tracks
        _currentIndex.value = startIndex
        _currentTrack.value = tracks.getOrNull(startIndex)
        _playbackError.value = null

        val mediaItems = tracks.map { it.toMediaItem() }

        mediaController?.setMediaItems(mediaItems, startIndex, 0L)
        mediaController?.prepare()
        mediaController?.play()
    }

    fun addToPlaylist(track: Track) {
        val currentList = _playlist.value.toMutableList()
        currentList.add(track)
        _playlist.value = currentList

        mediaController?.addMediaItem(track.toMediaItem())
    }

    /**
     * 插入到当前播放曲目之后（「下一首播放」）。
     * 无当前曲目时退化为追加到末尾。
     */
    fun addNext(track: Track) {
        val currentList = _playlist.value.toMutableList()
        val insertPos = (_currentIndex.value + 1).coerceAtLeast(currentList.size)
        currentList.add(insertPos, track)
        _playlist.value = currentList
        mediaController?.addMediaItem(insertPos, track.toMediaItem())
    }

    fun removeFromPlaylist(index: Int) {
        val currentList = _playlist.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _playlist.value = currentList
            mediaController?.removeMediaItem(index)
        }
    }

    /**
     * 清空整个播放列表并停止播放。
     */
    fun clearPlaylist() {
        _playlist.value = emptyList()
        _currentTrack.value = null
        _currentIndex.value = -1
        _currentPosition.value = 0L
        _duration.value = 0L
        _playbackError.value = null
        _isPlaying.value = false
        _isLoading.value = false
        mediaController?.clearMediaItems()
    }

    fun playAt(index: Int) {
        if (index in _playlist.value.indices) {
            _playbackError.value = null
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

    /**
     * 切换随机播放。ExoPlayer 原生 shuffle 会打乱播放顺序，
     * 但不影响播放列表数据本身。
     */
    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        mediaController?.shuffleModeEnabled = _isShuffle.value
    }

    /**
     * 设置播放倍速。ExoPlayer 原生支持 playbackSpeed。
     * @param speed 倍速值，范围 0.25f ~ 3.0f
     */
    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = safeSpeed
        mediaController?.playbackSpeed = safeSpeed
    }

    // ============ 睡眠定时器 ============

    /**
     * 启动睡眠定时器，到点自动暂停播放。
     * @param minutes 倒计时分钟数，<=0 视为取消
     */
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        _sleepTimerMinutes.value = minutes
        sleepTimerJob = serviceScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L) // 每分钟递减一次，刷新 UI 倒计时
                remaining--
                _sleepTimerMinutes.value = remaining
            }
            // 到点：暂停播放，保留定时器状态为 0（已结束）
            mediaController?.pause()
        }
    }

    /** 取消睡眠定时器。 */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerMinutes.value = 0
    }

    fun updateProgress() {
        mediaController?.let {
            _currentPosition.value = it.currentPosition
            _duration.value = it.duration.coerceAtLeast(0L)
        }
    }

    /**
     * 重新准备当前曲目（错误后重试）。
     * ExoPlayer 重新解析占位 URI 并加载，触发懒解析流程。
     */
    fun retry() {
        _playbackError.value = null
        val controller = mediaController ?: return
        controller.prepare()
        controller.play()
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

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(audioUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(Uri.parse(coverUrl))
                    .build()
            )
            .build()
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
