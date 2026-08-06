package com.biliaudio.data.repository

import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.BiliResponse
import com.biliaudio.data.model.DashData
import com.biliaudio.data.model.Track
import com.biliaudio.data.model.ApiActionResponse
import com.biliaudio.data.model.ReplyListResponse
import com.biliaudio.data.model.VideoItem
import com.biliaudio.data.model.VideoStat
import com.biliaudio.data.model.VideoStreamResponse
import com.biliaudio.data.model.VideoInfoResponse
import com.biliaudio.data.network.BiliApi
import com.biliaudio.data.network.BiliCookieJar
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
    private val preferencesManager: PreferencesManager,
    private val cookieJar: BiliCookieJar
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
        // B站收藏夹接口的 medias 仅返回 id（即 avid），不返回独立 aid 字段，
        // 反序列化后 VideoItem.aid 为 0。此处用 id 兜底，确保 aid 可用于点赞/评论接口。
        val effectiveAid = video.aid.takeIf { it > 0 } ?: video.id
        return when (val urlResult = getAudioUrl(video)) {
            is Result.Success -> Result.Success(
                Track(
                    id = video.bvid.ifEmpty { effectiveAid.toString() },
                    title = video.title,
                    artist = video.upper?.name ?: "Unknown",
                    coverUrl = video.cover.toHttpsUrl(),
                    audioUrl = urlResult.data,
                    duration = video.duration.toLong() * 1000,
                    bvid = video.bvid,
                    aid = effectiveAid,
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
        // B站收藏夹接口的 medias 仅返回 id（即 avid），不返回独立 aid 字段，
        // 反序列化后 VideoItem.aid 为 0。此处用 id 兜底，确保 aid 可用于：
        // 1. 点赞/评论接口（需 aid 作为 oid）
        // 2. playurl 解析（ResolvingDataSource 从占位 URI 读取 aid）
        val effectiveAid = video.aid.takeIf { it > 0 } ?: video.id
        return Track(
            id = video.bvid.ifEmpty { effectiveAid.toString() },
            title = video.title,
            artist = video.upper?.name ?: "Unknown",
            coverUrl = video.cover.toHttpsUrl(),
            audioUrl = buildLazyUri(video.bvid, effectiveAid, video.cid),
            duration = video.duration.toLong() * 1000,
            bvid = video.bvid,
            aid = effectiveAid,
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

    /**
     * 清空音频地址缓存。
     * 必须获取 [cacheMutex]：cache 是非线程安全的 LinkedHashMap，
     * resolveAudioUrl 在 ExoPlayer 加载线程读写，本方法在 Main 线程调用，
     * 不加锁会触发 ConcurrentModificationException。
     */
    suspend fun clearCache() {
        cacheMutex.withLock { cache.clear() }
    }

    // ============ 互动：点赞 & 评论 ============

    /**
     * 获取视频统计信息（点赞数、评论数等）。
     * 通过 view 接口拿到 stat 字段，比单独调接口更节省请求。
     */
    suspend fun fetchVideoStat(bvid: String): VideoStat? {
        val result = resultOf { api.getVideoInfo(bvid = bvid) }
        if (result is Result.Success) {
            return result.data.data?.stat
        }
        return null
    }

    /**
     * 获取视频信息（含 stat 与 req_user）。
     * req_user 含当前用户是否已点赞，供播放器初始化点赞按钮状态。
     */
    suspend fun fetchVideoInfo(bvid: String): VideoInfoResponse? {
        val result = resultOf { api.getVideoInfo(bvid = bvid) }
        if (result is Result.Success) {
            return result.data.data
        }
        return null
    }

    /**
     * 获取评论列表。
     *
     * 参考 BBPlayer getComments：使用 /x/v2/reply/main，参数 mode=3（按热度）、
     * plat=1、next=0（首页）。缺少 mode/plat 时该接口可能返回空 replies，
     * 是评论加载失败的常见根因。
     *
     * @param aid 视频 avid（oid 参数）
     */
    suspend fun fetchComments(aid: Long): Result<ReplyListResponse> {
        return try {
            Result.Success(api.getComments(oid = aid, type = 1, mode = 3, next = 0, plat = 1))
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "未知错误")
        }
    }

    /**
     * 查询当前视频是否已被点赞。
     * 参考 BBPlayer checkVideoIsThumbUp：用独立接口 x/web-interface/archive/has/like，
     * 而非 view 接口的 req_user（后者在风控/未登录场景下可能缺失）。
     * @return true=已点赞，false=未点赞，null=查询失败
     */
    suspend fun checkLikeStatus(bvid: String): Boolean? {
        return try {
            val resp = api.checkLikeStatus(bvid)
            if (resp.code == 0) {
                // data 为 JsonPrimitive，值为 0(未赞) 或 1(已赞)
                (resp.data as? kotlinx.serialization.json.JsonPrimitive)
                    ?.intOrNull == 1
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 点赞/取消点赞视频。
     * 参考 BBPlayer：使用 bvid 而非 aid，csrf 从 cookieJar 直接获取。
     * @param bvid 视频 BV 号
     * @param like 1=点赞，2=取消点赞
     */
    suspend fun likeVideo(bvid: String, like: Int): Result<ApiActionResponse> {
        val csrf = cookieJar.store.getCsrfToken() ?: ""
        if (csrf.isEmpty()) return Result.Error(Exception("未登录"), "请先登录")
        return try {
            Result.Success(api.likeVideo(bvid, like, csrf))
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "未知错误")
        }
    }

    /**
     * 发送视频评论。
     * csrf 从 cookieJar 直接获取（bili_jct cookie）。
     */
    suspend fun sendComment(aid: Long, message: String): Result<ApiActionResponse> {
        val csrf = cookieJar.store.getCsrfToken() ?: ""
        if (csrf.isEmpty()) return Result.Error(Exception("未登录"), "请先登录")
        return try {
            Result.Success(api.addComment(oid = aid, type = 1, message = message, csrf = csrf))
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "未知错误")
        }
    }
    private data class CacheEntry(
        val url: String,
        val timestamp: Long
    )
}
