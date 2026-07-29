package com.biliaudio.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.biliaudio.MainActivity
import com.biliaudio.data.BiliConstants

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // B站音频流 CDN（*.bilivideo.com）要求请求带 Referer 与 UA，
        // 否则返回 403，表现为「无法正常播放视频」。
        // 通过 DefaultHttpDataSource.Factory 为所有 ExoPlayer 发起的 HTTP
        // 请求统一注入这两个头，DataSource 层兜底，无需逐个 MediaItem 设置。
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BiliConstants.USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to (BiliConstants.WEB_BASE_URL + "/"),
                    "Origin" to BiliConstants.WEB_BASE_URL
                )
            )
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
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

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
