package com.biliaudio.data.repository

import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.DashData
import com.biliaudio.data.model.Track
import com.biliaudio.data.model.VideoItem
import com.biliaudio.data.model.VideoStreamResponse
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.preferences.PreferencesManager
import com.biliaudio.data.resultOf
import com.biliaudio.data.toHttpsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val api: BiliApi,
    private val preferencesManager: PreferencesManager
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
     * 解析真实音频 URL，带 URL 级缓存。
     *
     * 参考 BBPlayer resolveAudioUrl 的音频流选择策略（优先级从高到低）：
     *  1. 杜比全景声（dash.dolby.audio）
     *  2. Hi-Res 无损（dash.flac.audio）
     *  3. 指定质量（默认 192K AAC = 30280）
     *  4. 清单中最高质量
     *  5. durl 兜底（不支持 DASH 的老视频）
     *
     * B站 playurl 接口要求 cid 参数，若 [cid] 为 0 则先通过
     * x/web-interface/view 接口获取。
     */
    suspend fun resolveAudioUrl(
        bvid: String,
        aid: Long,
        cid: Long
    ): Result<String> {
        val key = bvid.ifEmpty { aid.toString() }

        // 命中 URL 缓存
        cacheMutex.withLock {
            cache[key]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < BiliConstants.Cache.AUDIO_URL_CACHE_TTL_MS) {
                    return Result.Success(entry.url)
                }
            }
        }

        // playurl 接口的 cid 是必需参数，缺失时服务端返回 -400/-404。
        // 收藏夹/合集/历史接口返回的 VideoItem 不含 cid，需先获取。
        var resolvedCid = cid
        if (resolvedCid == 0L) {
            when (val infoResult = resultOf {
                api.getVideoInfo(bvid = bvid, aid = aid)
            }) {
                is Result.Success -> {
                    resolvedCid = infoResult.data.data?.cid ?: 0L
                }
                is Result.Error -> return infoResult
                Result.Loading -> return Result.Loading
            }
            if (resolvedCid == 0L) {
                return Result.Error(IllegalStateException("No cid"), "无法获取视频 cid")
            }
        }

        return when (val result = getVideoStream(bvid = bvid, aid = aid, cid = resolvedCid)) {
            is Result.Success -> {
                val url = pickBestAudioUrl(result.data)
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

    /**
     * 从 playurl 响应中按优先级选择最优音频流地址。
     */
    private suspend fun pickBestAudioUrl(response: BiliResponse<VideoStreamResponse>): String? {
        val data = response.data ?: return null
        val dash = data.dash

        // DASH 清单：按 Dolby > Hi-Res > 指定质量 > 最高质量 选取
        if (dash != null) {
            selectBestAudio(dash)?.let { return it.toHttpsUrl() }
        }

        // 兜底：不支持 DASH 的老视频，取 durl 首段
        data.durl?.firstOrNull()?.url?.let { return it.toHttpsUrl() }

        return null
    }

    /**
     * 参考 BBPlayer 的音频流优先级选择。
     * 指定质量取用户偏好（设置页可配），0/无效时退回默认 192K AAC。
     */
    private suspend fun selectBestAudio(dash: DashData): String? {
        // 1. 杜比全景声（最高优先级）
        dash.dolby?.audio?.firstOrNull()?.url?.takeIf { it.isNotEmpty() }?.let { return it }

        // 2. Hi-Res 无损
        dash.flac?.audio?.url?.takeIf { it.isNotEmpty() }?.let { return it }

        // 3. 用户偏好的指定质量，0/无效时退回默认 192K AAC
        val preferred = preferencesManager.audioQuality.first()
        val targetQuality = if (preferred > 0) preferred else BiliConstants.AudioQuality.AAC_192K
        dash.audio.find { it.id == targetQuality }
            ?.url?.takeIf { it.isNotEmpty() }?.let { return it }

        // 4. 清单中最高质量（B站返回的 audio 列表已按质量从高到低排序）
        return dash.audio.firstOrNull()?.url?.takeIf { it.isNotEmpty() }
    }

    /**
     * 获取视频的音频流地址，带缓存。
     * 保留以兼容旧调用方，内部委托给 [resolveAudioUrl]。
     */
    suspend fun getAudioUrl(video: VideoItem): Result<String> =
        resolveAudioUrl(bvid = video.bvid, aid = video.aid, cid = video.cid)

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

    /**
     * 并发将多个视频转为 Track（预解析真实 URL）。
     *
     * 优化点：将原串行 for 循环改为并发请求，配合有界并发控制，
     * 避免一次性发起过多 playurl 请求被限流。长播放列表加载速度显著提升。
     *
     * 注意：当前主播放路径已切换为 [videosToLazyTracks]（懒解析，瞬时完成），
     * 本方法保留供需要预解析的场景使用。
     */
    suspend fun videosToTracks(videos: List<VideoItem>): Result<List<Track>> = coroutineScope {
        val concurrency = 5
        val tracks = mutableListOf<Track>()
        // 分批并发，每批 [concurrency] 个，避免瞬时大量请求触发风控
        videos.chunked(concurrency).forEach { batch ->
            val deferreds = batch.map { video ->
                async(Dispatchers.IO) { videoToTrack(video) }
            }
            deferreds.awaitAll().forEach { r ->
                if (r is Result.Success) tracks.add(r.data)
            }
        }
        Result.Success(tracks)
    }

    // ============ 懒解析（参考 BBPlayer 占位 URI 方案） ============

    /**
     * 创建懒解析 Track：audioUrl 填入占位 URI，不发起任何网络请求。
     *
     * 播放列表创建瞬时完成，真实音频 URL 在 ExoPlayer 加载该曲目时
     * 由 PlaybackService 中的 ResolvingDataSource 解析。
     * 占位 URI 编码了 bvid/aid/cid，解析时据此调用 playurl 接口。
     */
    fun videoToLazyTrack(video: VideoItem): Track {
        return Track(
            id = video.bvid.ifEmpty { video.aid.toString() },
            title = video.title,
            artist = video.upper?.name ?: "Unknown",
            coverUrl = video.cover.toHttpsUrl(),
            audioUrl = buildLazyUri(video.bvid, video.aid, video.cid),
            duration = video.duration.toLong() * 1000,
            bvid = video.bvid,
            aid = video.aid,
            cid = video.cid
        )
    }

    /**
     * 批量创建懒解析 Track（瞬时，无网络请求）。
     * 这是「播放全部」长列表不再卡顿的关键。
     */
    fun videosToLazyTracks(videos: List<VideoItem>): List<Track> =
        videos.map { videoToLazyTrack(it) }

    /**
     * 构建懒解析占位 URI：biliaudio://resolve?bvid=xxx&aid=xxx&cid=xxx
     */
    private fun buildLazyUri(bvid: String, aid: Long, cid: Long): String {
        val sb = StringBuilder()
        sb.append(BiliConstants.LazyUri.SCHEME)
            .append("://")
            .append(BiliConstants.LazyUri.HOST)
            .append("?bvid=").append(encode(bvid))
            .append("&aid=").append(aid)
            .append("&cid=").append(cid)
        return sb.toString()
    }

    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8")

    fun clearCache() {
        cache.clear()
    }

    private data class CacheEntry(
        val url: String,
        val timestamp: Long
    )
}
