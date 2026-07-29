package com.biliaudio.data.repository

import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.Track
import com.biliaudio.data.model.VideoItem
import com.biliaudio.data.model.VideoStreamResponse
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.resultOf
import com.biliaudio.data.toHttpsUrl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val api: BiliApi
) {

    private val cache = LinkedHashMap<String, CacheEntry>()
    private val cacheMutex = Mutex()

    suspend fun getVideoStream(
        bvid: String = "",
        aid: Long = 0,
        cid: Long = 0
    ): Result<BiliResponse<VideoStreamResponse>> = resultOf {
        api.getVideoStream(bvid = bvid, aid = aid, cid = cid)
    }

    /**
     * 获取视频的音频流地址，带缓存。
     * B站 playurl 接口要求 cid 参数，若 [video] 中无 cid 则先通过
     * x/web-interface/view 接口获取。
     */
    suspend fun getAudioUrl(video: VideoItem): Result<String> {
        val key = video.bvid.ifEmpty { video.aid.toString() }

        // 命中缓存
        cacheMutex.withLock {
            cache[key]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < BiliConstants.Cache.AUDIO_URL_CACHE_TTL_MS) {
                    return Result.Success(entry.url)
                }
            }
        }

        // playurl 接口的 cid 是必需参数，缺失时服务端返回 -400/-404。
        // 收藏夹/合集/历史接口返回的 VideoItem 不含 cid，需先获取。
        var cid = video.cid
        if (cid == 0L) {
            when (val infoResult = resultOf {
                api.getVideoInfo(bvid = video.bvid, aid = video.aid)
            }) {
                is Result.Success -> {
                    cid = infoResult.data.data?.cid ?: 0L
                }
                is Result.Error -> return infoResult
                Result.Loading -> return Result.Loading
            }
            if (cid == 0L) {
                return Result.Error(IllegalStateException("No cid"), "无法获取视频 cid")
            }
        }

        return when (val result = getVideoStream(bvid = video.bvid, aid = video.aid, cid = cid)) {
            is Result.Success -> {
                val audioItem = result.data.data?.dash?.audio?.firstOrNull()
                val url = audioItem?.url?.toHttpsUrl()
                if (url.isNullOrEmpty()) {
                    Result.Error(IllegalStateException("No audio stream"), "未获取到音频流")
                } else {
                    cacheMutex.withLock {
                        cache[key] = CacheEntry(url, System.currentTimeMillis())
                        if (cache.size > BiliConstants.Cache.AUDIO_URL_CACHE_SIZE) {
                            val oldest = cache.keys.first()
                            cache.remove(oldest)
                        }
                    }
                    Result.Success(url)
                }
            }
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    suspend fun videoToTrack(video: VideoItem): Result<Track> {
        return when (val urlResult = getAudioUrl(video)) {
            is Result.Success -> Result.Success(
                Track(
                    id = video.bvid.ifEmpty { video.aid.toString() },
                    title = video.title,
                    artist = video.upper?.name ?: "Unknown",
                    coverUrl = video.cover.toHttpsUrl(),
                    audioUrl = urlResult.data,
                    duration = video.duration.toLong() * 1000,
                    bvid = video.bvid,
                    aid = video.aid,
                    cid = video.cid
                )
            )
            is Result.Error -> urlResult
            Result.Loading -> Result.Loading
        }
    }

    suspend fun videosToTracks(videos: List<VideoItem>): Result<List<Track>> {
        val tracks = mutableListOf<Track>()
        for (video in videos) {
            when (val r = videoToTrack(video)) {
                is Result.Success -> tracks.add(r.data)
                is Result.Error -> { /* 跳过失败项 */ }
                Result.Loading -> {}
            }
        }
        return Result.Success(tracks)
    }

    fun clearCache() {
        cache.clear()
    }

    private data class CacheEntry(
        val url: String,
        val timestamp: Long
    )
}
