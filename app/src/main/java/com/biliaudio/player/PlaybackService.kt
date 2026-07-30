package com.biliaudio.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.biliaudio.MainActivity
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.repository.VideoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * 后台音频播放服务。
 *
 * 参考 BBPlayer (OrpheusMusicService) 的播放优化方案：
 *  - **懒解析**：Track.audioUrl 使用 `biliaudio://resolve?...` 占位 URI，
 *    ExoPlayer 加载时由 [BiliAudioResolver] 拦截并解析为真实音频地址，
 *    使播放列表创建瞬时完成，无需预请求所有曲目的 playurl。
 *  - **字节级 LRU 缓存**：已播放/预加载的音频字节缓存本地（256MB），
 *    跨曲目复用、再次播放秒开；缓存出错时回退到网络（FLAG_IGNORE_CACHE_ON_ERROR）。
 *  - **统一请求头**：B站 CDN 要求 Referer/UA，在 DataSource 层统一注入。
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var videoRepository: VideoRepository

    private var mediaSession: MediaSession? = null
    private var mediaCache: SimpleCache? = null

    override fun onCreate() {
        super.onCreate()
        // super.onCreate() 后 Hilt 已完成字段注入，videoRepository 可用。

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BiliConstants.USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to (BiliConstants.WEB_BASE_URL + "/"),
                    "Origin" to BiliConstants.WEB_BASE_URL
                )
            )

        // 懒解析层：拦截占位 URI，解析为真实音频 URL。
        // resolveDataSpec 运行在 ExoPlayer 的加载线程（后台），可阻塞。
        val resolvingFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory,
            BiliAudioResolver(videoRepository)
        )

        // 字节级 LRU 缓存层：跨曲目复用音频字节，提升长列表/重复播放体验。
        val cache = SimpleCache(
            File(cacheDir, BiliConstants.Cache.MEDIA_CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(BiliConstants.Cache.MEDIA_CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(this)
        )
        mediaCache = cache

        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = DefaultDataSource.Factory(this, cacheFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val sessionIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 退到后台且未在播放时停止服务，避免常驻后台耗电。
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        try {
            mediaCache?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaCache = null
        super.onDestroy()
    }
}

/**
 * 懒解析器：将 `biliaudio://resolve?bvid=...&aid=...&cid=...` 占位 URI
 * 解析为真实的 B站音频流地址。
 *
 * 参考 BBPlayer 的 BilibiliResolver：
 *  - 占位 URI 解析出 bvid/aid/cid
 *  - 调用 [VideoRepository.resolveAudioUrl] 获取真实 URL（带 URL 级缓存）
 *  - 用占位 URI 字符串作为缓存 key，保证同一曲目跨播放命中字节缓存
 *
 * 注意：[resolveDataSpec] 在 ExoPlayer 加载线程同步调用，使用 [runBlocking]
 * 等待协程结果（与 BBPlayer 的阻塞 .execute() 等价）。
 */
@OptIn(UnstableApi::class)
private class BiliAudioResolver(
    private val videoRepository: VideoRepository
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        // 仅拦截本应用的占位 URI，其余（已是真实地址）原样放行
        if (uri.scheme != BiliConstants.LazyUri.SCHEME ||
            uri.host != BiliConstants.LazyUri.HOST
        ) {
            return dataSpec
        }

        val bvid = uri.getQueryParameter("bvid").orEmpty()
        val aid = uri.getQueryParameter("aid")?.toLongOrNull() ?: 0L
        val cid = uri.getQueryParameter("cid")?.toLongOrNull() ?: 0L

        val realUrl = try {
            runBlocking {
                when (val r = videoRepository.resolveAudioUrl(bvid, aid, cid)) {
                    is Result.Success -> r.data
                    is Result.Error -> throw IOException("解析音频地址失败: ${r.message}", r.exception)
                    Result.Loading -> throw IOException("解析音频地址状态异常")
                }
            }
        } catch (e: Exception) {
            throw IOException("解析音频地址失败: ${e.message}", e)
        }

        // 用占位 URI 字符串作为缓存 key：同一曲目无论真实 URL 是否过期，
        // 字节缓存始终命中，实现跨播放的秒开体验。
        return dataSpec.buildUpon()
            .setUri(Uri.parse(realUrl))
            .setKey(uri.toString())
            .build()
    }
}
