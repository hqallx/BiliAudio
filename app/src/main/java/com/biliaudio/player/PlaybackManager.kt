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
import com.biliaudio.data.preferences.PreferencesManager
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    private var mediaController: MediaController? = null

    /**
     * 在 MediaController 未连接期间用户触发的播放操作（setPlaylist / playOrAdd 等）。
     * controller 连接成功后会优先应用 pending 列表并播放，避免被 restorePlaybackState 覆盖，
     * 也避免「点击播放全部后列表已填充却不出声」的静默失败。
     */
    private data class PendingPlayback(val tracks: List<Track>, val startIndex: Int)
    private var pendingPlayback: PendingPlayback? = null

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
        // 懒解析失败的消息在 cause.message 里（被 ExoPlayer 包装成 Source error）
        val cause = error.cause
        val causeMsg = cause?.message ?: ""
        return when {
            error.message?.contains("解析音频地址") == true ||
                causeMsg.contains("解析音频地址") -> "音频地址解析失败，请重试"
            causeMsg.contains("超时") -> "解析超时，请检查网络后重试"
            cause is java.io.IOException -> "网络加载失败，请检查网络后重试"
            else -> "播放失败，请重试"
        }
    }

    fun connectController(sessionToken: SessionToken) {
        // 幂等防御：配置变更（旋转等）重建时会再次调用 connectController，
        // 已存在 controller 则直接复用，避免泄漏旧 MediaController 实例。
        if (mediaController != null) return
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                mediaController = future.get()
                mediaController?.addListener(playerListener)
                // 连接后同步随机/循环模式到 Player，避免重建 controller 后状态丢失
                mediaController?.shuffleModeEnabled = _isShuffle.value
                mediaController?.setPlaybackSpeed(_playbackSpeed.value)
                mediaController?.repeatMode = when (_repeatMode.value) {
                    RepeatMode.NONE -> androidx.media3.common.Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
                }
                // 连接成功后优先应用 pending 播放操作（用户在 controller 未就绪期间触发的播放）；
                // 无 pending 时才恢复上次播放状态（列表+位置，不自动播放）。
                // 用 serviceScope 避免阻塞 directExecutor 线程。
                serviceScope.launch {
                    val pending = pendingPlayback
                    if (pending != null) {
                        applyPendingPlayback(pending)
                    } else {
                        restorePlaybackState()
                    }
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
        // 防御：startIndex 越界会导致 add(index, e) 抛 IndexOutOfBoundsException
        val safeIndex = startIndex.coerceIn(0, tracks.size)
        _playlist.value = tracks
        _currentIndex.value = safeIndex
        _currentTrack.value = tracks.getOrNull(safeIndex)
        _playbackError.value = null

        val controller = mediaController
        if (controller == null) {
            // controller 未就绪：暂存待连接后应用，避免静默失败。
            // 状态流已更新，UI 会显示列表；连接成功后 applyPendingPlayback 会真正开始播放。
            pendingPlayback = PendingPlayback(tracks, safeIndex)
            return
        }

        val mediaItems = tracks.map { it.toMediaItem() }
        controller.setMediaItems(mediaItems, safeIndex, 0L)
        controller.prepare()
        controller.play()
        savePlaybackState()
    }

    fun addToPlaylist(track: Track) {
        val currentList = _playlist.value.toMutableList()
        currentList.add(track)
        _playlist.value = currentList

        mediaController?.addMediaItem(track.toMediaItem())
        savePlaybackState()
    }

    /**
     * 插入到当前播放曲目之后（「下一首播放」）。
     * 无当前曲目时退化为追加到末尾。
     */
    fun addNext(track: Track) {
        val currentList = _playlist.value.toMutableList()
        // coerceIn 防御：_currentIndex 可能因上游越界而 > size-1
        val insertPos = (_currentIndex.value + 1).coerceIn(0, currentList.size)
        currentList.add(insertPos, track)
        _playlist.value = currentList
        mediaController?.addMediaItem(insertPos, track.toMediaItem())
        savePlaybackState()
    }

    fun removeFromPlaylist(index: Int) {
        val currentList = _playlist.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _playlist.value = currentList
            mediaController?.removeMediaItem(index)
            savePlaybackState()
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
        // 清空列表后同步清除持久化，避免重启后恢复一个已被用户清空的列表
        serviceScope.launch { preferencesManager.clearPlaybackState() }
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
        mediaController?.setPlaybackSpeed(safeSpeed)
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
     * 持久化当前播放列表、曲目索引与播放位置到 DataStore。
     * 在 setPlaylist/addToPlaylist/addNext/removeFromPlaylist 及进度更新时调用，
     * 保证进程被杀/划掉后台后可恢复。
     * 仅在 playlist 非空时保存，避免空列表覆盖已有状态（清空走 [clearPlaylist]）。
     */
    fun savePlaybackState() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return
        val snapshot = PlaybackSnapshot(
            playlist = tracks,
            currentIndex = _currentIndex.value,
            // 优先用 controller 的实时位置，更精确
            position = mediaController?.currentPosition ?: _currentPosition.value
        )
        serviceScope.launch {
            try {
                preferencesManager.savePlaybackState(
                    playlist = snapshot.playlist,
                    index = snapshot.currentIndex,
                    position = snapshot.position
                )
            } catch (e: Exception) {
                // 持久化失败不应影响播放
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    /**
     * 应用 controller 连接前暂存的播放操作。
     * 复用 [setPlaylist] 的主体逻辑（此时 mediaController 已就绪），并清空 pending。
     */
    private fun applyPendingPlayback(pending: PendingPlayback) {
        pendingPlayback = null
        val controller = mediaController ?: return
        _playlist.value = pending.tracks
        _currentIndex.value = pending.startIndex
        _currentTrack.value = pending.tracks.getOrNull(pending.startIndex)
        _playbackError.value = null
        val mediaItems = pending.tracks.map { it.toMediaItem() }
        controller.setMediaItems(mediaItems, pending.startIndex, 0L)
        controller.prepare()
        controller.play()
        savePlaybackState()
    }

    /**
     * 从 DataStore 恢复上次播放状态。
     * 应在 MediaController 连接成功后调用（见 [connectController] 回调），
     * 恢复后不自动播放（避免重启后突然出声），仅恢复列表与位置，由用户点播放。
     */
    suspend fun restorePlaybackState() {
        try {
            val tracks = preferencesManager.savedPlaylist.first()
            val index = preferencesManager.savedIndex.first()
            val position = preferencesManager.savedPosition.first()
            if (tracks.isEmpty()) return
            val safeIndex = index.coerceIn(0, tracks.lastIndex)
            _playlist.value = tracks
            _currentIndex.value = safeIndex
            _currentTrack.value = tracks.getOrNull(safeIndex)
            _currentPosition.value = position
            val mediaItems = tracks.map { it.toMediaItem() }
            // 恢复时不 play，仅 prepare 到指定位置，等待用户主动播放
            mediaController?.setMediaItems(mediaItems, safeIndex, position)
            mediaController?.prepare()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
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
